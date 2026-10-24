#!/usr/bin/env python3
"""Regression tests for counter filtering and guarded live-source migration."""
from pathlib import Path
import subprocess
import sys
import tempfile
from patch_statistics_filter import patch, GLOBAL, PRODUCT, SAVE

root = Path(__file__).resolve().parents[1]
fixture = "<?php\n" + GLOBAL + "\n// routing stays here\n" + PRODUCT + "\n" + SAVE
result = patch(fixture)
assert patch(result) == result
assert result.index('// routing stays here') < result.index('rm_stats_count_pageview()')
for bad in (fixture.replace(SAVE, ''), fixture + SAVE, fixture.replace("$stats['days'][$today]++;", '/* changed */')):
    try:
        patch(bad)
    except ValueError:
        pass
    else:
        raise AssertionError('Unknown counter structure was accepted')
print('Migration: idempotent, preserves routing, rejects unknown source.')
if '--patch-only' in sys.argv:
    sys.exit(0)

php = r'''<?php
require $argv[1];
function check($ok, $label) { if (!$ok) throw new RuntimeException($label); }
function reset_request() {
    $_SERVER = ['REQUEST_METHOD'=>'GET', 'HTTP_USER_AGENT'=>'Mozilla/5.0 Chrome/130.0 Safari/537.36', 'REQUEST_URI'=>'/zyrafa/', 'HTTP_SEC_FETCH_DEST'=>'document'];
    $_SESSION = []; $_COOKIE = [];
}
reset_request(); check(rm_stats_count_pageview(), 'visitor first page');
check(!rm_stats_count_pageview(), 'immediate reload');
$_SERVER['REQUEST_URI']='/puzzi/'; check(rm_stats_count_pageview(), 'second offer');
$_SESSION['rm_stats_recent'] = array_map(fn($at) => time()-31, $_SESSION['rm_stats_recent']);
check(rm_stats_count_pageview(), 'return after reload window');
foreach (['Googlebot','bingbot','curl/8.0','python-requests/2','facebookexternalhit','HeadlessChrome','', 'GPTBot'] as $ua) {
    reset_request(); $_SERVER['HTTP_USER_AGENT']=$ua;
    check(!rm_stats_count_pageview(), 'bot: '.$ua);
    check(empty($_SESSION['rm_stats_recent']), 'bot leaves session counter untouched');
}
reset_request(); $_SESSION['logged_in']=true; check(!rm_stats_count_pageview(), 'admin');
reset_request(); $_COOKIE['rm_stats_exclude']='1'; check(!rm_stats_count_pageview(), 'owner after logout');
reset_request(); $_COOKIE['rm_protocol_device']='test-device'; check(!rm_stats_count_pageview(), 'protocol device');
foreach (['POST','HEAD','OPTIONS'] as $method) {
    reset_request(); $_SERVER['REQUEST_METHOD']=$method; check(!rm_stats_count_pageview(), $method);
}
reset_request(); $_SERVER['HTTP_SEC_PURPOSE']='prefetch'; check(!rm_stats_count_pageview(), 'prefetch');
reset_request(); $_SERVER['HTTP_X_REQUESTED_WITH']='XMLHttpRequest'; check(!rm_stats_count_pageview(), 'ajax refresh');
reset_request(); $_SERVER['HTTP_SEC_FETCH_DEST']='empty'; check(!rm_stats_count_pageview(), 'fetch');
reset_request(); unset($_SERVER['HTTP_SEC_FETCH_DEST']); check(rm_stats_count_pageview(), 'older browser');
echo "Traffic filter regression tests passed.\n";
'''
with tempfile.TemporaryDirectory() as tmp:
    p=Path(tmp)/'test.php'; p.write_text(php)
    subprocess.run(['php', str(p), str(root/'server/traffic_filter.php')], check=True)
    # Exercise the actual migrated increments and save, including preserved old totals.
    (Path(tmp)/'traffic_filter.php').write_text((root/'server/traffic_filter.php').read_text())
    source = '''<?php
$_SERVER=['REQUEST_METHOD'=>'GET','HTTP_USER_AGENT'=>$argv[2], 'REQUEST_URI'=>'/zyrafa/'];
$_SESSION=[]; $_COOKIE=[];
$stats_file='unused'; $today='2026-10-24';
$stats=['total_views'=>100,'unique_visitors'=>50,'days'=>[$today=>10],'product_views'=>['x'=>5],'total_phone_clicks'=>9];
function get_traffic_source() { return 'direct'; }
function save_json_file($file,$stats) { echo json_encode($stats); }
''' + GLOBAL + "\n$detail_view=true; $product=['id'=>'x'];\n" + PRODUCT + '\n' + SAVE
    p.write_text(patch(source))
    import json
    run = lambda ua: subprocess.check_output(['php', str(p), str(root/'server/traffic_filter.php'), ua], text=True)
    stats = json.loads(run('Mozilla/5.0 Chrome/130.0'))
    assert stats['total_views']==101 and stats['days']['2026-10-24']==11
    assert stats['unique_visitors']==51 and stats['product_views']['x']==6
    assert stats['total_phone_clicks']==9 and stats['sources']['2026-10-24']['direct']==1
    assert run('Googlebot') == ''
    p.write_text(patch(source).replace('$detail_view=true;', 'http_response_code(404); $detail_view=true;'))
    assert run('Mozilla/5.0') == ''
print('Migrated counter: visitor increments once; bot and 404 never save; historical totals preserved.')
