#!/usr/bin/env python3
"""Patch only the verified RentMor Control OAuth version, without touching state or secrets."""
from __future__ import annotations
import argparse
import hashlib
from pathlib import Path

EXPECTED_SHA = "864e7827ad1c0d41e210b085131b79a9a5b3df4a1f66053d4303ecfef15a7ca7"
REPLACEMENTS = [
    (
        "        $result=rm_state(function (&$s)use($nonce,$verified,$signed,$ticketStatus){",
        "        $code=rm_random_token();\n"
        "        $result=rm_state(function (&$s)use($nonce,$verified,$signed,$ticketStatus,$code){",
    ),
    (
        "            $s['login'][$key]=['used'=>true,'expires'=>time()+RM_OAUTH_LOGIN_SECONDS];\n"
        "            return ['status'=>'ok','p'=>$signed];",
        "            // Consume login nonce and issue OAuth code in ONE atomic state update.\n"
        "            $s['login'][$key]=['used'=>true,'expires'=>time()+RM_OAUTH_LOGIN_SECONDS];\n"
        "            $s['codes'][hash('sha256',$code)]=['p'=>$signed,'expires'=>time()+RM_OAUTH_CODE_SECONDS];\n"
        "            return ['status'=>'ok','p'=>$signed];",
    ),
    (
        "        $p=$result['p'];\n"
        "        $code=rm_random_token();\n"
        "        rm_state(function (&$s)use($code,$p){$s['codes'][hash('sha256',$code)]=['p'=>$p,'expires'=>time()+RM_OAUTH_CODE_SECONDS];});",
        "        $p=$result['p'];",
    ),
]

def patch_source(original: bytes) -> bytes:
    if hashlib.sha256(original).hexdigest() != EXPECTED_SHA:
        raise ValueError("Unexpected live OAuth version: refusing changes")
    text = original.decode("utf-8")
    for old, new in REPLACEMENTS:
        if text.count(old) != 1:
            raise ValueError("Missing/ambiguous source fragment: refusing changes")
        text = text.replace(old, new, 1)
    if text.count("$s['codes'][hash('sha256',$code)]") != 1:
        raise ValueError("Authorization code is not issued exactly once")
    return text.encode("utf-8")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    args.destination.write_bytes(patch_source(args.source.read_bytes()))
    print("Verified atomic OAuth code-issuance patch applied in a local temporary file.")
