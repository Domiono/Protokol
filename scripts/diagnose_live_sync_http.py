#!/usr/bin/env python3
"""Unauthenticated API endpoint diagnosis. No device cookies or customer data are used."""
import json
import urllib.error
import urllib.request

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None

opener=urllib.request.build_opener(NoRedirect)
url="https://rentmor.pl/protocol_sync.php"
for method in ("GET","POST"):
    payload=b'{"operations":[]}' if method=="POST" else None
    req=urllib.request.Request(
        url,
        data=payload,
        method=method,
        headers={
            "Accept":"application/json",
            "User-Agent":"RentMor-Android-API-Check/1.0",
            "Content-Type":"application/json",
            "X-RentMor-Sync":"1"
        },
    )
    try:
        try: response=opener.open(req,timeout=15)
        except urllib.error.HTTPError as exc: response=exc
        with response:
            status=response.status
            media=response.headers.get("Content-Type","")
            head=response.read(4096).decode("utf-8","replace").lstrip()
            mode="JSON" if head.startswith("{") else "HTML" if head.startswith("<") else "UNKNOWN"
            print(f"{method}: HTTP {status}; content-type={media}; response={mode}")
            if mode=="JSON":
                try:
                    data=json.loads(head)
                    print("  JSON fields: "+",".join(sorted(data.keys())[:8]))
                except ValueError:print("  Partial JSON, not reporting contents.")
            if 300<=status<400: print("  Server redirected this API request.")
            if mode!="JSON":print("  WARNING: Android JSON parser cannot read this response.")
    except Exception as exc:
        print(f"{method}: public endpoint unavailable: {type(exc).__name__}")
