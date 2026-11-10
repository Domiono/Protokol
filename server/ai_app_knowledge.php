<?php
declare(strict_types=1);
require_once __DIR__.'/ai_knowledge_store.php';
/** Public answers use only this deliberately separate, administrator-published note. */
function rm_ai_public_knowledge_read(string $dir): array {
    $path=$dir.'/public-knowledge';
    if(is_link($path))throw new RuntimeException('Nieprawidłowy magazyn wiedzy.');
    if(!file_exists($path))return ['text'=>'','revision'=>hash('sha256',''),'updated_at'=>null];
    if(!is_dir($path)||realpath($path)!==$path)throw new RuntimeException('Nieprawidłowy magazyn wiedzy.');
    return rm_ai_knowledge_read($path);
}
function rm_ai_app_knowledge_dir(string $dir,string $audience,bool $create=false): string {
    if($audience==='private')return $dir;
    if($audience!=='public')throw new InvalidArgumentException('Wybierz rodzaj wiedzy.');
    $path=$dir.'/public-knowledge';
    if(is_link($path))throw new RuntimeException('Nieprawidłowy magazyn wiedzy.');
    if(!file_exists($path)&&$create&&!mkdir($path,0700)&&!is_dir($path))throw new RuntimeException('Nie udało się przygotować wiedzy.');
    if(file_exists($path)&&(!is_dir($path)||realpath($path)!==$path))throw new RuntimeException('Nieprawidłowy magazyn wiedzy.');
    return $path;
}
/** Role is resolved from an active device on every request, never from JSON. */
function rm_ai_app_knowledge_request(string $role,string $method,string $header,array $query,?array $input,string $dir,array $catalogue=[]): array {
    if($role!=='admin')return [403,['ok'=>false,'error'=>'Wiedza AI jest dostępna tylko dla administratora.']];
    if(!in_array($method,['GET','POST'],true))return [405,['ok'=>false,'error'=>'Niedozwolona metoda.']];
    if($method==='POST'&&$header!=='1')return [403,['ok'=>false,'error'=>'Brak nagłówka aplikacji.']];
    $audience=$method==='GET'?($query['audience']??'public'):($input['audience']??null);
    if(!is_string($audience)||!in_array($audience,['public','private'],true))return [400,['ok'=>false,'error'=>'Wybierz rodzaj wiedzy.']];
    try {
        $base=rm_ai_app_knowledge_dir($dir,$audience,$method==='POST');
        $notes=rm_ai_equipment_notes($base);
        $names=array_values(array_unique(array_merge(array_column($catalogue['products']??[],'name'),array_keys($notes))));
        sort($names,SORT_NATURAL|SORT_FLAG_CASE);
        $equipment=$method==='GET'?($query['equipment']??''):($input['equipment']??'');
        if(!is_string($equipment)||($equipment!==''&&!in_array($equipment,$names,true)))return [400,['ok'=>false,'error'=>'Wybierz sprzęt z listy.']];
        $path=$equipment===''?$base:rm_ai_equipment_dir($base,$equipment,false);
        if($method==='POST'){
            if(!is_string($input['text']??null)||!is_string($input['revision']??null))return [400,['ok'=>false,'error'=>'Nieprawidłowy zapis wiedzy.']];
            if(!preg_match('/^[a-f0-9]{64}$/D',$input['revision']))return [400,['ok'=>false,'error'=>'Odśwież wiedzę przed zapisem.']];
            // Validate before mkdir as well as in the existing atomic storage layer.
            if(strlen($input['text'])>12000)return [400,['ok'=>false,'error'=>'Wiedza: maksymalnie 12000 bajtów.']];
            if($equipment!=='')$path=rm_ai_equipment_dir($base,$equipment,true);
            rm_ai_knowledge_save($path,$input['text'],$input['revision']);
        }
        $note=rm_ai_knowledge_read($path,false);
        unset($note['equipment_notes']);
        return [200,['ok'=>true,'role'=>'admin','audience'=>$audience,'knowledge'=>$note,'max_bytes'=>12000,'equipment'=>$equipment,'equipment_options'=>$names]];
    }catch(InvalidArgumentException $e){return [400,['ok'=>false,'error'=>$e->getMessage()]];}
    catch(RuntimeException $e){
        if(str_starts_with($e->getMessage(),'Wiedzę zmieniono'))return [409,['ok'=>false,'error'=>'Wiedzę zmieniono na innym urządzeniu. Skopiuj swój tekst i pobierz aktualną wersję.']];
        return [503,['ok'=>false,'error'=>'Wiedza jest chwilowo niedostępna. Twój tekst nie został potwierdzony.']];
    }
}
function rm_ai_app_knowledge_handle(string $role,string $method): never {
    if($role!=='admin')rm_json_response(403,['ok'=>false,'error'=>'Wiedza AI jest dostępna tylko dla administratora.']);
    if(!in_array($method,['GET','POST'],true))rm_json_response(405,['ok'=>false,'error'=>'Niedozwolona metoda.']);
    $input=null;
    if($method==='POST'){
        if(($_SERVER['HTTP_X_RENTMOR_SYNC']??'')!=='1')rm_json_response(403,['ok'=>false,'error'=>'Brak nagłówka aplikacji.']);
        $raw=file_get_contents('php://input',false,null,0,65537);
        if(!is_string($raw)||strlen($raw)>65536)rm_json_response(413,['ok'=>false,'error'=>'Za dużo danych.']);
        $input=json_decode($raw,true);
        if(!is_array($input))rm_json_response(400,['ok'=>false,'error'=>'Nieprawidłowy zapis wiedzy.']);
    }
    require_once __DIR__.'/ai_chat_service.php';
    try{$dir=rm_ai_chat_dir(dirname(__DIR__).'/rentmor-private',$_SERVER['DOCUMENT_ROOT']??__DIR__);}
    catch(Throwable $e){rm_json_response(503,['ok'=>false,'error'=>'Magazyn wiedzy jest niedostępny.']);}
    try{$catalogue=rm_ai_catalogue(__DIR__.'/data.json');}catch(Throwable $e){rm_json_response(503,['ok'=>false,'error'=>'Lista sprzętu jest niedostępna.']);}
    [$status,$body]=rm_ai_app_knowledge_request($role,$method,$_SERVER['HTTP_X_RENTMOR_SYNC']??'',$_GET,$input,$dir,$catalogue);
    rm_json_response($status,$body);
}
