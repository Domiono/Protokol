#!/usr/bin/env python3
"""Isolated, read-only regression tests for RentMor SEO deployment patches."""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import patch_seo_template
import patch_seo_titles
import patch_canonical_host


class SEOPatches(unittest.TestCase):
    def test_template_insert_and_repeat(self):
        template = """<html><head></head><body><?php
        echo rentmor_render_description($product['desc'] ?? '');
        ?></body></html>"""
        patched = patch_seo_template.patch(template.replace("</head>", "\n</head>"))
        self.assertEqual(patched, patch_seo_template.patch(patched))
        self.assertEqual(patched.count("rentmor_offer_details($product)"), 1)
        self.assertEqual(patched.count('type="application/ld+json"'), 1)
        self.assertIn("require_once __DIR__ . '/rental_offer_details.php'", patched)
        self.assertIn('"@type": "Organization"', patched)
        self.assertNotIn("rentmor-logo.png", patched)
        with self.assertRaises(ValueError):
            patch_seo_template.patch(template.replace("echo rentmor_render_description($product['desc'] ?? '');", "echo 'other';"))
        with self.assertRaises(ValueError):
            patch_seo_template.patch(patched.replace("<!-- rentmor-offer-details-v1 -->", "<!-- rentmor-offer-details-v1 --><!-- rentmor-offer-details-v1 -->"))

    def test_titles_isolated_and_idempotent(self):
        keys = ["Wabrotech WT20i", "Ozonator ULSONIX", "Zaciskarka Energotytan",
                "Drabina Krause", "Bruzdownica Stalco", "Zestaw szlifierka Stanley",
                "Szlifierka Stanley, żyrafa"]
        products = [{"id": str(i), "name": name, "price": 123, "deposit": 456,
                     "slug": "unchanged-"+str(i), "stock": 3, "seo_title": "before",
                     "desc": "ZESTA ZAWIERA:" if i == 5 else "Original"}
                    for i, name in enumerate(keys)]
        products[5]["id"] = "69385df56774a"
        fixed = patch_seo_titles.patch(products)
        self.assertEqual(fixed, patch_seo_titles.patch(fixed))
        self.assertEqual([p["price"] for p in fixed], [123]*len(keys))
        self.assertEqual([p["slug"] for p in fixed], [p["slug"] for p in products])
        self.assertEqual(products[5]["desc"], "ZESTA ZAWIERA:")
        self.assertEqual(fixed[5]["desc"], "ZESTAW ZAWIERA:")
        self.assertTrue(all(p["seo_title"].endswith(" | RentMor") for p in fixed))
        with self.assertRaises(ValueError):
            patch_seo_titles.patch(products + [dict(products[0], id="extra")])
        with self.assertRaises(ValueError):
            patch_seo_titles.patch(products[:-1])

    def test_canonical_append_preserves_original_and_is_idempotent(self):
        old = "Options -Indexes\nRewriteEngine On\nRewriteRule ^old$ index.php [L]\n"
        updated = patch_canonical_host.patch(old)
        self.assertEqual(updated, patch_canonical_host.patch(updated))
        self.assertTrue(updated.endswith(old))
        self.assertIn("RewriteCond %{REQUEST_METHOD} ^(?:GET|HEAD)$", updated)
        self.assertIn("https://rentmor.pl%{REQUEST_URI}", updated)
        self.assertIn("HTTP:X-Forwarded-Proto", updated)
        with self.assertRaises(ValueError):
            patch_canonical_host.patch(updated.replace("# BEGIN RentMor canonical host v1", "# BEGIN RentMor canonical host v1\n# BEGIN RentMor canonical host v1"))

    def test_php_offer_helper_output_and_escaping(self):
        php = r"""require 'server/rental_offer_details.php';
        $known=['69388418830b8','69385df56774a','69385f95de01c'];
        foreach ($known as $id) {
          $body=rentmor_offer_details(['id'=>$id]);
          if (!str_contains($body,'<h2') || !str_contains($body,'/zasady.php') ||
              !str_contains($body,'data-rentmor-offer-details')) exit(2);
        }
        if (rentmor_offer_details(['id'=>'unknown'])!=='') exit(3);
        echo 'ok';"""
        if not __import__("shutil").which("php"):
            self.skipTest("php-cli missing")
        result = subprocess.run(["php", "-r", php], cwd=ROOT, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "ok")


if __name__ == "__main__":
    unittest.main()
