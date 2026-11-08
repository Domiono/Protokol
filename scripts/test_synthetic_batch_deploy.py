#!/usr/bin/env python3
"""Offline-only deployment transaction simulator. NEVER deploys to AZ.pl.

The only writable targets are fixture-* files under a fresh temporary directory.
It tests the algorithm needed for future RentMor Control batch deployments:
whole-batch SHA preflight, safe staging, PHP parse, atomic replacement,
post-change verification and restoration of every file after failure.
"""
from __future__ import annotations

import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

MARKER = ".rentmor-synthetic-test-only"
MARKER_TEXT = "LOCAL TEST DATA ONLY\n"
ALLOWED = re.compile(r"^fixture-[a-z0-9-]{1,45}\.(php|js|css)$")
MIN_FILES = 2
MAX_FILES = 4  # Mirrors RentMor Control stage_safe_batch contract
MAX_BYTES = 65_536


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def atomic_write(target: Path, content: bytes) -> None:
    fd, temp_name = tempfile.mkstemp(prefix=".rentmor-fixture-", dir=target.parent)
    try:
        with os.fdopen(fd, "wb") as dest:
            dest.write(content)
            dest.flush()
            os.fsync(dest.fileno())
        os.replace(temp_name, target)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)


def preflight(root: Path, changes: list[dict]) -> list[tuple[Path, bytes, bytes]]:
    root = Path(root)
    tmp_root = Path(tempfile.gettempdir()).resolve()
    if (root.is_symlink() or root.resolve() == tmp_root or
            tmp_root not in root.resolve().parents or
            not (root / MARKER).is_file() or
            (root / MARKER).read_text(encoding="utf-8") != MARKER_TEXT):
        raise ValueError("Only a marked synthetic temporary directory is allowed")
    if not MIN_FILES <= len(changes) <= MAX_FILES:
        raise ValueError("Invalid batch size")
    seen = set()
    prepared = []
    for entry in changes:
        name = entry.get("name", "")
        if not isinstance(name, str) or not ALLOWED.fullmatch(name) or name in seen:
            raise ValueError("Only distinct synthetic fixture files are permitted")
        seen.add(name)
        target = root / name
        if target.is_symlink() or not target.is_file() or target.resolve().parent != root.resolve():
            raise ValueError("Missing, linked or nonlocal target")
        before = target.read_bytes()
        old_hash = entry.get("expected_sha256", "")
        if not isinstance(old_hash, str) or not re.fullmatch(r"[a-f0-9]{64}", old_hash):
            raise ValueError("Missing or malformed expected SHA256")
        if sha(before) != old_hash:
            raise ValueError("Stale SHA256: full batch refused")
        replacement = entry.get("content")
        if not isinstance(replacement, str):
            raise ValueError("Replacement must be UTF-8 text")
        after = replacement.encode("utf-8")
        if len(after) > MAX_BYTES or len(before) > MAX_BYTES:
            raise ValueError("Fixture exceeds test byte limit")
        if target.suffix == ".php":
            if not shutil.which("php"):
                raise RuntimeError("PHP CLI missing; cannot claim PHP syntax validated")
            with tempfile.TemporaryDirectory(dir=root) as d:
                candidate = Path(d) / "test.php"
                candidate.write_bytes(after)
                result = subprocess.run(
                    ["php", "-n", "-l", str(candidate)],
                    capture_output=True, timeout=8, check=False
                )
            if result.returncode:
                raise ValueError("PHP syntax rejected before any write")
        prepared.append((target, before, after))
    return prepared


def simulate_batch(root: Path, changes: list[dict], *, fail_after: int | None = None,
                   healthcheck=None) -> list[str]:
    """Commit all fixtures or restore all; exceptional rollback is always reported."""
    prepared = preflight(root, changes)
    written = []
    try:
        for idx, (path, before, after) in enumerate(prepared, 1):
            atomic_write(path, after)
            written.append((path, before))
            if fail_after == idx:
                raise RuntimeError("Injected write failure")
        for path, _, after in prepared:
            if sha(path.read_bytes()) != sha(after):
                raise RuntimeError("Post-write checksum mismatch")
        if healthcheck is not None and not healthcheck(root):
            raise RuntimeError("Simulated post-deployment health check failure")
        return [sha(after) for _, _, after in prepared]
    except Exception as original:
        rollback_problems = []
        for path, before in reversed(written):
            try:
                atomic_write(path, before)
                if sha(path.read_bytes()) != sha(before):
                    raise OSError("Restore hash verification failed")
            except OSError as exc:
                rollback_problems.append(f"{path.name}: {type(exc).__name__}")
        if rollback_problems:
            raise RuntimeError("ROLLBACK INCOMPLETE: " + "; ".join(rollback_problems)) from original
        raise RuntimeError("Batch refused; all modified fixtures restored") from original


class SyntheticTransactionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="rentmor-batch-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / MARKER).write_text(MARKER_TEXT, encoding="utf-8")
        self.a = self.root / "fixture-home.css"
        self.b = self.root / "fixture-app.js"
        self.a.write_text("body { color: black; }\n", encoding="utf-8")
        self.b.write_text("const count = 1;\n", encoding="utf-8")

    def batch(self):
        return [
            {"name": self.a.name, "expected_sha256": sha(self.a.read_bytes()),
             "content": "body { color: blue; }\n"},
            {"name": self.b.name, "expected_sha256": sha(self.b.read_bytes()),
             "content": "const count = 2;\n"},
        ]

    def test_two_files_commit_after_full_preflight(self):
        resulting = simulate_batch(self.root, self.batch(), healthcheck=lambda root: True)
        self.assertEqual(len(resulting), 2)
        self.assertIn("blue", self.a.read_text())
        self.assertIn("count = 2", self.b.read_text())


    def four_file_batch(self):
        third = self.root / "fixture-third.css"
        fourth = self.root / "fixture-fourth.js"
        third.write_text("h1 { display: block; }\\n", encoding="utf-8")
        fourth.write_text("const ready = false;\\n", encoding="utf-8")
        return self.batch() + [
            {"name": third.name, "expected_sha256": sha(third.read_bytes()),
             "content": "h1 { display: flex; }\\n"},
            {"name": fourth.name, "expected_sha256": sha(fourth.read_bytes()),
             "content": "const ready = true;\\n"},
        ]

    def test_four_files_commit_after_full_preflight(self):
        batch = self.four_file_batch()
        hashes = simulate_batch(self.root, batch)
        self.assertEqual(len(hashes), 4)
        for entry, checksum in zip(batch, hashes):
            data = (self.root / entry["name"]).read_bytes()
            self.assertEqual(data, entry["content"].encode("utf-8"))
            self.assertEqual(sha(data), checksum)

    def test_one_file_refused_by_host_contract(self):
        before = self.a.read_bytes()
        with self.assertRaisesRegex(ValueError, "batch size"):
            simulate_batch(self.root, self.batch()[:1])
        self.assertEqual(self.a.read_bytes(), before)

    def test_five_files_refused_by_host_contract(self):
        batch = self.four_file_batch()
        fifth = self.root / "fixture-fifth.css"
        fifth.write_text("p { margin: 0; }\\n", encoding="utf-8")
        batch.append({"name": fifth.name, "expected_sha256": sha(fifth.read_bytes()),
                      "content": "p { margin: 1px; }\\n"})
        with self.assertRaisesRegex(ValueError, "batch size"):
            simulate_batch(self.root, batch)
        self.assertEqual(self.a.read_text(), "body { color: black; }\\n")

    def test_stale_sha_on_fourth_file_aborts_whole_batch(self):
        batch = self.four_file_batch()
        before = {entry["name"]: (self.root / entry["name"]).read_bytes()
                  for entry in batch}
        batch[3]["expected_sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "Stale"):
            simulate_batch(self.root, batch)
        for name, original in before.items():
            self.assertEqual((self.root / name).read_bytes(), original)

    def test_fourth_write_failure_restores_all_four_files(self):
        batch = self.four_file_batch()
        before = {entry["name"]: (self.root / entry["name"]).read_bytes()
                  for entry in batch}
        with self.assertRaisesRegex(RuntimeError, "restored"):
            simulate_batch(self.root, batch, fail_after=4)
        for name, original in before.items():
            self.assertEqual((self.root / name).read_bytes(), original)

    def test_stale_sha_refuses_entire_batch(self):
        batch = self.batch()
        original = self.a.read_bytes()
        batch[1]["expected_sha256"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "Stale"):
            simulate_batch(self.root, batch)
        self.assertEqual(self.a.read_bytes(), original)

    def test_injected_second_write_rolls_back_both(self):
        old_a, old_b = self.a.read_bytes(), self.b.read_bytes()
        with self.assertRaisesRegex(RuntimeError, "restored"):
            simulate_batch(self.root, self.batch(), fail_after=2)
        self.assertEqual((self.a.read_bytes(), self.b.read_bytes()), (old_a, old_b))

    def test_post_deploy_health_failure_rolls_back(self):
        old_a, old_b = self.a.read_bytes(), self.b.read_bytes()
        with self.assertRaisesRegex(RuntimeError, "restored"):
            simulate_batch(self.root, self.batch(), healthcheck=lambda root: False)
        self.assertEqual((self.a.read_bytes(), self.b.read_bytes()), (old_a, old_b))

    def test_duplicate_refused(self):
        batch = self.batch()
        batch[1]["name"] = batch[0]["name"]
        with self.assertRaisesRegex(ValueError, "distinct"):
            simulate_batch(self.root, batch)

    def test_symlink_escape_refused(self):
        outside = self.root / "outside.txt"
        outside.write_text("untouched")
        self.a.unlink()
        self.a.symlink_to(outside)
        with self.assertRaisesRegex(ValueError, "linked"):
            simulate_batch(self.root, self.batch())
        self.assertEqual(outside.read_text(), "untouched")

    def test_protected_path_refused(self):
        batch = self.batch()
        batch[1]["name"] = "../owner.json"
        with self.assertRaisesRegex(ValueError, "fixture"):
            simulate_batch(self.root, batch)

    def test_unmarked_root_refused(self):
        (self.root / MARKER).unlink()
        with self.assertRaisesRegex(ValueError, "synthetic"):
            simulate_batch(self.root, self.batch())

    @unittest.skipUnless(shutil.which("php"), "PHP CLI not available")
    def test_bad_php_syntax_aborts_other_file(self):
        php = self.root / "fixture-parse.php"
        php.write_text("<?php echo 1;\n")
        batch = self.batch()
        before = self.a.read_bytes()
        batch.append({"name": php.name, "expected_sha256": sha(php.read_bytes()),
                      "content": "<?php function invalid( {\n"})
        with self.assertRaisesRegex(ValueError, "syntax"):
            simulate_batch(self.root, batch)
        self.assertEqual(self.a.read_bytes(), before)


if __name__ == "__main__":
    unittest.main(verbosity=2)
