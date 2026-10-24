import unittest
from verify_public_seo import validate

class SEOChecks(unittest.TestCase):
    url = 'https://rentmor.pl/test/'
    def html(self):
        return f'<link href="{self.url}" rel="canonical"><h1>Test</h1>'
    def test_accepts_self_canonical(self):
        validate(self.html(), {}, self.url)
    def test_rejects_wrong_or_duplicate_canonical(self):
        for html in [self.html().replace(self.url, self.url+'wrong'), self.html()*2]:
            with self.assertRaises(AssertionError): validate(html, {}, self.url)
    def test_rejects_header_and_reordered_meta_noindex(self):
        for html, headers in [(self.html(), {'X-Robots-Tag':'noindex'}), (self.html()+'<meta content="noindex" name="robots">', {}), (self.html()+'<meta name="googlebot" content="none">', {})]:
            with self.assertRaises(AssertionError): validate(html, headers, self.url)

if __name__ == '__main__': unittest.main()
