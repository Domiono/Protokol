#!/usr/bin/env python3
"""Patch only product photo markup in the *currently downloaded* RentMor homepage.

No inventory edits, no rewrites from an outdated stored index.php.
Fail closed if the page template differs from what was audited.
"""
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: patch_gallery.py /path/to/live/index.php")
path = Path(sys.argv[1])
source = path.read_text(encoding="utf-8")
if not all(x in source for x in (
    "<?php",
    "product_url($p)",
    "SZCZEGÓŁY PRODUKTU",
    "product['images']",
    'id="kontakt"',
)):
    raise SystemExit("Unexpected RentMor product template. No changes were saved.")

if 'data-rm-gallery' in source:
    if ('/rentmor-gallery.css?v=1' not in source or
        not any(version in source for version in ('/rentmor-gallery.js?v=1', '/rentmor-gallery.js?v=2')) or
        'rm-detail-gallery' not in source):
        raise SystemExit("Incomplete previous gallery integration; refusing to apply a second patch.")
    print("RentMor galleries already installed; no changes.")
    sys.exit(0)

def replace_unique(old, new, title):
    global source
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one {title}, got {count}. No changes saved.")
    source = source.replace(old, new, 1)

old_image = "        $product_image = $p['images'][0] ?? '';"
new_image = """        $product_image = $p['images'][0] ?? '';
        $product_gallery_images = [];
        foreach ((array)($p['images'] ?? []) as $filename) {
            $filename = basename(trim((string)$filename));
            if ($filename !== '' && $filename !== '.' && $filename !== '..') {
                $product_gallery_images[] = $filename;
            }
        }
        $product_gallery_images = array_values(array_unique($product_gallery_images));"""
replace_unique(old_image, new_image, "card images field")

card_start = '''            <a
                href="<?php echo e(product_url($p)); ?>"
                class="h-64 flex items-center justify-center p-6 bg-white group"
            >'''
if source.count(card_start) != 1:
    raise SystemExit("Unexpected homepage product preview markup. No changes saved.")
start = source.index(card_start)
end = source.index("            </a>", start) + len("            </a>")
old_card = source[start:end]
new_card = '''            <div class="rm-gallery rm-card-gallery" data-rm-gallery data-rm-autoplay="true"
                 aria-label="Zdjęcia: <?php echo e($product_name); ?>">
                <a href="<?php echo e(product_url($p)); ?>" class="rm-gallery-stage block"
                   aria-label="Otwórz <?php echo e($product_name); ?>">
                    <?php if ($product_gallery_images): ?>
                        <?php foreach ($product_gallery_images as $photoIndex => $filename): ?>
                            <div class="rm-gallery-slide <?php echo $photoIndex === 0 ? 'rm-active' : ''; ?>"
                                 data-rm-slide aria-hidden="<?php echo $photoIndex === 0 ? 'false' : 'true'; ?>">
                                <img src="/uploads/<?php echo e(rawurlencode($filename)); ?>"
                                     alt="<?php echo e($product_name); ?> – zdjęcie <?php echo $photoIndex + 1; ?>"
                                     loading="lazy" decoding="async">
                            </div>
                        <?php endforeach; ?>
                    <?php else: ?>
                        <div class="rm-gallery-slide rm-active" data-rm-slide aria-hidden="false">
                            <span class="text-gray-400 text-sm font-bold">Zdjęcie w przygotowaniu</span>
                        </div>
                    <?php endif; ?>
                </a>
                <?php if (count($product_gallery_images) > 1): ?>
                    <span class="rm-gallery-counter"><span data-rm-current>1</span> / <?php echo count($product_gallery_images); ?></span>
                    <button type="button" class="rm-gallery-nav rm-gallery-prev" data-rm-prev aria-label="Poprzednie zdjęcie">‹</button>
                    <button type="button" class="rm-gallery-nav rm-gallery-next" data-rm-next aria-label="Następne zdjęcie">›</button>
                    <div class="rm-gallery-dots" aria-label="Wybierz zdjęcie">
                        <?php foreach ($product_gallery_images as $photoIndex => $filename): ?>
                            <button type="button" class="rm-gallery-dot <?php echo $photoIndex === 0 ? 'rm-active' : ''; ?>"
                                    data-rm-dot aria-label="Zdjęcie <?php echo $photoIndex + 1; ?>"></button>
                        <?php endforeach; ?>
                    </div>
                <?php endif; ?>
            </div>'''
source = source[:start] + new_card + source[end:]

detail_start = '            <div class="bg-white p-6 flex flex-col items-center border-b border-gray-50">'
if source.count(detail_start) != 1:
    raise SystemExit("Unexpected product detail image section. No changes saved.")
start = source.index(detail_start)
tail = source[start:]
match = re.search(r'\n\s{12}<div class="p-(?:4 sm:p-8|8)">', tail)
if not match:
    raise SystemExit("Cannot determine end of product detail gallery. No changes saved.")
end = start + match.start()
detail_old = source[start:end]
if 'id="mainProductImage"' not in detail_old or "<?php foreach ($product['images'] as $img): ?>" not in detail_old:
    raise SystemExit("Unexpected current product thumbnails. No changes saved.")

new_detail = '''            <div class="bg-white p-4 sm:p-6 flex flex-col items-center border-b border-gray-50">
                <?php
                    $detail_gallery_images = [];
                    foreach ((array)($product['images'] ?? []) as $filename) {
                        $filename = basename(trim((string)$filename));
                        if ($filename !== '' && $filename !== '.' && $filename !== '..') {
                            $detail_gallery_images[] = $filename;
                        }
                    }
                    $detail_gallery_images = array_values(array_unique($detail_gallery_images));
                ?>
                <?php if ($detail_gallery_images): ?>
                    <div class="rm-gallery rm-detail-gallery" data-rm-gallery data-rm-autoplay="true" tabindex="0"
                         aria-label="Galeria zdjęć: <?php echo e($product['name']); ?>">
                        <div class="rm-gallery-stage">
                            <?php foreach ($detail_gallery_images as $photoIndex => $filename): ?>
                                <div class="rm-gallery-slide <?php echo $photoIndex === 0 ? 'rm-active' : ''; ?>"
                                     data-rm-slide aria-hidden="<?php echo $photoIndex === 0 ? 'false' : 'true'; ?>">
                                    <img src="/uploads/<?php echo e(rawurlencode($filename)); ?>"
                                         alt="<?php echo e($product['name']); ?> – zdjęcie <?php echo $photoIndex + 1; ?>"
                                         decoding="async" <?php echo $photoIndex === 0 ? 'fetchpriority="high"' : 'loading="lazy"'; ?>>
                                </div>
                            <?php endforeach; ?>
                        </div>
                        <button type="button" class="rm-gallery-fullscreen" data-rm-zoom aria-label="Powiększ zdjęcie">
                            <i class="fa-solid fa-expand mr-1" aria-hidden="true"></i> Powiększ
                        </button>
                        <?php if (count($detail_gallery_images) > 1): ?>
                            <span class="rm-gallery-counter"><span data-rm-current>1</span> / <?php echo count($detail_gallery_images); ?></span>
                            <button type="button" class="rm-gallery-nav rm-gallery-prev" data-rm-prev aria-label="Poprzednie zdjęcie">‹</button>
                            <button type="button" class="rm-gallery-nav rm-gallery-next" data-rm-next aria-label="Następne zdjęcie">›</button>
                            <div class="rm-gallery-dots" aria-label="Wybierz zdjęcie">
                                <?php foreach ($detail_gallery_images as $photoIndex => $filename): ?>
                                    <button type="button" class="rm-gallery-dot <?php echo $photoIndex === 0 ? 'rm-active' : ''; ?>"
                                            data-rm-dot aria-label="Zdjęcie <?php echo $photoIndex + 1; ?>"></button>
                                <?php endforeach; ?>
                            </div>
                        <?php endif; ?>
                    </div>
                    <?php if (count($detail_gallery_images) > 1): ?>
                        <div class="rm-gallery-thumbs" aria-label="Miniatury zdjęć sprzętu">
                            <?php foreach ($detail_gallery_images as $photoIndex => $filename): ?>
                                <button type="button" class="rm-gallery-thumb <?php echo $photoIndex === 0 ? 'rm-active' : ''; ?>"
                                        data-rm-thumb aria-label="Pokaż zdjęcie <?php echo $photoIndex + 1; ?>">
                                    <img src="/uploads/<?php echo e(rawurlencode($filename)); ?>"
                                         alt="" loading="lazy">
                                </button>
                            <?php endforeach; ?>
                        </div>
                    <?php endif; ?>
                <?php endif; ?>
            </div>
'''
source = source[:start] + new_detail + source[end:]

replace_unique("</head>", '    <link rel="stylesheet" href="/rentmor-gallery.css?v=1">\n</head>', "head closing tag")
replace_unique("</body>", '    <script src="/rentmor-gallery.js?v=1" defer></script>\n</body>', "body closing tag")
if source.count('data-rm-gallery') != 2:
    raise SystemExit("Gallery integration count mismatch. No changes saved.")
path.write_text(source, encoding="utf-8")
print("Patched current public homepage with home-card and detail photo galleries.")
