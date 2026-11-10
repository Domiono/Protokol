<?php
declare(strict_types=1);
if(realpath((string)($_SERVER['SCRIPT_FILENAME']??''))===__FILE__){http_response_code(404);exit;}
require_once __DIR__.'/search_console.php';

/** Return a bounded, explicitly whitelisted projection; never return Google credentials. */
function rm_gsc_app_report(?array $report,array $products,int $days,bool $configured,bool $connected):array {
    $body=['ok'=>true,'role'=>'admin','days'=>$days,'configured'=>$configured,'connected'=>$connected,
        'has_report'=>false,'equipment'=>[],'setup_url'=>'https://rentmor.pl/admin.php?tab=google'];
    if($report===null)return $body;
    if(($report['property']??'')!==RM_GSC_PROPERTY||($report['days']??0)!==$days
        ||!isset($report['current']['pages']['rows'],$report['previous']['pages']['rows'],
        $report['current']['queries']['rows'],$report['previous']['queries']['rows'],$report['ranges'],$report['fetched_at']))throw new RuntimeException('Invalid report');
    $body['has_report']=true;$body['ranges']=$report['ranges'];$body['fetched_at']=$report['fetched_at'];
    $body['stale']=$report['ranges']!==rm_gsc_periods($days);$body['truncated']=false;
    foreach(['current','previous'] as $period)foreach(['pages','queries'] as $kind)$body['truncated']=$body['truncated']||!empty($report[$period][$kind]['truncated']);
    $current=rm_gsc_group($report['current']['pages']['rows'],$products);
    $previous=rm_gsc_group($report['previous']['pages']['rows'],$products);
    $queries=rm_gsc_group($report['current']['queries']['rows'],$products,true);
    $oldQueries=rm_gsc_group($report['previous']['queries']['rows'],$products,true);
    $names=[];
    foreach($products as $product)if(is_array($product)&&isset($product['id'],$product['name']))$names[(string)$product['id']]=(string)$product['name'];
    $names['_other']='Pozostałe strony / nierozpoznane adresy';$empty=rm_gsc_summary([]);
    foreach($names as $id=>$name){
        $items=$queries[$id]??[];
        uasort($items,static fn($a,$b)=>($b['clicks']<=>$a['clicks'])?:($b['impressions']<=>$a['impressions']));
        $top=[];
        foreach(array_slice($items,0,100,true)as $query=>$metrics)$top[]=['query'=>(string)$query,'current'=>$metrics,'previous'=>$oldQueries[$id][$query]??$empty];
        $body['equipment'][]=['id'=>(string)$id,'name'=>$name,'current'=>$current[$id]??$empty,'previous'=>$previous[$id]??$empty,'queries'=>$top];
    }
    usort($body['equipment'],static fn($a,$b)=>$b['current']['impressions']<=>$a['current']['impressions']);
    return $body;
}

/** Authorization precedes storage/network calls. Callbacks allow isolated tests. */
function rm_gsc_app_request(string $role,string $method,string $header,array $query,?array $input,
    array $products,callable $reader,callable $refresh):array {
    if($role!=='admin')return [403,['ok'=>false,'error'=>'Search Console jest dostępne tylko dla administratora.']];
    if(!in_array($method,['GET','POST'],true))return [405,['ok'=>false,'error'=>'Niedozwolona metoda.']];
    if($method==='POST'&&$header!=='1')return [403,['ok'=>false,'error'=>'Brak nagłówka aplikacji.']];
    $raw=$method==='POST'?($input['days']??null):($query['days']??'28');
    if(!is_scalar($raw)||!in_array((string)$raw,['7','28','90'],true))return [400,['ok'=>false,'error'=>'Wybierz okres 7, 28 lub 90 dni.']];
    if($method==='POST'&&($input['action']??'')!=='refresh')return [400,['ok'=>false,'error'=>'Nieprawidłowa operacja Google.']];
    $days=(int)$raw;
    try{
        if($method==='POST')$refresh($days);
        // Do not include values from either credential file in the API projection.
        return [200,rm_gsc_app_report($reader('cache-'.$days.'.json'),$products,$days,$reader('client.json')!==null,$reader('token.json')!==null)];
    }catch(Throwable $e){return [503,['ok'=>false,'error'=>'Nie udało się pobrać raportu Google. Poprzednie dane pozostają zapisane. Sprawdź połączenie Google w panelu WWW.']];}
}
function rm_gsc_app_handle(string $role,string $method):never {
    if($role!=='admin')rm_json_response(403,['ok'=>false,'error'=>'Search Console jest dostępne tylko dla administratora.']);
    $input=null;
    if($method==='POST'){
        if(($_SERVER['HTTP_X_RENTMOR_SYNC']??'')!=='1')rm_json_response(403,['ok'=>false,'error'=>'Brak nagłówka aplikacji.']);
        $raw=file_get_contents('php://input',false,null,0,2049);
        if(!is_string($raw)||strlen($raw)>2048)rm_json_response(413,['ok'=>false,'error'=>'Za dużo danych.']);
        $input=json_decode($raw,true);
        if(!is_array($input))rm_json_response(400,['ok'=>false,'error'=>'Nieprawidłowe żądanie.']);
    }
    if(!in_array($method,['GET','POST'],true))rm_json_response(405,['ok'=>false,'error'=>'Niedozwolona metoda.']);
    try{
        $products=json_decode((string)file_get_contents(__DIR__.'/data.json'),true,512,JSON_THROW_ON_ERROR);
        if(!is_array($products)||!array_is_list($products))throw new RuntimeException('Invalid catalogue');
    }catch(Throwable $e){rm_json_response(503,['ok'=>false,'error'=>'Katalog sprzętu jest chwilowo niedostępny.']);}
    $refresh=static function(int $days):void{
        rm_gsc_lock(static function()use($days):void{
            $path=rm_gsc_path('cache-'.$days.'.json');
            // Shared recent cache avoids duplicate requests after an uncertain mobile response.
            if(is_file($path)&&filemtime($path)>time()-60)return;
            $client=rm_gsc_read('client.json');if(!$client)throw new RuntimeException('Google not configured');
            $token=rm_gsc_access_token($client);
            $report=rm_gsc_build_report($days,static fn(array $body):array=>rm_gsc_http(
                'https://www.googleapis.com/webmasters/v3/sites/'.rawurlencode(RM_GSC_PROPERTY).'/searchAnalytics/query',$body,$token));
            rm_gsc_write('cache-'.$days.'.json',$report);
        });
    };
    [$status,$body]=rm_gsc_app_request($role,$method,$_SERVER['HTTP_X_RENTMOR_SYNC']??'',$_GET,$input,$products,'rm_gsc_read',$refresh);
    rm_json_response($status,$body);
}
