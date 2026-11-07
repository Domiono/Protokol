#!/usr/bin/env python3
"""Strictly patch known atomic RentMor Control OAuth for idempotent retry redirects."""
import argparse
from pathlib import Path
import hashlib

EXPECTED_SHA = '28dc2b277fec5f2dbd16331e94c2b3db20a81f11fe7e04807aeff40d78a6d772'
REPLACEMENTS = [
    (
        "        $code=rm_random_token();\n        $result=rm_state(function (&$s)use($nonce,$verified,$signed,$ticketStatus,$code){",
        "        // Stable, unpredictable code for the same nonce: retried POST must redirect\n"
        "        // to the ORIGINAL unredeemed code, never mint a second code.\n"
        "        $code=rtrim(strtr(base64_encode(hash_hmac('sha256',\"rm:oauth:code:v1\\0\".$nonce,$owner['password_hash'],true)),'+/','-_'),'=');\n"
        "        $result=rm_state(function (&$s)use($nonce,$verified,$signed,$ticketStatus,$code){",
    ),
    (
        "            if(is_array($row)&&!empty($row['used']))return ['status'=>'D06'];",
        "            if(is_array($row)&&!empty($row['used'])){\n"
        "                // On browser/network retry return ONLY the identical pending code.\n"
        "                // Once token endpoint consumes it, replay stays blocked with D06.\n"
        "                $pending=$s['codes'][hash('sha256',$code)]??null;\n"
        "                if(is_array($pending)&&($pending['expires']??0)>time()&&($pending['p']??null)===$signed)\n"
        "                    return ['status'=>'ok','p'=>$signed];\n"
        "                return ['status'=>'D06'];\n"
        "            }",
    ),
]

def patch(original: bytes) -> bytes:
    if hashlib.sha256(original).hexdigest() != EXPECTED_SHA:
        raise ValueError('Unexpected OAuth revision, refusing to patch')
    text = original.decode('utf-8')
    for before, after in REPLACEMENTS:
        if text.count(before) != 1:
            raise ValueError('Missing or ambiguous OAuth block, refusing to patch')
        text = text.replace(before, after, 1)
    if text.count("$s['codes'][hash('sha256',$code)]=") != 1:
        raise ValueError('Incorrect number of authorization-code writes')
    if "if(is_array($pending)&&($pending['expires']??0)>time()&&($pending['p']??null)===$signed)" not in text:
        raise ValueError('Retry guard missing')
    return text.encode('utf-8')

if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('source', type=Path)
    ap.add_argument('destination', type=Path)
    args = ap.parse_args()
    args.destination.write_bytes(patch(args.source.read_bytes()))
    print('Patched known OAuth revision for same-code retry; PKCE and single-use token exchange unchanged.')
