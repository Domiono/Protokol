#!/usr/bin/env python3
"""Check current WT20i video metadata and the PDF independently of site deployment.

oEmbed verifies public video metadata, not playback on every device.
External outages fail this check but do not block publishing the RentMor site.
"""
import json
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from concurrent.futures import ThreadPoolExecutor
from html.parser import HTMLParser

GUIDE = "https://rentmor.pl/poradnik-agregat-malarski-jak-malowac-dobor-dysz.php"
VIDEOS = ("-GtS3HWavo8", "8OgLnaFO1Jk")
PDF = "https://static.wabrotech.pl/public/products/29/29/files/wt20i-user-manual_06.07.2026-1783331866.pdf"

class Links(HTMLParser):
    def __init__(self, html):
        super().__init__()
        self.links, self.frames = [], []
        self.feed(html)
    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "a": self.links.append(attrs.get("href"))
        if tag == "iframe": self.frames.append(attrs.get("src"))

def request(url):
    return urlopen(Request(url, headers={"User-Agent": "RentMorResourceCheck/1.0"}), timeout=30)

def check_video(video_id):
    url = "https://www.youtube.com/oembed?" + urlencode({
        "url": "https://www.youtube.com/watch?v=" + video_id, "format": "json"})
    with request(url) as response:
        data = json.load(response)
    if data.get("type") != "video" or data.get("author_name", "").upper() != "WABROTECH":
        raise ValueError("Unexpected video metadata: " + video_id)
    return "PASS YouTube metadata: " + video_id

def check_pdf():
    with request(PDF) as response:
        if response.status != 200 or not response.read(5).startswith(b"%PDF-"):
            raise ValueError("WT20i instruction is not a readable PDF")
    return "PASS WT20i PDF"

def main():
    with request(GUIDE) as response:
        page = Links(response.read().decode("utf-8"))
    if PDF not in page.links:
        raise ValueError("Current guide is missing the expected instruction")
    for video in VIDEOS:
        if "https://www.youtube-nocookie.com/embed/" + video not in page.frames:
            raise ValueError("Current guide video changed: " + video)
    with ThreadPoolExecutor(max_workers=3) as pool:
        futures = [pool.submit(check_video, video) for video in VIDEOS] + [pool.submit(check_pdf)]
        errors = []
        for future in futures:
            try: print(future.result())
            except Exception as error:
                errors.append(str(error))
                print("FAIL external resource: " + str(error))
    if errors:
        raise SystemExit(1)

if __name__ == "__main__":
    main()
