"""Insert visible privacy notice next to the existing public contact form.

The current homepage is downloaded from hosting; require one form and one close tag.
Never replace other content.
"""
from pathlib import Path
import re
import sys

MARKER = '<!-- rentmor-contact-privacy-v1 -->'
LINK = (MARKER + '\n<p class="text-sm text-gray-600 mt-3">Administratorem danych z formularza jest '
        'Dominik Morawski (RentMor.pl). Dane służą do odpowiedzi na zapytanie. '
        '<a href="/polityka-prywatnosci.php" class="underline font-semibold">Polityka prywatności</a>.</p>')

def patch(source):
    if MARKER in source:
        if source.count(MARKER)!=1 or source.count(LINK)!=1:
            raise ValueError('Damaged contact privacy link')
        return source
    forms = re.findall(r'<form\b',source,re.I)
    closes = list(re.finditer(r'</form\s*>',source,re.I))
    if len(forms)!=1 or len(closes)!=1:
        raise ValueError('Expected one public contact form')
    at=closes[0].start()
    return source[:at]+LINK+'\n'+source[at:]

if __name__=='__main__':
    file=Path(sys.argv[1]);file.write_text(patch(file.read_text(encoding='utf-8')),encoding='utf-8')
