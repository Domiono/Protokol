"""Limit only homepage previews to one image; retain product detail galleries."""
from pathlib import Path
import sys
MARKER = '// rentmor-static-cards-v1'
OLD = '$product_gallery_images = array_values(array_unique($product_gallery_images));'
NEW = OLD + '\n        ' + MARKER + '\n        $product_gallery_images = array_slice($product_gallery_images, 0, 1);'
CARD = 'class="rm-gallery rm-card-gallery" data-rm-gallery data-rm-autoplay="true"'
STATIC = 'class="rm-gallery rm-card-gallery" data-rm-static="1"'
def patch(source):
    if MARKER in source:
        if source.count(NEW)!=1 or source.count(STATIC)!=1:
            raise ValueError('Incomplete static-card migration')
        return source
    if source.count(OLD)!=1 or source.count(CARD)!=1 or 'rm-detail-gallery' not in source:
        raise ValueError('Unknown gallery template: no changes saved')
    return source.replace(OLD,NEW,1).replace(CARD,STATIC,1)
if __name__=='__main__':
    p=Path(sys.argv[1]);p.write_text(patch(p.read_text()),encoding='utf-8')
