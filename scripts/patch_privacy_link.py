"""Safe, idempotent footer link on the LIVE server-owned PHP homepage."""
import re
from pathlib import Path
import sys

MARKER = '<!-- rentmor-privacy-link-v1 -->'
LINK = MARKER + ' <span aria-hidden="true">·</span> <a href="/polityka-prywatnosci.php">Polityka prywatności</a>'

def patch(source: str) -> str:
    if MARKER in source:
        if source.count(MARKER) != 1 or source.count('href="/polityka-prywatnosci.php"') != 1:
            raise ValueError('Duplicate or damaged privacy link')
        return source
    if 'href="/polityka-prywatnosci.php"' in source:
        raise ValueError('Privacy link exists without a marker')
    endings = list(re.finditer(r'</footer\s*>', source, flags=re.I))
    if len(endings) != 1:
        raise ValueError('Expected exactly one footer in live template')
    at = endings[0].start()
    return source[:at] + LINK + '\n' + source[at:]

if __name__ == '__main__':
    target = Path(sys.argv[1])
    before = target.read_text(encoding='utf-8')
    after = patch(before)
    target.write_text(after, encoding='utf-8')
    print('RentMor privacy footer link verified.')
