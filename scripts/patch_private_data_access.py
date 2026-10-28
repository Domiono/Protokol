#!/usr/bin/env python3
"""Preserve live Apache rules and protect sensitive RentMor data files."""
from pathlib import Path
import sys

MARKER = '# BEGIN RentMor private-data access v1'
RULES = r'''# BEGIN RentMor private-data access v1
# PHP reads these files directly; clients must not receive their contents.
<FilesMatch "(?i)^(?:data|reservations|customers|costs|protocols|messages|stats|terms|protocol_devices|online_sessions|rental_addons|product_stats|messages_archive)\.json$">
  Require all denied
</FilesMatch>
# In-flight writes and sidecar locks may contain private data as well.
<FilesMatch "(?i)\.(?:lock|tmp)$">
  Require all denied
</FilesMatch>
# END RentMor private-data access v1

'''

def patch(source: str) -> str:
    if MARKER in source:
        if source.count(MARKER) != 1 or source.count('# END RentMor private-data access v1') != 1:
            raise ValueError('Incomplete or repeated private-data rule')
        return source
    return RULES + source

if __name__ == '__main__':
    file = Path(sys.argv[1])
    file.write_text(patch(file.read_text()))
    print('Private JSON, locks and temporary files protected in Apache; other rules preserved.')
