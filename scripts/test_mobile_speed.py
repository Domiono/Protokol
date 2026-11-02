from patch_mobile_speed import patch,cache,ASSET
s='''<?php echo "keep"; ?><html><head></head><body><a href="tel:733891281" onclick="trackCall('top_nav')"><i class="fa-solid fa-phone"></i><span class="hidden sm:inline">Zadzwoń</span></a><header data-rentmor-blueprint="v1"><h1>RentMor</h1></header></body></html>'''
p=patch(s)
assert patch(p)==p and ASSET in p
assert 'aria-label="Zadzwoń do RentMor"' in p and 'aria-hidden="true"' in p
assert '<?php echo "keep"; ?>' in p and "trackCall('top_nav')" in p
assert cache(cache('Require all denied'))==cache('Require all denied')
assert 'php' not in cache('') and 'json' not in cache('')
try:patch('<html></html>')
except ValueError:pass
else:raise AssertionError('Must reject unknown template')
print('PASS: idempotency, original actions, narrow cache and unknown-template rejection')
