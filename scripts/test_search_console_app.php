<?php
declare(strict_types=1);
require __DIR__.'/../server/search_console_app.php';
$count=0;
function ok(bool $value,string $name):void{global $count;++$count;if(!$value)throw new RuntimeException('FAIL '.$name);}
$calls=0;$reader=static function(string $path)use(&$calls):?array{++$calls;return null;};$refresh=static function(int $days)use(&$calls):void{++$calls;};
foreach(['assistant','', 'unknown']as $role){[$status,$body]=rm_gsc_app_request($role,'GET','',[],null,[],$reader,$refresh);ok($status===403,'roles denied');}
ok($calls===0,'no storage for unauthorized roles');
[$status]=rm_gsc_app_request('admin','POST','',[],['action'=>'refresh','days'=>28],[],$reader,$refresh);ok($status===403&&$calls===0,'write header required before IO');
foreach([[],['days'=>'../28'],['days'=>['28']]]as $query){
    if($query===[])continue;
    [$status]=rm_gsc_app_request('admin','GET','',$query,null,[],$reader,$refresh);ok($status===400,'invalid period');
}
[$status]=rm_gsc_app_request('admin','DELETE','',[],null,[],$reader,$refresh);ok($status===405,'method rejected');
[$status,$body]=rm_gsc_app_request('admin','GET','',['days'=>28],null,[],$reader,$refresh);
ok($status===200&&!$body['has_report']&&!$body['connected']&&$body['equipment']===[],'no report is not zero traffic');
$products=[['id'=>'1','name'=>'Żyrafa do gładzi'],['id'=>'2','name'=>'Agregat malarski']];
$row=static fn(string $url,int $clicks,int $impressions,int $position,?string $query=null):array=>[
    'keys'=>$query===null?[$url]:[$url,$query],'clicks'=>$clicks,'impressions'=>$impressions,'position'=>$position];
$report=['property'=>RM_GSC_PROPERTY,'days'=>28,'ranges'=>rm_gsc_periods(28),'fetched_at'=>gmdate('c'),
    'current'=>['pages'=>['rows'=>[$row('https://rentmor.pl/zyrafa-do-gladzi/',2,10,4),$row('https://rentmor.pl/index.php?id=1',1,30,8)],'truncated'=>false],
        'queries'=>['rows'=>[$row('https://rentmor.pl/index.php?id=1',1,5,2,'żyrafa wynajem')],'truncated'=>false]],
    'previous'=>['pages'=>['rows'=>[$row('https://rentmor.pl/zyrafa-do-gladzi/',1,20,8)],'truncated'=>false],
        'queries'=>['rows'=>[],'truncated'=>false]],'access_token'=>'must-never-leak','client_secret'=>'also-never-leak'];
$read=static fn(string $name):array=>str_starts_with($name,'cache-')?$report:['access_token'=>'must-never-leak','client_secret'=>'also-never-leak'];
[$status,$body]=rm_gsc_app_request('admin','GET','',['days'=>28],null,$products,$read,$refresh);
ok($status===200&&$body['has_report']&&$body['connected'],'shared cache report');
$item=$body['equipment'][0];ok($item['id']==='1'&&$item['current']['clicks']===3.0&&$item['current']['position']===7.0,'merge legacy URLs and weighted position');
ok($item['current']['impressions']===40.0&&$item['queries'][0]['current']['impressions']===5.0,'query privacy does not reduce page totals');
ok(!str_contains(json_encode($body),'must-never-leak')&&!str_contains(json_encode($body),'also-never-leak'),'no token or secret in response');
$failed=static function(int $days):void{throw new RuntimeException('sensitive-google-error-body');};
[$status,$body]=rm_gsc_app_request('admin','POST','1',[],['action'=>'refresh','days'=>28],$products,$read,$failed);
ok($status===503&&!str_contains(json_encode($body),'sensitive-google-error-body'),'safe Google error');
[$status,$body]=rm_gsc_app_request('admin','GET','',['days'=>28],null,$products,$read,$refresh);
ok($status===200&&$body['has_report'],'old cache remains after failure');
$many=$report;for($i=0;$i<110;++$i)$many['current']['queries']['rows'][]=$row('https://rentmor.pl/index.php?id=1',0,1,5,'fraza '.$i);
$body=rm_gsc_app_report($many,$products,28,true,true);ok(count($body['equipment'][0]['queries'])===100,'bounded query list');
$many['ranges']['current']=['2025-01-01','2025-01-28'];$many['current']['queries']['truncated']=true;
$body=rm_gsc_app_report($many,$products,28,true,true);ok($body['stale']&&$body['truncated'],'stale and partial flags');
$protocol=file_get_contents(__DIR__.'/../server/protocol_sync.php');
ok(strpos($protocol,'rm_gsc_app_handle($role,$method);')>strpos($protocol,'$role = rm_device_role('),'hook after active-device authorization');
ok(strpos($protocol,'rm_gsc_app_handle($role,$method);')>strpos($protocol,"\$_SERVER['HTTP_X_RENTMOR_SYNC']"),'hook after native write header');
echo 'PASS: '.$count." native Search Console API checks (fixtures only)\n";
