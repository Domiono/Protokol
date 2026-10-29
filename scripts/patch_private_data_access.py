#!/usr/bin/env python3
"""Preserve live Apache rules and protect sensitive RentMor data files."""
from pathlib import Path
import sys
import re

MARKER = '# BEGIN RentMor private-data access v2'
RULES = r'''# BEGIN RentMor private-data access v2
# PHP reads these files directly; clients must not receive their contents.
<FilesMatch "(?i)^(?:data|reservations|customers|costs|protocols|messages|stats|terms|protocol_devices|online_sessions|rental_addons|product_stats|messages_archive)\.json(?:[.~].*)?$">
  Require all denied
</FilesMatch>
# PHP data wrappers and accidental copies must also stay private.
<FilesMatch "(?i)^(?:rental_assets|rental_equipment_names|phone_events_data)\.php(?:[.~].*)?$">
  Require all denied
</FilesMatch>
# In-flight writes and sidecar locks may contain private data as well.
<FilesMatch "(?i)\.(?:lock|tmp)$">
  Require all denied
</FilesMatch>
# END RentMor private-data access v2

'''

def patch(source: str) -> str:
    pattern = r'# BEGIN RentMor private-data access v[12]\n.*?# END RentMor private-data access v[12]\n(?:\n)?'
    matches = list(re.finditer(pattern, source, re.S))
    if '# BEGIN RentMor private-data access' in source or '# END RentMor private-data access' in source:
        if len(matches) != 1 or source.count('# BEGIN RentMor private-data access') != 1 or source.count('# END RentMor private-data access') != 1:
            raise ValueError('Incomplete or repeated private-data rule')
        match = matches[0]
        return source[:match.start()] + RULES + source[match.end():]
    return RULES + source

if __name__ == '__main__':
    file = Path(sys.argv[1])
    file.write_text(patch(file.read_text()))
    print('Private JSON, locks and temporary files protected in Apache; other rules preserved.')
