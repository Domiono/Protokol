#!/usr/bin/env python3
"""Emit only expired, allowlisted private snapshot names. Input: SFTP 'cls -1'."""
import argparse
import datetime as dt
import re
import sys

DATA = re.compile(r"^(\d{4}-\d{2}-\d{2})-run-(\d{5,15})$")
PHOTOS = re.compile(r"^(\d{4}-\d{2}-\d{2})-run-(\d{5,15})\.tar\.gz$")

def expired(lines, kind, *, today=None):
    today = today or dt.datetime.now(dt.timezone.utc).date()
    cutoff = today - dt.timedelta(days=30 if kind=="data" else 35)
    matcher = DATA if kind=="data" else PHOTOS
    to_delete = []
    for line in lines:
        name = line.strip().strip("/")
        if "/" in name or not matcher.fullmatch(name):
            continue
        try:
            date = dt.date.fromisoformat(matcher.fullmatch(name).group(1))
        except ValueError:
            continue
        if date < cutoff:
            to_delete.append(name)
    # Retain at least 2 snapshot folders/files (even if very old) in the
    # expected date/run naming format. Historical YYYY-MM-DD folders untouched.
    matches = [x.strip().strip("/") for x in lines
               if matcher.fullmatch(x.strip().strip("/"))]
    protected = set(sorted(matches)[-2:])
    return [x for x in sorted(set(to_delete)) if x not in protected]

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("kind", choices=["data", "photos"])
    args = parser.parse_args()
    for name in expired(sys.stdin.readlines(), args.kind):
        print(name)
