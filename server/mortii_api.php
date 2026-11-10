<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');header('Cache-Control: no-store');header('X-Content-Type-Options: nosniff');header("Content-Security-Policy: default-src 'none'; frame-ancestors 'none'");
function rm_mortii_reply(int $status,array $data): never{http_response_code($status);echo json_encode($data,JSON_UNESCAPED_UNICODE|JSON_INVALID_UTF8_SUBSTITUTE);exit;}
if(empty($_SERVER['HTTPS'])||$_SERVER['HTTPS']==='off')rm_mortii_reply(403,['error'=>'Wymagane HTTPS.']);
if(!in_array($_SERVER['REQUEST_METHOD']??'',['GET','POST'],true))rm_mortii_reply(405,['error'=>'Nieprawidłowe żądanie.']);
if(($_SERVER['HTTP_SEC_FETCH_SITE']??'')==='cross-site')rm_mortii_reply(403,['error'=>'Otwórz formularz na stronie RentMor.']);
if(isset($_SERVER['HTTP_ORIGIN'])&&!in_array($_SERVER['HTTP_ORIGIN'],['https://rentmor.pl','https://www.rentmor.pl'],true))rm_mortii_reply(403,['error'=>'Nieprawidłowe pochodzenie.']);
require_once __DIR__.'/ai_chat_service.php';require_once __DIR__.'/mortii_service.php';require_once __DIR__.'/ai_app_knowledge.php';
// Separate named session: never open or mutate an administrator session.
session_name('rm_mortii');ini_set('session.use_strict_mode','1');session_set_cookie_params(['lifetime'=>0,'path'=>'/','secure'=>true,'httponly'=>true,'samesite'=>'Lax']);session_start();
$locks=[];
try{
    $dir=rm_ai_chat_dir(dirname(__DIR__).'/rentmor-private',$_SERVER['DOCUMENT_ROOT']??__DIR__);
    $ip=(string)($_SERVER['REMOTE_ADDR']??'unknown');
    if(($_SERVER['REQUEST_METHOD']??'')==='GET'){
        rm_mortii_limit($dir,$ip,'bootstrap');
        $_SESSION['csrf']??=bin2hex(random_bytes(32));$_SESSION['opened']??=time();
        $schedule=rm_booking_schedule_read($dir);$cat=rm_ai_catalogue(__DIR__.'/data.json');
        try{rm_mortii_pickup_address($dir);$bookingEnabled=true;}catch(Throwable $e){$bookingEnabled=false;}
        rm_mortii_reply(200,['csrf'=>$_SESSION['csrf'],'request_id'=>bin2hex(random_bytes(16)),'history'=>$_SESSION['history']??[],
            'booking_enabled'=>$bookingEnabled,'weekly'=>$schedule['weekly'],'dates'=>$schedule['dates'],'products'=>array_map(fn($p)=>['name'=>$p['name'],'rate'=>$p['price_pln_per_day'],'deposit'=>$p['deposit_pln']],$cat['products'])]);
    }
    if((int)($_SERVER['CONTENT_LENGTH']??0)>12000)rm_mortii_reply(413,['error'=>'Za dużo danych.']);
    $raw=(string)file_get_contents('php://input',false,null,0,12001);if(strlen($raw)>12000)rm_mortii_reply(413,['error'=>'Za dużo danych.']);
    $in=json_decode($raw,true);
    if(!is_array($in)||!is_string($in['csrf']??null)||!hash_equals($_SESSION['csrf']??'INVALID',$in['csrf']))rm_mortii_reply(403,['error'=>'Odśwież formularz.']);
    $id=$in['request_id']??null;
    if(!is_string($id)||!preg_match('/^[a-f0-9]{32}$/D',$id))rm_mortii_reply(400,['error'=>'Odśwież formularz.']);
    if(($_SESSION['last_id']??'')===$id)rm_mortii_reply(200,$_SESSION['last_response']);
    $action=$in['action']??'';
    if($action==='reset'){$_SESSION['history']=[];$out=['history'=>[]];}
    elseif($action==='chat'){
        rm_mortii_limit($dir,$ip,'chat');$message=$in['message']??null;
        if(!is_string($message)||strlen($message)>2000)throw new InvalidArgumentException('Wpisz krótszą wiadomość.');
        if(preg_match('/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}|(?:\+?\d[ ()-]*){9,}/i',$message))throw new InvalidArgumentException('Dane kontaktowe wpisz w formularzu Rezerwuj, nie w czacie.');
        $history=rm_ai_history($_SESSION['history']??[]);
        $cat=rm_ai_catalogue(__DIR__.'/data.json');
        $context=rm_ai_knowledge_context($cat,rm_ai_public_knowledge_read($dir),$message);
        $answer=rm_ai_chat(dirname(__DIR__).'/rentmor-private',$_SERVER['DOCUMENT_ROOT']??__DIR__,$message,$context,$history,true);
        $history[]=['role'=>'user','content'=>$message];$history[]=['role'=>'assistant','content'=>$answer];
        $_SESSION['history']=rm_ai_history($history);$out=['history'=>$_SESSION['history']];
    }elseif($action==='request'){
        rm_mortii_limit($dir,$ip,'request');
        if(($in['website']??'')!==''||time()-($_SESSION['opened']??time())<3)throw new InvalidArgumentException('Odczekaj chwilę i spróbuj ponownie.');
        if(rm_rental_maintenance())throw new RuntimeException('Konserwacja.');
        rm_mortii_pickup_address($dir);
        $locks[]=rm_booking_lock($dir.'/handover.lock');$locks[]=rm_booking_lock($dir.'/mortii-requests.lock');
        $locks[]=rm_booking_lock(rm_rental_file('reservations.json').'.lock');$al=rm_lock_assets();if(!$al)throw new RuntimeException('Brak blokady.');$locks[]=$al;
        $out=rm_mortii_request($dir,$in,$id,rm_booking_schedule_read($dir),rm_ai_catalogue(__DIR__.'/data.json'),rm_booking_list(rm_rental_file('reservations.json')),rm_read_assets());
        require_once __DIR__.'/push_bridge.php';
        rm_push_notify_new_message('mortii_'.$id);
    }else throw new InvalidArgumentException('Nieprawidłowa operacja.');
    $_SESSION['last_id']=$id;$_SESSION['last_response']=$out;
    rm_mortii_reply(200,$out);
}catch(InvalidArgumentException $e){rm_mortii_reply(400,['error'=>$e->getMessage()]);}
catch(Throwable $e){rm_mortii_reply(503,['error'=>'Mortii jest chwilowo niedostępny lub osiągnięto limit. Spróbuj później albo zadzwoń: 733 891 281.']);}
finally{foreach(array_reverse($locks) as $lock){flock($lock,LOCK_UN);fclose($lock);}}
