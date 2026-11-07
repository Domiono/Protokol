#!/usr/bin/env python3
"""Offline password-rotation integration regression using synthetic owner/state."""
from html.parser import HTMLParser
import json
import socket
import subprocess
import tempfile
import shutil
import time
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

ROOT=Path(__file__).resolve().parents[1]
class Hidden(HTMLParser):
    def __init__(self): super().__init__(); self.values={}
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if tag=="input" and a.get("type")=="hidden": self.values[a["name"]]=a["value"]

def request(url, data=None):
    obj=Request(url,data=urlencode(data).encode() if data is not None else None)
    try: r=urlopen(obj,timeout=6)
    except HTTPError as e:r=e
    return r.status,r.read().decode()

def main():
    old="OldSyntheticOwnerPassword_2026!"
    new="NewSyntheticOwnerPassword#2026!"
    with tempfile.TemporaryDirectory(prefix="rentmor-owner-password-") as folder:
        root=Path(folder); public=root/"public_html"/"rentmor-control-mcp"; private=root/"rentmor-control-private"
        public.mkdir(parents=True);private.mkdir()
        shutil.copyfile(ROOT/"scripts"/"mcp_owner_change_password.php",public/"change-password.php")
        # Test against the actual currently deployed core, fetched securely by workflow.
        shutil.copyfile(Path(__import__("sys").argv[1]),public/"oauth_core.php")
        owner_hash=subprocess.check_output(["php","-r","echo password_hash($argv[1], PASSWORD_DEFAULT);",old],text=True)
        (private/"owner.json").write_text(json.dumps({"password_hash":owner_hash,"created_at":"2026-11-01T01:01:01Z"}))
        live={"clients":{"CL_KEY":{"expires":time.time()+10000}},"codes":{"ABC":{"expires":time.time()+10000}},
              "access":{"TKN":{"expires":time.time()+10000}},"refresh":{"REF":{"expires":time.time()+10000}},
              "login":{"NON":{"expires":time.time()+10000}},"pair_until":int(time.time()+300)}
        (private/"state.json").write_text(json.dumps(live))
        with socket.socket() as sock:
            sock.bind(("127.0.0.1",0)); port=sock.getsockname()[1]
        process=subprocess.Popen(["php","-S",f"127.0.0.1:{port}","-t",str(root/"public_html")],
                                 stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        try:
            url=f"http://127.0.0.1:{port}/rentmor-control-mcp/change-password.php"
            for _ in range(40):
                try: status,body=request(url);break
                except URLError:time.sleep(.1)
            assert status==200 and "Obecne hasło" in body
            p=Hidden();p.feed(body)
            assert {"nonce","until","proof"}<=set(p.values)
            status,body=request(url,p.values|{"old":"incorrect","new":new,"confirm":new})
            assert status==403 and "Nieprawidłowe hasło" in body
            status,body=request(url,p.values|{"old":old,"new":"short","confirm":"short"})
            assert status==400 and "Nieprawidłowe nowe hasło" in body
            status,body=request(url,p.values|{"old":old,"new":new,"confirm":new})
            assert status==200 and "Hasło zmienione" in body
            changed=json.loads((private/"owner.json").read_text())
            assert changed["password_hash"]!=owner_hash
            assert changed["created_at"]=="2026-11-01T01:01:01Z"
            assert subprocess.run(["php","-r","exit(password_verify($argv[1],$argv[2]) ? 0 : 1);",
                                   new,changed["password_hash"]]).returncode==0
            assert subprocess.run(["php","-r","exit(password_verify($argv[1],$argv[2]) ? 0 : 1);",
                                   old,changed["password_hash"]]).returncode==1
            state=json.loads((private/"state.json").read_text())
            assert all(state[g] in ({},[]) for g in ["codes","access","refresh","login"])
            assert "pair_until" not in state
            assert "CL_KEY" in state["clients"]
            status,body=request(url,p.values|{"old":old,"new":new,"confirm":new})
            assert status==403 and "Formularz wygasł" in body
            print("PASS: GET form, valid CSRF token, password verification and validation")
            print("PASS: atomic owner password update; new password works, old one rejected")
            print("PASS: access/refresh/codes/login revoked; registered OAuth clients preserved")
            print("PASS: stale form rejected; no real customer data or password in tests")
        finally:
            process.terminate();process.wait(timeout=4)

if __name__=="__main__":main()
