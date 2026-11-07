#!/usr/bin/env python3
"""Update only the known RentMor OAuth core CSP line for ChatGPT redirects."""
import argparse
import hashlib
from pathlib import Path

EXPECTED_SHA='91a1a3a804110f2270e625c4e92c1a607f9873056bc7916fa76f4085a69e3576'
OLD="""    header("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");"""
NEW="""    // Chromium checks form-action against the full POST->302 redirect chain.
    // Allow the trusted ChatGPT callback ONLY on the OAuth GET approval form.
    $isApprovalForm=(basename((string)($_SERVER['SCRIPT_NAME']??''))==='oauth.php' &&
        ($_GET['op']??'')==='authorize' && ($_SERVER['REQUEST_METHOD']??'GET')==='GET');
    $formTargets=$isApprovalForm ? "'self' https://chatgpt.com" : "'self'";
    header("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action ".$formTargets."; base-uri 'none'; frame-ancestors 'none'");"""

def patch(raw: bytes) -> bytes:
    if hashlib.sha256(raw).hexdigest()!=EXPECTED_SHA:
        raise ValueError('Unknown live core checksum: refuse deployment')
    text=raw.decode('utf-8')
    if text.count(OLD)!=1:
        raise ValueError('Missing/ambiguous original CSP line: refuse deployment')
    result=text.replace(OLD,NEW,1)
    if result.count("https://chatgpt.com")!=1:
        raise ValueError('Unexpected ChatGPT CSP entries')
    return result.encode('utf-8')

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('source',type=Path)
    parser.add_argument('destination',type=Path)
    args=parser.parse_args()
    args.destination.write_bytes(patch(args.source.read_bytes()))
    print('CSP fix verified and applied to temporary OAuth core only.')
