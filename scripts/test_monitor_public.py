#!/usr/bin/env python3
"""Offline tests for the read-only monitor; no network calls."""
import io
import unittest
from unittest.mock import patch

import monitor_public as monitor


class FakeResponse:
    def __init__(self, code: int, body: str, content_type="text/html"):
        self.status = code
        self.buffer = io.BytesIO(body.encode("utf-8"))
        self.headers = {"Content-Type": content_type}

    def read(self, n):
        return self.buffer.read(n)

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.buffer.close()


class FakeOpener:
    def __init__(self, response):
        self.response = response
        self.requests = []

    def open(self, req, timeout):
        self.requests.append((req.full_url, req.get_method(), timeout))
        return self.response


class MonitorTests(unittest.TestCase):
    def test_homepage_is_read_only_get(self):
        opener = FakeOpener(FakeResponse(200, "<html>RentMor</html>"))
        times = iter([0, 0.245])
        result = monitor.check("/", 200, "html", opener=opener,
                               clock=lambda: next(times))
        self.assertEqual(result["duration_s"], 0.245)
        self.assertEqual(opener.requests, [(monitor.BASE + "/", "GET", 18)])

    def test_api_requires_real_json_refusal(self):
        opener = FakeOpener(FakeResponse(401, '{"ok": false}', "application/json"))
        result = monitor.check("/protocol_sync.php", 401, "auth", opener=opener,
                               clock=iter([0, 1]).__next__)
        self.assertEqual(result["status"], 401)
        bad = FakeOpener(FakeResponse(401, '{"ok":true}', "application/json"))
        with self.assertRaisesRegex(RuntimeError, "not safely rejected"):
            monitor.check("/protocol_sync.php", 401, "auth", opener=bad)

    def test_health_requires_content_and_no_silent_redirect(self):
        opener = FakeOpener(FakeResponse(200, "maintenance"))
        with self.assertRaisesRegex(RuntimeError, "HTML absent"):
            monitor.check("/", 200, "html", opener=opener)
        self.assertIsNone(monitor.NoRedirect().redirect_request(
            None, None, 302, "redirect", {}, "https://other.invalid"))

    def test_only_fixed_public_endpoints(self):
        for path in ("../private", "https://example.org", "/admin.php"):
            with self.assertRaisesRegex(ValueError, "Non-allowlisted"):
                monitor.check(path, 200, "html", opener=FakeOpener(FakeResponse(200, "x")))
        with self.assertRaises(ValueError):
            monitor.check("/", 200, "html", base="https://other.invalid")

    def test_too_slow_is_failure(self):
        times = iter([0, monitor.SLOW_SECONDS + 0.1])
        with self.assertRaisesRegex(RuntimeError, "exceeded"):
            monitor.check("/", 200, "html",
                          opener=FakeOpener(FakeResponse(200, "<html>RentMor</html>")),
                          clock=lambda: next(times))

    def test_sitemap_and_robots_semantics(self):
        monitor.check("/robots.txt", 200, "text", opener=FakeOpener(
            FakeResponse(200, "User-Agent: *")))
        monitor.check("/sitemap.php", 200, "xml", opener=FakeOpener(
            FakeResponse(200, '<?xml version="1.0"?><urlset></urlset>', "application/xml")))
        with self.assertRaisesRegex(RuntimeError, "sitemap"):
            monitor.check("/sitemap.php", 200, "xml", opener=FakeOpener(
                FakeResponse(200, "<html>broken</html>")))

    def test_final_failure_does_not_leak_body(self):
        bad_opener = FakeOpener(FakeResponse(503, "PRIVATE_RESPONSE_BODY"))
        with self.assertRaises(SystemExit) as error:
            monitor.run(checks=[("/", 200, "html")], opener=bad_opener, sleep=lambda _: None)
        self.assertNotIn("PRIVATE_RESPONSE_BODY", str(error.exception))


if __name__ == "__main__":
    unittest.main(verbosity=2)
