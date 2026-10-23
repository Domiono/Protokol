#!/usr/bin/env python3
"""Insert a guide card into the current live index, preserving every existing byte."""
from pathlib import Path
import re
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding='utf-8')
marker = '<!-- rentmor-guide-link-v1 -->'
if marker in text:
    print('Guide card already present; no changes.')
    raise SystemExit(0)
if '<?php' not in text or 'stats.json' not in text:
    raise SystemExit('Unexpected live index; refusing to patch.')
matches = list(re.finditer(r'<footer\b', text, re.I))
if len(matches) != 1:
    raise SystemExit('Expected one footer; refusing ambiguous insertion.')
card = '''<!-- rentmor-guide-link-v1 -->
<section id="poradniki" aria-labelledby="rentmor-guides-title" class="max-w-7xl mx-auto w-full px-4 py-8">
  <div class="bg-white border border-gray-200 rounded-2xl p-6">
    <h2 id="rentmor-guides-title" class="text-xl font-bold text-gray-900 mb-3">Poradniki</h2>
    <a href="/poradnik-jaki-papier-scierny-do-zyrafy.php" class="font-bold text-gray-900 underline">Jaki papier ścierny do żyrafy? Jak dobrać gradację do gładzi</a>
    <p class="text-gray-600 mt-2">Sprawdź tabelę gradacji, dobór papieru i najczęstsze błędy przy szlifowaniu ścian.</p>
  </div>
</section>
'''
offset = matches[0].start()
path.write_text(text[:offset] + card + text[offset:], encoding='utf-8')
print('Guide card inserted before the footer in the current live index.')
