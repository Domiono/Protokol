#!/usr/bin/env python3
from pathlib import Path
import unittest
from patch_search_console import patch, HOOKS

class PatchTests(unittest.TestCase):
    def test_current_and_future_template(self):
        source=(Path(__file__).resolve().parents[1]/'server/admin.php').read_text()
        patched=patch(source)
        self.assertEqual(patch(patched),patched)
        # Newer production integrations must survive; strip only our known insertions.
        original=source
        for _,insertion,_ in HOOKS:
            original=original.replace(insertion,'')
        newer=original.replace('STATY','NOWSZY PANEL + MORTII')
        result=patch(newer)
        for _,insertion,_ in HOOKS:
            self.assertIn(insertion,result)
            result=result.replace(insertion,'')
        self.assertEqual(result,newer)
    def test_refuse_unknown_or_duplicate(self):
        with self.assertRaises(ValueError): patch('<?php echo "unknown";')
        source=(Path(__file__).resolve().parents[1]/'server/admin.php').read_text()
        with self.assertRaises(ValueError): patch(source+HOOKS[0][0])
    def test_auth_order(self):
        source=patch((Path(__file__).resolve().parents[1]/'server/admin.php').read_text())
        hook=source.index('rm_gsc_admin_handle($is_logged);')
        self.assertLess(source.index('rm_password_version'),hook)
        self.assertLess(source.index("$is_logged=!empty($_SESSION['logged_in'])&&!empty($_SESSION['rm_mfa_verified']);"),hook)
        self.assertLess(hook,source.index('<!DOCTYPE html>'))

if __name__=='__main__': unittest.main()
