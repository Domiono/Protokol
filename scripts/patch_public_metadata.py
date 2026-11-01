"""Idempotently enable metadata on the actual public PHP template."""
from pathlib import Path
import re
import sys

MARKER = '/* rentmor-public-metadata-v1 */'
INCLUDE = "<?php " + MARKER + " require_once __DIR__ . '/public_metadata.php'; ob_start('rentmor_public_metadata'); ?>\n"

def patch(source):
    if source.count(MARKER) > 1:
        raise ValueError('Duplicate metadata hook')
    if MARKER in source:
        if INCLUDE not in source:
            raise ValueError('Incomplete metadata hook')
        return source
    matches = list(re.finditer(r'<!doctype\s+html\s*>', source, re.I))
    if len(matches) != 1:
        raise ValueError('Expected one public HTML document')
    pos = matches[0].start()
    return source[:pos] + INCLUDE + source[pos:]

if __name__ == '__main__':
    p = Path(sys.argv[1])
    p.write_text(patch(p.read_text()))
