#!/usr/bin/env python3
"""Check live server output after publication (not just prepared PHP)."""
from html.parser import HTMLParser
from urllib.request import Request, urlopen
from pathlib import Path
import sys
from patch_hero_blueprint import ASSET, MARKER

class Elements(HTMLParser):
    def __init__(self):
        super().__init__()
        self.heroes = []
    def handle_starttag(self, tag, attrs):
        d = dict(attrs)
        if d.get("data-rentmor-blueprint") == "v1":
            self.heroes.append((tag, d))

def verify(html):
    p = Elements()
    p.feed(html)
    assert len(p.heroes) == 1 and p.heroes[0][0] in ("header", "section"), p.heroes
    assert f'<style id="{MARKER}">' in html
    assert ASSET in html
    assert "Wypożyczalnia sprzętu budowlanego i ogrodniczego w Krakowie" in html or "WYPOŻYCZALNIA" in html
    assert "Prosty i tani wynajem" in html
    print("PASS: published homepage has blueprint background and existing real text")

if __name__ == "__main__":
    with urlopen(Request("https://rentmor.pl/?hero_blueprint_check=1",
                         headers={"Cache-Control": "no-cache",
                                  "User-Agent": "RentMorBlueprintCheck/1.0"}), timeout=40) as resp:
        assert resp.status == 200
        verify(resp.read().decode("utf-8"))
