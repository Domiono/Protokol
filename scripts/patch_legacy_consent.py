"""Gate legacy on-page click tracker and PHP session before analytics consent.

Source is the current hosting-owned index.php, never an obsolete repo snapshot.
Fail closed if its structure no longer matches the read-only live audit.
"""
from pathlib import Path
import re
import sys

MARKER = '/* rentmor-legacy-analytics-consent-v1 */'
SESSION = '/* rentmor-session-consent-v1 */'
CONSENT = "(document.cookie.split('; ').indexOf('rm_stats_consent=allow') !== -1)"

def patch(source: str) -> str:
    if MARKER in source or SESSION in source:
        if source.count(MARKER) != 1 or source.count(SESSION) != 1:
            raise ValueError('Incomplete analytics consent patch')
        if source.count(CONSENT) != 1:
            raise ValueError('Consent guard corrupted')
        return source
    if source.count('session_start()') != 1 or len(re.findall(r'\$_SESSION\s*\[', source)) != 0:
        raise ValueError('Live session usage changed; manual review required')
    matches = list(re.finditer(r'function\s+trackCall\s*\([^)]*\)\s*\{',source))
    if len(matches)!=1:
        raise ValueError('Legacy trackCall structure changed')
    fun=matches[0]
    block=source[fun.end():fun.end()+1600]
    if 'fetch(' not in block:
        raise ValueError('Expected legacy fetch-only click tracker')
    if any(token in block for token in ('location.', 'window.open', 'preventDefault(', 'return false', 'tel:')):
        raise ValueError('Tracker may affect phone calling; refusing unsafe change')
    # No change to customer links. The legacy function returns before sending
    # its statistical request, while a normal tel: href still navigates.
    guarded = source[:fun.end()] + '\n  ' + MARKER + '\n  if (!' + CONSENT + ') return;\n' + source[fun.end():]
    session = re.compile(r'\bsession_start\s*\(\s*\)\s*;')
    code = SESSION + "\nif (($_COOKIE['rm_stats_consent'] ?? '') === 'allow') session_start();"
    guarded,n = session.subn(lambda _:code, guarded)
    if n != 1:
        raise ValueError('Expected a single PHP session start')
    return guarded

if __name__ == '__main__':
    file=Path(sys.argv[1])
    file.write_text(patch(file.read_text(encoding='utf-8')),encoding='utf-8')
    print('Legacy stats and PHP session consent enforced.')
