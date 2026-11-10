<?php
declare(strict_types=1);
// SSH stdin only. This transaction is never placed in public_html.
$payload=json_decode(base64_decode('__NATIVE_GSC_PAYLOAD__',true),true,512,JSON_THROW_ON_ERROR);
function ng_read(string $path):?array{
    if(is_link($path))throw new RuntimeException('Symlink');if(!file_exists($path))return null;
    if(!is_file($path)||filesize($path)>100000000)throw new RuntimeException('Unsafe file');
    $data=file_get_contents($path);if($data===false)throw new RuntimeException('Read failed');return ['data'=>$data,'mode'=>fileperms($path)&0777];
}
function ng_put(string $path,string $data,int $mode):void{
    $tmp='rentmor-control-private/native-gsc-upload-'.bin2hex(random_bytes(12));
    try{
        $fp=fopen($tmp,'xb');if(!$fp)throw new RuntimeException('Stage failed');
        try{if(!chmod($tmp,$mode)||fwrite($fp,$data)!==strlen($data)||!fflush($fp))throw new RuntimeException('Incomplete upload');}finally{fclose($fp);}
        if(file_get_contents($tmp)!==$data||!rename($tmp,$path))throw new RuntimeException('Atomic rename');
        $read=ng_read($path);if($read===null||$read['data']!==$data||$read['mode']!==$mode)throw new RuntimeException('Readback');
    }finally{if(is_file($tmp))unlink($tmp);}
}
function ng_rollback(string $backup):void{
    if(!preg_match('~^rentmor-control-private/native-gsc-backup-[a-f0-9]{24}$~D',$backup))throw new RuntimeException('Bad backup');
    $manifest=json_decode((string)file_get_contents($backup.'/manifest.json'),true,512,JSON_THROW_ON_ERROR);
    foreach($manifest as $row){$current=ng_read($row['path']);$hash=$current===null?null:hash('sha256',$current['data']);if($hash!==$row['before_sha']&&$hash!==$row['after_sha'])throw new RuntimeException('Concurrent edit blocks rollback');}
    foreach(array_reverse($manifest)as $row){
        $current=ng_read($row['path']);if(($current===null?null:hash('sha256',$current['data']))===$row['before_sha'])continue;
        if($row['before_sha']===null){if(!unlink($row['path']))throw new RuntimeException('Unlink failed');}
        else{$old=(string)file_get_contents($backup.'/'.$row['index']);if(hash('sha256',$old)!==$row['before_sha'])throw new RuntimeException('Backup differs');ng_put($row['path'],$old,$row['before_mode']);}
    }
}
$phase='private-directory';$lock=null;
try{
    umask(0077);
    foreach(['rentmor-control-private','rentmor-control-private/progress']as $dir)if(is_link($dir)||!is_dir($dir)||(fileperms($dir)&0077))throw new RuntimeException('Private directory');
    $lockpath='rentmor-control-private/native-gsc-publish.lock';if(is_link($lockpath))throw new RuntimeException('Lock symlink');
    $lock=fopen($lockpath,'c+');if(!$lock||!chmod($lockpath,0600)||!flock($lock,LOCK_EX|LOCK_NB))throw new RuntimeException('Busy');
    if(($payload['mode']??'')==='rollback'){ng_rollback($payload['backup']);echo "NATIVE_GSC_ROLLBACK_OK\n";exit;}
    if(($payload['mode']??'')!=='publish')throw new RuntimeException('Bad mode');
    $phase='progress-baseline';$changes=[];
    foreach(['CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md']as $name){
        $path='rentmor-control-private/progress/'.$name;$old=ng_read($path);
        if($old===null||($old['mode']&0077)||sha1('blob '.strlen($old['data'])."\0".$old['data'])!==$payload['docs'][$name]['before_blob'])throw new RuntimeException('Private progress changed');
        $new=base64_decode($payload['docs'][$name]['content'],true);if($new===false)throw new RuntimeException('Payload');
        $changes[]=['path'=>$path,'before'=>$old,'after'=>$new,'mode'=>0600];
    }
    if(empty($payload['progress_only'])){
        $phase='protocol-baseline';$path='public_html/protocol_sync.php';$old=ng_read($path);
        if($old===null)throw new RuntimeException('API missing');
        $blob=sha1('blob '.strlen($old['data'])."\0".$old['data']);
        if($blob!==$payload['protocol_before_blob']){echo 'NATIVE_GSC_PROTOCOL_BLOB '.$blob."\n";throw new RuntimeException('API changed');}
        [$anchor,$hook]=$payload['hook'];$new=$old['data'];
        if(substr_count($new,$anchor)!==1||str_contains($new,'rm_gsc_app_handle('))throw new RuntimeException('Unknown API template');
        $new=str_replace($anchor,$hook.$anchor,$new);token_get_all($new,TOKEN_PARSE);
        $protocol=['path'=>$path,'before'=>$old,'after'=>$new,'mode'=>$old['mode']];
        $phase='helper-preflight';$helper=base64_decode($payload['helper'],true);if($helper===false)throw new RuntimeException('Helper payload');token_get_all($helper,TOKEN_PARSE);
        $helperPath='public_html/search_console_app.php';if(ng_read($helperPath)!==null)throw new RuntimeException('Helper already exists');
        $base=ng_read('public_html/search_console.php');if($base===null||hash('sha256',$base['data'])!==$payload['core_sha256'])throw new RuntimeException('Shared Search Console changed');
        $phase='apk-preflight';
        if(!preg_match('~^rentmor-control-private/native-gsc-stage-[a-f0-9]{24}\.apk$~D',$payload['staged_apk']))throw new RuntimeException('Staging path');
        $next=ng_read($payload['staged_apk']);$apk=ng_read('public_html/RentMor.apk');$meta=ng_read('public_html/app-release.json');
        if($next===null||$next['mode']!==0600||hash('sha256',$next['data'])!==$payload['apk_sha256']||strlen($next['data'])!==$payload['apk_size'])throw new RuntimeException('Staged APK differs');
        if($apk===null||hash('sha256',$apk['data'])!==$payload['apk_before_sha256']||$meta===null)throw new RuntimeException('Published APK changed');
        $previous=json_decode($meta['data'],true,512,JSON_THROW_ON_ERROR);
        if(($previous['version_code']??0)!==44||($previous['sha256']??'')!==$payload['apk_before_sha256'])throw new RuntimeException('Release metadata changed');
        $metadata=json_encode(['version'=>'1.7.30','version_code'=>45,'size'=>strlen($next['data']),'sha256'=>$payload['apk_sha256']],JSON_PRETTY_PRINT|JSON_THROW_ON_ERROR)."\n";
        $changes=array_merge([
            ['path'=>$helperPath,'before'=>null,'after'=>$helper,'mode'=>0644],$protocol,
            ['path'=>'public_html/RentMor.apk','before'=>$apk,'after'=>$next['data'],'mode'=>$apk['mode']],
            ['path'=>'public_html/app-release.json','before'=>$meta,'after'=>$metadata,'mode'=>$meta['mode']],
        ],$changes);
    }
    $phase='backup';$backup='rentmor-control-private/native-gsc-backup-'.bin2hex(random_bytes(12));if(!mkdir($backup,0700))throw new RuntimeException('Backup directory');$manifest=[];
    foreach($changes as $i=>$row){if($row['before']!==null)ng_put($backup.'/'.$i,$row['before']['data'],0600);$manifest[]=['index'=>$i,'path'=>$row['path'],'before_sha'=>$row['before']===null?null:hash('sha256',$row['before']['data']),'before_mode'=>$row['before']['mode']??null,'after_sha'=>hash('sha256',$row['after'])];}
    ng_put($backup.'/manifest.json',json_encode($manifest,JSON_THROW_ON_ERROR),0600);
    $phase='publication';
    try{
        foreach($changes as $row){$current=ng_read($row['path']);if(($current['data']??null)!==($row['before']['data']??null))throw new RuntimeException('Concurrent edit');ng_put($row['path'],$row['after'],$row['mode']);}
        $hashes=[];foreach($changes as $row){$read=ng_read($row['path']);if($read===null||$read['data']!==$row['after']||$read['mode']!==$row['mode'])throw new RuntimeException('Final readback');$hashes[basename($row['path'])]=hash('sha256',$read['data']);}
    }catch(Throwable $e){ng_rollback($backup);throw new RuntimeException('Publication rolled back');}
    echo 'NATIVE_GSC_RESULT '.json_encode(['backup'=>$backup,'sha256'=>$hashes],JSON_THROW_ON_ERROR)."\n";
}catch(Throwable $e){echo 'NATIVE_GSC_STOPPED '.$phase.' '.get_class($e)."\n";exit(1);}
finally{if(is_resource($lock)){flock($lock,LOCK_UN);fclose($lock);}if(isset($payload['staged_apk'])&&preg_match('~^rentmor-control-private/native-gsc-stage-[a-f0-9]{24}\.apk$~D',$payload['staged_apk'])&&is_file($payload['staged_apk']))unlink($payload['staged_apk']);}
