from patch_seo_structure import patch_index, patch_rules, HOOK, RULES
from patch_canonical_host import RULES as HOST
source = '<?php $x = 1; ?>\n<!doctype html><html><head></head><body>KEEP</body></html>'
result = patch_index(source)
assert result.replace(HOOK, '') == source
assert patch_index(result) == result
rules = HOST + 'RewriteRule ^([a-z-]+)/$ index.php?slug=$1 [L,QSA]\n'
updated = patch_rules(rules)
assert updated.replace('\n\n'+RULES,'') == rules
assert patch_rules(updated) == updated
assert updated.index('canonical host v1') < updated.index('SEO structure v1') < updated.index('index.php?slug=')
for text in ('', '<html></html>', '<?php ?> <!doctype html><!doctype html>'):
    try: patch_index(text)
    except ValueError: pass
    else: raise AssertionError('Unknown template accepted')
print('PASS: non-destructive hooks, rule ordering, idempotence and unknown-template refusal')
