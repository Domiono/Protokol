<?php
declare(strict_types=1);
// Public installer only; never loads admin configuration, sessions or customer data.
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');header('Expires: 0');header('X-Content-Type-Options: nosniff');
if(!in_array($_SERVER['REQUEST_METHOD']??'GET',['GET','HEAD'],true)){http_response_code(405);header('Allow: GET, HEAD');exit;}
$meta=__DIR__.'/app-release.json';$apk=__DIR__.'/RentMor.apk';$stream=null;
try{
    if(is_link($meta)||!is_file($meta)||filesize($meta)>4096||is_link($apk))throw new RuntimeException();
    $release=json_decode((string)file_get_contents($meta),true);
    if(!is_array($release)||!is_string($release['version']??null)||!preg_match('/^\d+\.\d+\.\d+$/D',$release['version'])
        ||!is_string($release['sha256']??null)||!preg_match('/^[a-f0-9]{64}$/D',$release['sha256'])||!is_int($release['size']??null))throw new RuntimeException();
    $stream=fopen($apk,'rb');if(!$stream)throw new RuntimeException();$stat=fstat($stream);
    if(($stat['mode']&0170000)!==0100000||$stat['size']!==$release['size']||$stat['size']>100000000)throw new RuntimeException();
    $hash=hash_init('sha256');hash_update_stream($hash,$stream);
    if(!hash_equals($release['sha256'],hash_final($hash))||!rewind($stream))throw new RuntimeException();
    header('Content-Type: application/vnd.android.package-archive');
    header('Content-Disposition: attachment; filename="RentMor-'.$release['version'].'.apk"');
    header('Content-Length: '.$stat['size']);
    if(($_SERVER['REQUEST_METHOD']??'GET')!=='HEAD')fpassthru($stream);
}catch(Throwable $e){http_response_code(503);header('Retry-After: 30');header('Content-Type: text/plain; charset=utf-8');echo 'Instalator jest chwilowo aktualizowany. Spróbuj ponownie za chwilę.';}
finally{if(is_resource($stream))fclose($stream);}
