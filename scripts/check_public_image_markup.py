#!/usr/bin/env python3
"""Assert public pages actually render original-preserving responsive image markup."""
import json
from urllib.request import Request, urlopen
from audit_public_assets import Audit, URLS

def main():
    for url in URLS:
        with urlopen(Request(url,headers={"User-Agent":"RentMorImageSmoke/1.0", "Cache-Control":"no-cache"}),timeout=30) as response:
            assert response.status == 200, (url, response.status)
            html=response.read().decode("utf-8","replace")
        parsed=Audit(); parsed.feed(html)
        photos=[a for a in parsed.images if "/uploads/" in a.get("src","")]
        ready=[a for a in photos if a.get("data-rentmor-optimized")=="1"
               and a.get("srcset") and a.get("width") and a.get("height")]
        logo=[a for a in parsed.images if a.get("src")=="/rentmor-logo.png"]
        assert logo and logo[0].get("srcset"), ("Optimized logo missing",url)
        assert len(ready) >= (35 if url.endswith("/") else 1), ("Responsive photographs absent",url,len(ready),len(photos))
        for img in ready:
            assert img["src"].startswith("/uploads/"), "Full-resolution original URL must remain in src"
            assert ".webp" in img["srcset"]
            assert img.get("decoding") == "async"
        print(json.dumps({"page":url,"original_upload_img_count":len(photos),
                          "optimized_img_count":len(ready),
                          "logo_optimized":True},ensure_ascii=False))
    print("PASS: live HTML has responsive WebP srcsets, original URLs and intrinsic dimensions.")

if __name__=="__main__":
    main()
