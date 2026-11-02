"""Patch the current hosting template/rules; preserve unrelated bytes and legacy URLs."""
from pathlib import Path
import re
import sys

MARKER = '/* rentmor-seo-structure-hook-v1 */'
HOOK = "<?php " + MARKER + " require_once __DIR__ . '/seo_structure.php'; ob_start('rentmor_seo_structure'); ?>\n"
RULE_MARKER = '# BEGIN RentMor SEO structure v1'
RULES = r'''# BEGIN RentMor SEO structure v1
<IfModule mod_rewrite.c>
RewriteEngine On
# Only direct, query-free reads; preserve legacy ?id=, tracking and POST semantics.
RewriteCond %{REQUEST_METHOD} ^(?:GET|HEAD)$
RewriteCond %{THE_REQUEST} \s/+index\.php[\s?] [NC]
RewriteCond %{QUERY_STRING} ^$
RewriteRule ^index\.php$ https://rentmor.pl/ [R=301,L]
# Explicit category routes take precedence over the generic product slug router.
RewriteCond %{REQUEST_METHOD} ^(?:GET|HEAD)$
RewriteRule ^(sprzet-budowlany|sprzet-ogrodniczy|sprzet-czyszczacy)$ https://rentmor.pl/$1/ [R=301,L]
RewriteRule ^(?:sprzet-budowlany|sprzet-ogrodniczy|sprzet-czyszczacy)/$ category.php [END]
RewriteRule ^sitemap\.xml$ category_sitemap.php [END]
</IfModule>
# END RentMor SEO structure v1

'''

def patch_index(text):
    if MARKER in text:
        if text.count(MARKER) != 1 or HOOK not in text: raise ValueError('Invalid SEO hook')
        return text
    matches = list(re.finditer(r'<!doctype\s+html\s*>', text, re.I))
    if len(matches) != 1 or '<?php' not in text: raise ValueError('Unknown PHP template')
    pos = matches[0].start()
    return text[:pos] + HOOK + text[pos:]

def patch_rules(text):
    if RULE_MARKER in text:
        if text.count(RULE_MARKER) != 1 or RULES not in text: raise ValueError('Invalid SEO rules')
        return text
    # Place after canonical-host rules to retain www/HTTPS normalization first.
    marker = '# END RentMor canonical host v1'
    if text.count(marker) != 1: raise ValueError('Expected canonical host rules')
    pos = text.index(marker) + len(marker)
    return text[:pos] + '\n\n' + RULES + text[pos:]

if __name__ == '__main__':
    path = Path(sys.argv[1]); source = path.read_text()
    result = patch_rules(source) if path.name.endswith('htaccess') else patch_index(source)
    path.write_text(result)
