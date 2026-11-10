<?php
declare(strict_types=1);
// All writes use a disposable copy, never real credentials, inventory or customers.
$root=sys_get_temp_dir().'/rentmor-gsc-test-'.bin2hex(random_bytes(10));
mkdir($root,0700); mkdir($root.'/server',0700);
foreach(['search_console.php','search_console_admin.php'] as $file) copy(__DIR__.'/../server/'.$file,$root.'/server/'.$file);
require $root.'/server/search_console_admin.php';
$checks=0;
function check(bool $condition,string $name):void { global $checks; ++$checks; if(!$condition)throw new RuntimeException('FAIL '.$name); }
function fails(callable $fn,string $name):void { try{$fn();}catch(Throwable $e){check(true,$name);return;}check(false,$name); }
function clean(string $path):void { foreach(scandir($path) as $f){if($f==='.'||$f==='..')continue;$p=$path.'/'.$f;if(is_dir($p)&&!is_link($p))clean($p);else unlink($p);}rmdir($path); }
try {
    $ranges=rm_gsc_periods(28,new DateTimeImmutable('2026-11-10 12:00:00',new DateTimeZone('America/Los_Angeles')));
    check($ranges===['current'=>['2026-10-11','2026-11-07'],'previous'=>['2026-09-13','2026-10-10']],'equal inclusive adjacent ranges');
    fails(fn()=>rm_gsc_periods(29),'reject arbitrary range');
    check(rm_gsc_days([])===28 && rm_gsc_days('90')===90,'safe range input');
    $products=[['id'=>'1','name'=>'Żyrafa do gładzi'],['id'=>'2','name'=>'Agregat malarski']];
    check(rm_gsc_equipment('https://rentmor.pl/zyrafa-do-gladzi/',$products)==='1','current URL');
    check(rm_gsc_equipment('http://www.rentmor.pl/index.php?id=1',$products)==='1','legacy URL');
    check(rm_gsc_equipment('https://rentmor.pl/?id=2',$products)==='2','root legacy URL');
    check(rm_gsc_equipment('https://evil.example/zyrafa-do-gladzi/',$products)===null,'foreign host');
    check(rm_gsc_equipment('https://rentmor.pl/index.php?id[]=1',$products)===null,'array query injection');
    check(rm_gsc_equipment('https://rentmor.pl/zyrafa-do-gladzi/',[...$products,['id'=>'3','name'=>'Żyrafa do gładzi']])===null,'ambiguous slug not attributed');
    $rows=[['keys'=>['https://rentmor.pl/zyrafa-do-gladzi/'],'clicks'=>2,'impressions'=>10,'position'=>4],['keys'=>['https://rentmor.pl/index.php?id=1'],'clicks'=>1,'impressions'=>30,'position'=>8]];
    $a=rm_gsc_group($rows,$products)['1'];
    check($a===['clicks'=>3.0,'impressions'=>40.0,'ctr'=>7.5,'position'=>7.0],'merge aliases and weighted position');
    check(rm_gsc_summary([])['position']===null,'zero impressions is no position');
    $s=[];$client=['client_id'=>'1234567890.apps.googleusercontent.com','client_secret'=>'example-test-secret'];
    $url=rm_gsc_authorize($client,$s,1000);parse_str((string)parse_url($url,PHP_URL_QUERY),$q);
    check($q['scope']===RM_GSC_SCOPE && $q['redirect_uri']===RM_GSC_CALLBACK,'fixed readonly scope and callback');
    check($q['code_challenge_method']==='S256' && !str_contains($url,'example-test-secret'),'PKCE and no secret in authorize URL');
    $verifier=rm_gsc_consume_state($s,$q['state'],1100);check(strlen($verifier)===64,'valid callback');
    fails(function() use (&$s,$q){rm_gsc_consume_state($s,$q['state'],1100);},'reject replay');
    rm_gsc_authorize($client,$s,1000);fails(function() use (&$s){rm_gsc_consume_state($s,'wrong',1100);},'reject wrong state');
    rm_gsc_authorize($client,$s,1000);$state=$s['gsc_oauth']['state'];fails(function()use(&$s,$state){rm_gsc_consume_state($s,$state,1601);},'reject expired state');
    check(rm_gsc_validate_client($client['client_id'],$client['client_secret'])===$client,'validate OAuth config');
    fails(fn()=>rm_gsc_validate_client('foreign.example','example-test-secret'),'bad client rejected');
    fails(fn()=>rm_gsc_token_result(['access_token'=>'fake-token']),'require refresh token');
    check(rm_gsc_token_result(['access_token'=>'fake-token'],['refresh_token'=>'fake-refresh'])['refresh_token']==='fake-refresh','retain refresh token on refresh');
    fails(fn()=>rm_gsc_http('https://evil.example/',[]),'fixed endpoints prevent SSRF');
    rm_gsc_write('client.json',$client);check(rm_gsc_read('client.json')===$client,'private roundtrip');
    check((fileperms(rm_gsc_path('client.json'))&0777)===0600,'file mode0600');
    check((fileperms(rm_gsc_dir())&0777)===0700,'directory mode0700');
    fails(fn()=>rm_gsc_path('../token.json'),'path traversal');
    symlink(rm_gsc_path('client.json'),rm_gsc_dir().'/token.json');fails(fn()=>rm_gsc_read('token.json'),'symlink read');unlink(rm_gsc_dir().'/token.json');
    $calls=[];
    $request=static function(array $body)use(&$calls):array{
        $calls[]=$body;
        return ['rows'=>[['keys'=>count($body['dimensions'])===1?['https://rentmor.pl/zyrafa-do-gladzi/']:['https://rentmor.pl/zyrafa-do-gladzi/','<script>alert(1)</script>'],
            'clicks'=>1,'impressions'=>10,'position'=>5]]];
    };
    $report=rm_gsc_build_report(28,$request);
    check(count($calls)===4,'independent page and query calls for both periods');
    check($calls[0]['dataState']==='final' && $calls[0]['type']==='web','final organic web only');
    fails(fn()=>rm_gsc_query_rows(fn($b)=>['rows'=>[['keys'=>['x'],'clicks'=>20,'impressions'=>10,'position'=>3]]],['a','b'],['page']),'reject inconsistent metrics');
    $page=0;$batch=array_fill(0,25000,['keys'=>['https://rentmor.pl/'],'clicks'=>0,'impressions'=>1,'position'=>1]);
    $result=rm_gsc_query_rows(static function($b)use(&$page,$batch){++$page;return ['rows'=>$batch];},['a','b'],['page']);
    check($page===4 && $result['truncated'] && count($result['rows'])===100000,'bounded pagination with explicit partial flag');unset($batch,$result);
    rm_gsc_write('cache-28.json',$report);$_GET=['days'=>'28','equipment'=>'1'];$_SESSION=['gsc_csrf'=>'fake-csrf'];
    ob_start();rm_gsc_admin_render(true,$products);$html=ob_get_clean();
    check(!str_contains($html,'<script>alert(1)</script>') && str_contains($html,'&lt;script&gt;'),'escape search query');
    check(!str_contains($html,'example-test-secret'),'never render saved secret');
    check(str_contains($html,'Google ukrywa część zapytań'),'explain missing queries');
    ob_start();rm_gsc_admin_render(false,$products);$denied=ob_get_clean();check($denied===''&&http_response_code()===403,'unauthorized renderer denied');
    rm_gsc_write('token.json',['access_token'=>'fake-token']);rm_gsc_clear_connection();
    check(rm_gsc_read('token.json')===null && rm_gsc_read('cache-28.json')===null && rm_gsc_read('client.json')===$client,'disconnect clears tokens/cache only');
    echo 'PASS: '.$checks." Search Console checks, isolated data only\n";
} finally {clean($root);}
