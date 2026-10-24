<?php
declare(strict_types=1);
/**
 * RentMor Firebase Cloud Messaging bridge.
 * Store credentials and token registry in ../rentmor-private (OUTSIDE public_html).
 * Never place the Firebase service-account private key in this repository or web root.
 */
if (isset($_SERVER['SCRIPT_FILENAME']) && realpath((string)$_SERVER['SCRIPT_FILENAME']) === __FILE__) {
    http_response_code(404); exit;
}
function rm_push_private_dir(): string {
    return dirname(__DIR__) . '/rentmor-private';
}
function rm_push_registry_file(): string {
    return rm_push_private_dir() . '/fcm-tokens.json';
}
function rm_push_read_json(string $path): array {
    if (!is_file($path)) return [];
    $decoded=json_decode((string)@file_get_contents($path),true);
    return is_array($decoded)?$decoded:[];
}
function rm_push_device_hash(): string {
    return hash('sha256',(string)($_COOKIE['rm_protocol_device'] ?? ''));
}
/** Registration is called only by the authenticated protocol_sync.php endpoint. */
function rm_push_register(string $deviceHash, string $token, bool $admin): bool {
    $dir=rm_push_private_dir();
    if (!is_dir($dir) && !@mkdir($dir,0700,true) && !is_dir($dir)) return false;
    $path=rm_push_registry_file();
    $lock=@fopen($path.'.lock','c');
    if (!$lock||!flock($lock,LOCK_EX)) { if($lock)fclose($lock);return false; }
    try {
        $tokens=rm_push_read_json($path);
        if (!array_is_list($tokens)) $tokens=[];
        $new=[];$now=time();
        foreach($tokens as $row){
            if(!is_array($row)||!isset($row['device_hash'],$row['token'],$row['updated']))continue;
            if(!is_string($row['device_hash'])||!is_string($row['token']))continue;
            if($row['device_hash']===$deviceHash || ($token!==''&&hash_equals($row['token'],$token)))continue;
            if((int)$row['updated']<$now-90*86400)continue;
            $new[]=$row;
        }
        if($admin && $token!=='')$new[]=['device_hash'=>$deviceHash,'token'=>$token,'updated'=>$now];
        if(count($new)>30)$new=array_slice($new,-30);
        $json=json_encode($new,JSON_UNESCAPED_SLASHES);
        if($json===false)return false;
        $temp=$path.'.'.bin2hex(random_bytes(8)).'.tmp';
        if(file_put_contents($temp,$json,LOCK_EX)===false)return false;
        @chmod($temp,0600);
        $ok=@rename($temp,$path);
        if(!$ok)@unlink($temp);
        return $ok;
    }finally{flock($lock,LOCK_UN);fclose($lock);}
}
function rm_push_active_admin_hashes(): array {
    $file=__DIR__.'/protocol_devices.json';
    $data=rm_push_read_json($file);
    $hashes=[];
    foreach(($data['devices'] ?? []) as $device){
        if(!is_array($device)||empty($device['active']))continue;
        // Match protocol_sync's existing role mapping for backwards compatibility.
        if(($device['role'] ?? '')==='assistant')continue;
        $hash=(string)($device['token_hash']??'');
        if(preg_match('/^[a-f0-9]{64}$/',$hash))$hashes[$hash]=true;
    }
    return $hashes;
}
function rm_push_access_token(array $account): ?string {
    $email=$account['client_email']??'';
    $key=$account['private_key']??'';
    if(!is_string($email)||!is_string($key)||!str_contains($email,'@')||!str_contains($key,'PRIVATE KEY'))return null;
    if(!function_exists('openssl_sign')||!function_exists('curl_init'))return null;
    $now=time();
    $base64=static fn(string $str):string=>rtrim(strtr(base64_encode($str),'+/','-_'),'=');
    $header=$base64('{"alg":"RS256","typ":"JWT"}');
    $claims=json_encode([
        'iss'=>$email,'scope'=>'https://www.googleapis.com/auth/firebase.messaging',
        'aud'=>'https://oauth2.googleapis.com/token','iat'=>$now,'exp'=>$now+3500
    ],JSON_UNESCAPED_SLASHES);
    if($claims===false)return null;
    $unsigned=$header.'.'.$base64($claims);
    $signature='';
    if(!openssl_sign($unsigned,$signature,$key,OPENSSL_ALGO_SHA256))return null;
    $jwt=$unsigned.'.'.$base64($signature);
    $body=http_build_query(['grant_type'=>'urn:ietf:params:oauth:grant-type:jwt-bearer','assertion'=>$jwt]);
    $ch=curl_init('https://oauth2.googleapis.com/token');
    curl_setopt_array($ch,[CURLOPT_POST=>true,CURLOPT_POSTFIELDS=>$body,CURLOPT_RETURNTRANSFER=>true,
        CURLOPT_CONNECTTIMEOUT=>3,CURLOPT_TIMEOUT=>7,CURLOPT_HTTPHEADER=>['Content-Type: application/x-www-form-urlencoded']]);
    $response=curl_exec($ch);$http=(int)curl_getinfo($ch,CURLINFO_HTTP_CODE);
    curl_close($ch);
    if(!is_string($response)||$http!==200)return null;
    $data=json_decode($response,true);
    return is_array($data)&&is_string($data['access_token']??null)?$data['access_token']:null;
}
function rm_push_notify_new_message(string $messageId): void {
    if($messageId===''||strlen($messageId)>128)return;
    $account=rm_push_read_json(rm_push_private_dir().'/fcm-service-account.json');
    $project=$account['project_id']??'';
    if(!is_string($project)||!preg_match('/^[a-zA-Z0-9_-]{4,100}$/',$project))return;
    $active=rm_push_active_admin_hashes();
    if(!$active)return;
    $targets=[];$now=time();
    foreach(rm_push_read_json(rm_push_registry_file()) as $row){
        if(!is_array($row))continue;
        $hash=$row['device_hash']??'';
        $token=$row['token']??'';
        if(!is_string($hash)||!isset($active[$hash])||!is_string($token))continue;
        if((int)($row['updated']??0)<$now-90*86400)continue;
        $targets[]=$token;
    }
    if(!$targets)return;
    $access=rm_push_access_token($account);
    if($access===null)return;
    $url='https://fcm.googleapis.com/v1/projects/'.rawurlencode($project).'/messages:send';
    foreach(array_slice(array_unique($targets),0,10) as $token){
        $payload=json_encode(['message'=>[
            'token'=>$token,
            'data'=>['type'=>'contact_message','message_id'=>$messageId],
            'android'=>['priority'=>'high','ttl'=>'3600s']
        ]],JSON_UNESCAPED_SLASHES);
        if($payload===false)continue;
        $ch=curl_init($url);
        curl_setopt_array($ch,[CURLOPT_POST=>true,CURLOPT_POSTFIELDS=>$payload,CURLOPT_RETURNTRANSFER=>true,
            CURLOPT_CONNECTTIMEOUT=>2,CURLOPT_TIMEOUT=>5,CURLOPT_HTTPHEADER=>[
                'Content-Type: application/json; charset=UTF-8','Authorization: Bearer '.$access
            ]]);
        $response=curl_exec($ch);
        $http=(int)curl_getinfo($ch,CURLINFO_HTTP_CODE);
        curl_close($ch);
        if($http!==200)error_log('RentMor FCM send failed: HTTP '.$http); // no PII or tokens in logs
    }
}
