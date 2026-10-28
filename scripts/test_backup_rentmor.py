"""Unit tests for private backup snapshot validation; never use production data."""
import json
import tempfile
import unittest
from pathlib import Path
from backup_rentmor import REQUIRED, validate

class BackupChecks(unittest.TestCase):
    def test_valid_and_damaged_snapshots(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in REQUIRED:
                (root / name).write_text("[]", encoding="utf-8")
            self.assertEqual(set(validate(root)), REQUIRED)
            (root / "reservations.json").write_text("{damaged", encoding="utf-8")
            with self.assertRaises(ValueError):
                validate(root)
            (root / "reservations.json").write_text("[]", encoding="utf-8")
            (root / "reservations.json").unlink()
            with self.assertRaises(ValueError):
                validate(root)

    def test_notebook_is_optional_but_must_be_valid_if_archived(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in REQUIRED:
                (root / name).write_text("[]", encoding="utf-8")
            self.assertNotIn("notebook.json", validate(root))
            (root / "notebook.json").write_text(json.dumps({"revision": 1, "notes": {}, "processed": {}}), encoding="utf-8")
            self.assertIn("notebook.json", validate(root))
            (root / "notebook.json").write_text(json.dumps({"notes": []}), encoding="utf-8")
            with self.assertRaises(ValueError):
                validate(root)

    def test_schema_for_sensitive_config(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in REQUIRED:
                (root / name).write_text("[]", encoding="utf-8")
            (root / "protocol_devices.json").write_text("[]", encoding="utf-8")
            with self.assertRaises(ValueError):
                validate(root)
            (root / "protocol_devices.json").write_text(json.dumps({"devices":[]}), encoding="utf-8")
            self.assertIn("protocol_devices.json", validate(root))

if __name__ == "__main__":
    unittest.main()
