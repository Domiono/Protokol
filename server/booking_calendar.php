<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');header("Content-Security-Policy: default-src 'none'; frame-ancestors 'none'");
function rm_calendar_reply(int $status,array $data): never {http_response_code($status);echo json_encode($data,JSON_UNESCAPED_UNICODE|JSON_INVALID_UTF8_SUBSTITUTE);exit;}
if(empty($_SERVER['HTTPS'])||$_SERVER['HTTPS']==='off')rm_calendar_reply(403,['error'=>'Wymagane HTTPS.']);
if(($_SERVER['REQUEST_METHOD']??'')!=='GET')rm_calendar_reply(405,['error'=>'Nieprawidłowe żądanie.']);
if(($_SERVER['HTTP_SEC_FETCH_SITE']??'')==='cross-site')rm_calendar_reply(403,['error'=>'Otwórz formularz na stronie RentMor.']);
require_once __DIR__.'/ai_chat_service.php';require_once __DIR__.'/mortii_service.php';require_once __DIR__.'/booking_calendar_rules.php';
$locks=[];
try{
    $dir=rm_ai_chat_dir(dirname(__DIR__).'/rentmor-private',$_SERVER['DOCUMENT_ROOT']??__DIR__);
    rm_mortii_limit($dir,(string)($_SERVER['REMOTE_ADDR']??'unknown'),'bootstrap');
    if(rm_rental_maintenance())throw new RuntimeException('Konserwacja.');
    $locks[]=rm_booking_lock($dir.'/handover.lock');
    $locks[]=rm_booking_lock(rm_rental_file('reservations.json').'.lock');
    $assetLock=rm_lock_assets();if(!$assetLock)throw new RuntimeException('Brak dostępu do egzemplarzy.');$locks[]=$assetLock;
    $schedule=rm_booking_schedule_read($dir);$items=rm_booking_list(rm_rental_file('reservations.json'));$assets=rm_read_assets();$catalogue=rm_ai_catalogue(__DIR__.'/data.json');
    // Release snapshot locks before computing a month's slots.
    foreach(array_reverse($locks) as $lock){flock($lock,LOCK_UN);fclose($lock);}$locks=[];
    rm_calendar_reply(200,rm_calendar_month($_GET,$schedule,$catalogue,$items,$assets));
}catch(InvalidArgumentException $e){rm_calendar_reply(400,['error'=>$e->getMessage()]);}
catch(Throwable $e){rm_calendar_reply(503,['error'=>'Nie udało się sprawdzić dostępności. Spróbuj ponownie lub zadzwoń.']);}
finally{foreach(array_reverse($locks) as $lock){flock($lock,LOCK_UN);fclose($lock);}}
