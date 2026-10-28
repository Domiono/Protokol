#!/usr/bin/env python3
"""Repair combined catalogue filters in the live PHP template, idempotently."""
from pathlib import Path
import re
import sys

MARKER = '// RentMor combined catalogue filters v1'
JS = '''        // RentMor combined catalogue filters v1
        let selectedCategory = 'all';
        function searchProducts() {
            const input = document.getElementById('searchInput');
            filterItems(input.value.toLowerCase().trim(), selectedCategory);
        }
        function filterProducts(cat, btn) {
            selectedCategory = cat;
            document.querySelectorAll('.filter-btn').forEach(function (b) {
                b.classList.toggle('filter-active', b === btn);
                b.setAttribute('aria-pressed', b === btn ? 'true' : 'false');
            });
            searchProducts();
        }

'''

def patch(source):
    if MARKER in source:
        if "oninput=\"searchProducts()\"" not in source or source.count(MARKER) != 1:
            raise ValueError('Unexpected patched catalogue structure')
        return source
    pattern = r'        function searchProducts\(\)\s*\{.*?(?=        function filterItems\(search, cat\))'
    source, count = re.subn(pattern, lambda m: JS, source, flags=re.S)
    if count != 1 or source.count('onkeyup="searchProducts()"') != 1:
        raise ValueError('Unknown catalogue structure; refusing to change live template')
    return source.replace('onkeyup="searchProducts()"', 'oninput="searchProducts()"', 1)

if __name__ == '__main__':
    path = Path(sys.argv[1])
    path.write_text(patch(path.read_text()), encoding='utf-8')
    print('Combined catalogue filters verified.')
