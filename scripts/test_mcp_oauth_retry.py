#!/usr/bin/env python3
"""Integration-test live OAuth source OFFLINE with a synthetic owner and isolated state."""
from __future__ import annotations
import base64
import hashlib
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlencode, urlsplit
from urllib.request import build_opener, HTTPRedirectHandler, Request

class Hidden(HTMLParser):
    def __init__(self):
        super().__init__()
        self.values = {}
    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if tag == "input" and values.get("type") == "hidden":
            self.values[values["name"]] = values["value"]

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

opener = build_opener(NoRedirect)

def request(url, data=None, headers=None):
    if isinstance(data, dict):
        data = urlencode(data).encode("utf-8")
    obj = Request(url, data=data, headers=headers or {}, method="POST" if data is not None else "GET")
    try:
        response = opener.open(obj, timeout=8)
    except HTTPError as exc:
        response = exc
    return response.code, dict(response.headers), response.read().decode("utf-8")

def main(oauth, core):
    password = "SyntheticPasswordForIsolatedOAuthTests123!"
    with tempfile.TemporaryDirectory(prefix="rm-mcp-oauth-") as d:
        root = Path(d)
        public = root / "public_html" / "rentmor-control-mcp"
        private = root / "rentmor-control-private"
        public.mkdir(parents=True)
        private.mkdir()
        shutil.copyfile(oauth, public / "oauth.php")
        shutil.copyfile(core, public / "oauth_core.php")
        hashed = subprocess.check_output(["php", "-r", "echo password_hash($argv[1], PASSWORD_DEFAULT);", password], text=True)
        (private / "owner.json").write_text(json.dumps({"password_hash": hashed}))
        state = private / "state.json"
        state.write_text(json.dumps({"pair_until": int(time.time()) + 300}))
        with socket.socket() as s:
            s.bind(("127.0.0.1", 0))
            port = s.getsockname()[1]
        base = f"http://127.0.0.1:{port}/rentmor-control-mcp"
        env = os.environ | {"RM_MCP_TEST_BASE_URL": base}
        proc = subprocess.Popen(["php", "-S", f"127.0.0.1:{port}", "-t", str(root / "public_html")],
                                env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            for _ in range(70):
                try:
                    status, _, _ = request(base + "/oauth.php?op=metadata")
                    if status == 200:
                        break
                except URLError:
                    pass
                time.sleep(.1)
            else:
                raise AssertionError("Local PHP OAuth service failed")
            status, _, body = request(base + "/oauth.php?op=register",
                json.dumps({"redirect_uris": ["https://chatgpt.com/connector_platform_oauth_redirect"],
                            "token_endpoint_auth_method": "none"}).encode(),
                {"Content-Type": "application/json"})
            assert status == 201, ("register", status)
            client = json.loads(body)["client_id"]
            verifier = "A" * 43
            challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
            params = {"response_type": "code", "client_id": client,
                      "redirect_uri": "https://chatgpt.com/connector_platform_oauth_redirect",
                      "code_challenge": challenge, "code_challenge_method": "S256",
                      "scope": "source:read", "state": "SYNTHETIC_TEST_OAUTH_STATE",
                      "resource": base + "/index.php"}
            def form():
                status, _, body = request(base + "/oauth.php?op=authorize&" + urlencode(params))
                assert status == 200, ("form", status)
                parser = Hidden()
                parser.feed(body)
                assert "nonce" in parser.values and "ticket" in parser.values, "missing signed form"
                return parser.values
            first = form()
            payload = {"nonce": first["nonce"], "ticket": first["ticket"], "password": password}
            status, headers, _ = request(base + "/oauth.php?op=authorize", payload)
            assert status == 302, ("authorize", status)
            code = parse_qs(urlsplit(headers["Location"]).query)["code"][0]
            stored = json.loads(state.read_text())
            assert hashlib.sha256(code.encode()).hexdigest() in stored["codes"], "missing auth code"
            assert stored["login"][hashlib.sha256(first["nonce"].encode()).hexdigest()]["used"] is True
            # Network/browser retry may submit an identical form a second time.
            status, repeat_headers, body = request(base + "/oauth.php?op=authorize", payload)
            assert status == 302, ("repeat authorize", status, body)
            repeated_code = parse_qs(urlsplit(repeat_headers["Location"]).query)["code"][0]
            assert repeated_code == code, "Retry must return original pending code"
            stored = json.loads(state.read_text())
            assert len(stored["codes"]) == 1, "Retry must not mint extra code"
            bad_password = dict(payload, password="INCORRECT-TEST-PASSWORD")
            status, _, body = request(base + "/oauth.php?op=authorize", bad_password)
            assert status == 403 and "D01" in body, "Retry must still require owner password"
            token_request = {"grant_type": "authorization_code", "code": code, "code_verifier": verifier,
                             "client_id": client, "redirect_uri": params["redirect_uri"], "resource": params["resource"]}
            status, _, body = request(base + "/oauth.php?op=token", token_request)
            assert status == 200 and "access_token" in json.loads(body), ("token", status)
            status, _, body = request(base + "/oauth.php?op=authorize", payload)
            assert status == 403 and "D06" in body, "Replay after token exchange must be blocked"
            status, _, body = request(base + "/oauth.php?op=token", token_request)
            assert status == 400 and "invalid_grant" in body, "Token code must remain single use"
            second = form()
            stored = json.loads(state.read_text())
            stored["login"].pop(hashlib.sha256(second["nonce"].encode()).hexdigest())
            state.write_text(json.dumps(stored))
            status, _, _ = request(base + "/oauth.php?op=authorize",
                {"nonce": second["nonce"], "ticket": second["ticket"], "password": password})
            assert status == 302, ("lost temporary login entry", status)
            print("PASS: fresh signed form -> atomic authorization -> redirect -> PKCE token")
            print("PASS: duplicate identical POST returns original pending code, no second code")
            print("PASS: incorrect password denied and exchanged code blocks all replays (D06)")
            print("PASS: valid signed form recovers when temporary login row disappears")
            print("PASS: synthetic password/state only; no live logins or private data")
        finally:
            proc.terminate()
            try:
                proc.communicate(timeout=3)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.communicate()

if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: test_mcp_oauth_retry.py oauth.php oauth_core.php")
    main(Path(sys.argv[1]), Path(sys.argv[2]))
