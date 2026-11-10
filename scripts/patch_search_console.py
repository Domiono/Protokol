#!/usr/bin/env python3
"""Add three exact hooks to a CURRENT admin.php; never deploy a stale full copy."""
from pathlib import Path
import sys

HOOKS = [
    ("$is_logged=!empty($_SESSION['logged_in'])&&!empty($_SESSION['rm_mfa_verified']);",
     "\n// Search Console: after the existing password, MFA and session validation.\nrequire_once __DIR__ . '/search_console_admin.php';\nrm_gsc_admin_handle($is_logged);", "rm_gsc_admin_handle($is_logged);"),
    ('                <a href="?tab=messages"',
     '''                <a href="?tab=google" class="px-4 py-2 rounded-lg text-xs font-black transition <?php echo $active_tab == 'google' ? 'bg-yellow-400 text-black' : ''; ?>">GOOGLE / SEO</a>\n\n''', 'href="?tab=google"'),
    ("        <?php if ($active_tab == 'dashboard'): ?>",
     "        <?php if ($active_tab == 'google') rm_gsc_admin_render($is_logged, $products); ?>\n\n", "rm_gsc_admin_render($is_logged, $products);")
]

def patch(source: str) -> str:
    # Unknown/partial templates are refused, even when one insertion already exists.
    for anchor, insertion, marker in HOOKS:
        if source.count(anchor) != 1:
            raise ValueError('Unrecognized admin template: exact anchor count')
        if marker in source and source.count(marker) != 1:
            raise ValueError('Duplicate Search Console hook')
    result = source
    for i, (anchor, insertion, marker) in enumerate(HOOKS):
        if marker in result:
            continue
        result = result.replace(anchor, anchor + insertion if i == 0 else insertion + anchor, 1)
    return result

if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: patch_search_console.py CURRENT_ADMIN_PATH')
    file = Path(sys.argv[1])
    original = file.read_text(encoding='utf-8')
    result = patch(original)
    file.write_text(result, encoding='utf-8')
