#!/usr/bin/env python3
"""Apply combined filters and accessible empty-state controls to live index.php."""
from pathlib import Path
import re
import sys

MARKER = '// RentMor combined catalogue filters v3'
OLD_MARKERS = ('// RentMor combined catalogue filters v1', '// RentMor combined catalogue filters v2')
JS = '''        // RentMor combined catalogue filters v3
        let selectedCategory = 'all';
        // Shuffle once per page load; filtering only changes visibility.
        function rmShuffleCatalogue() {
            if (!document.getElementById('searchInput')) return;
            const cards = Array.from(document.querySelectorAll('.product-item'));
            if (cards.length < 2) return;
            const grid = cards[0].parentNode;
            if (!grid || cards.some(card => card.parentNode !== grid)) return;
            for (let i = cards.length - 1; i > 0; i--) {
                const j = Math.floor(Math.random() * (i + 1));
                [cards[i], cards[j]] = [cards[j], cards[i]];
            }
            cards.forEach(card => grid.appendChild(card));
        }
        rmShuffleCatalogue();
        function searchProducts() {
            const input = document.getElementById('searchInput');
            filterItems(input.value.toLowerCase().trim(), selectedCategory);
            rmRefreshEmptyCatalogue();
        }
        function filterProducts(cat, btn) {
            selectedCategory = cat;
            document.querySelectorAll('.filter-btn').forEach(function (b) {
                b.classList.toggle('filter-active', b === btn);
                b.setAttribute('aria-pressed', b === btn ? 'true' : 'false');
            });
            searchProducts();
        }
        function clearSearchFilters() {
            document.getElementById('searchInput').value = '';
            const buttons = Array.from(document.querySelectorAll('.filter-btn'));
            const all = buttons.find(b => /filterProducts\(['"]all['"]/.test(b.getAttribute('onclick') || '')) || buttons[0];
            selectedCategory = 'all';
            buttons.forEach(b => {
                const active = b === all;
                b.classList.toggle('filter-active', active);
                b.setAttribute('aria-pressed', active ? 'true' : 'false');
            });
            searchProducts();
            document.getElementById('searchInput').focus();
        }
        function rmRefreshEmptyCatalogue() {
            const selectors = ['.product-card', '.product-item', '[data-product-id]'];
            let cards = [];
            for (const selector of selectors) {
                cards = Array.from(document.querySelectorAll(selector));
                if (cards.length) break;
            }
            // Never show an incorrect empty message if the live card markup changes.
            if (!cards.length) return;
            let empty = document.getElementById('rentmor-empty-catalogue');
            if (!empty) {
                empty = document.createElement('div');
                empty.id = 'rentmor-empty-catalogue';
                empty.setAttribute('role', 'status');
                empty.setAttribute('aria-live', 'polite');
                empty.style.gridColumn = '1 / -1';
                empty.style.textAlign = 'center';
                empty.style.padding = '28px 16px';
                empty.style.borderRadius = '18px';
                empty.style.background = '#f8fafc';
                empty.innerHTML = '<p style="font-weight:800;margin-bottom:12px">Brak wyników</p><p style="margin-bottom:16px">Nie znaleziono sprzętu pasującego do filtrów.</p><button type="button" style="background:#facc15;color:#111827;border-radius:12px;padding:12px 20px;font-weight:700">Wyczyść filtry</button>';
                cards[0].parentNode.appendChild(empty);
                empty.querySelector('button').addEventListener('click', clearSearchFilters);
            }
            const visible = cards.some(card =>
                !card.hidden && card.style.display !== 'none' &&
                !card.classList.contains('hidden')
            );
            empty.style.display = visible ? 'none' : 'block';
        }

'''

def patch(source):
    if MARKER in source:
        if source.count(MARKER) != 1 or source.count('function rmRefreshEmptyCatalogue()') != 1 or 'oninput="searchProducts()"' not in source:
            raise ValueError('Incomplete catalogue v2 patch')
        return source
    if any(marker in source for marker in OLD_MARKERS):
        OLD_MARKER = next(marker for marker in OLD_MARKERS if marker in source)
        if source.count(OLD_MARKER) != 1 or source.count('oninput="searchProducts()"') != 1:
            raise ValueError('Unexpected v1 catalogue structure')
        pattern = r'        // RentMor combined catalogue filters v[12].*?(?=        function filterItems\(search, cat\))'
        source, count = re.subn(pattern, lambda m: JS, source, flags=re.S)
    else:
        pattern = r'        function searchProducts\(\)\s*\{.*?(?=        function filterItems\(search, cat\))'
        source, count = re.subn(pattern, lambda m: JS, source, flags=re.S)
        if count == 1 and source.count('onkeyup="searchProducts()"') == 1:
            source = source.replace('onkeyup="searchProducts()"', 'oninput="searchProducts()"', 1)
        else:
            raise ValueError('Unknown catalogue structure; refusing to patch live index')
    if count != 1: raise ValueError('Unexpected catalogue structure; refusing to patch')
    return source

if __name__ == '__main__':
    path = Path(sys.argv[1])
    path.write_text(patch(path.read_text(encoding='utf-8')), encoding='utf-8')
    print('Catalogue filters and empty state verified.')
