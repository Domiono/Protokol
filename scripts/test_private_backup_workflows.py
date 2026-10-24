"""Static regression checks: deployments must fail closed without private backups."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = (
    ROOT / ".github/workflows/deploy-rentmor.yml",
    ROOT / ".github/workflows/deploy-gallery.yml",
)


class PrivateBackupWorkflowTests(unittest.TestCase):
    def test_each_homepage_deployment_verifies_off_webroot_backup_first(self):
        for path in WORKFLOWS:
            with self.subTest(workflow=path.name):
                source = path.read_text(encoding="utf-8")
                self.assertNotIn("public_html/.rentmor-backups", source)
                self.assertNotIn("public_html/rentmor-backups", source)
                self.assertNotIn("mkdir -p rentmor-backups", source)
                self.assertIn("$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT.php", source)
                save = source.index("; cd rentmor-backups; put ")
                readback = source.index("get rentmor-backups/$BACKUP_NAME", save)
                comparison = source.index("cmp ", readback)
                publish = re.search(
                    r"put \\$[A-Z_]+/index\\.php -o public_html/index\\.php", source
                )
                self.assertIsNotNone(publish)
                self.assertLess(save, readback)
                self.assertLess(readback, comparison)
                self.assertLess(comparison, publish.start())


if __name__ == "__main__":
    unittest.main()
