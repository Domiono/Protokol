from patch_font_speed import patch
m={'css':'/assets/rentmor-type-000000000000.css','logo':'/assets/rentmor-wordmark-000000000000.webp'}
s='''<html><head><link href="https://fonts.googleapis.com/css2?family=Roboto&amp;display=swap"><link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css"><?php if (empty($detail_view)): ?><link rel="preload" as="image" href="/assets/hero.webp"><?php endif; ?></head><body><img src="/rentmor-logo.png?v=old" srcset="/rentmor-logo.png?v=old 1x"><a href="tel:123" onclick="trackCall('top_nav')">Call</a></body></html>'''
u=patch(s,m);assert patch(u,m)==u
assert u.index('rel="preload"')<u.index(m['css'])
assert "trackCall('top_nav')" in u
assert 'fonts.googleapis.com/css' not in u and 'cdnjs.cloudflare.com/ajax/libs/font-awesome' not in u
assert u.count(m['logo'])==2
print('PASS: PHP preload order, idempotency, logo srcset and contact action')
