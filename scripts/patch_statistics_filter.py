#!/usr/bin/env python3
"""Patch the freshly downloaded live counter, refusing unknown structures."""
from pathlib import Path
import sys

GLOBAL = "$stats['total_views'] =\n    (int)($stats['total_views'] ?? 0) + 1;\n\nif (!isset($stats['days'][$today])) {\n    $stats['days'][$today] = 0;\n}\n\n$stats['days'][$today]++;\n\n/*\n * Unikalny odwiedzający:\n * sesja zamiast każdego odświeżenia strony.\n */\n\nif (!isset($_SESSION['v_rm'])) {\n\n    $stats['unique_visitors'] =\n        (int)($stats['unique_visitors'] ?? 0) + 1;\n\n    $_SESSION['v_rm'] = true;\n\n    $source = get_traffic_source();\n\n    if (!isset($stats['sources'][$today])) {\n        $stats['sources'][$today] = [\n            'google_bing_search' => 0,\n            'social' => 0,\n            'olx' => 0,\n            'direct' => 0,\n            'other_ext' => 0,\n            'internal' => 0\n        ];\n    }\n\n    if (!isset($stats['sources'][$today][$source])) {\n        $stats['sources'][$today][$source] = 0;\n    }\n\n    $stats['sources'][$today][$source]++;\n}"
PRODUCT = "        if (!isset($stats['product_views'])) {\n            $stats['product_views'] = [];\n        }\n        if (!isset($stats['product_views'][$product['id']])) {\n            $stats['product_views'][$product['id']] = 0;\n        }\n        $stats['product_views'][$product['id']]++;"
SAVE = 'save_json_file($stats_file, $stats);'
MARKER = '// RentMor filtered statistics v1'

def patch(source):
    if MARKER in source:
        return source
    for block in (GLOBAL, PRODUCT, SAVE):
        if source.count(block) != 1:
            raise ValueError('Unexpected live counter structure; no files changed.')
    source = source.replace(GLOBAL, '', 1).replace(PRODUCT, '', 1)
    replacement = (MARKER + "\nrequire_once __DIR__ . '/traffic_filter.php';\n"
        + "if (http_response_code() !== 404 && rm_stats_count_pageview()) {\n"
        + GLOBAL + "\n    if ($detail_view && $product) {\n" + PRODUCT
        + "\n    }\n    " + SAVE + "\n}\n")
    return source.replace(SAVE, replacement, 1)

if __name__ == '__main__':
    path = Path(sys.argv[1])
    source = path.read_text(encoding='utf-8')
    updated = patch(source)
    path.write_text(updated, encoding='utf-8')
    print('Statistics filter verified in prepared homepage.')
