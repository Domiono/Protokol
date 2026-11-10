<?php
declare(strict_types=1);
// Owner-requested feature removal, executed over SSH stdin only.
$payload=json_decode(base64_decode('__REMOVE_GSC_PAYLOAD__',true),true,512,JSON_THROW_ON_ERROR);
function rg_read(string $path):?array{
    if(is_link($path))throw new RuntimeException('Symlink');if(!file_exists($path))return null;
    if(!is_file($path)||filesize($path)>100000000)throw new RuntimeException('Unsafe file');
    $b=file_get_contents($path);if($b===false)throw new RuntimeException('Read failed');return ['data'=>$b,'mode'=>fileperms($path)&0777];
}
function rg_put(string $path,string $data,int $mode):void{
    $tmp='rentmor-control-private/remove-gsc-upload-'.bin2hex(random_bytes(12));
    try{$fp=fopen($tmp,'xb');if(!$fp)throw new RuntimeException('Stage failed');try{if(!chmod($tmp,$mode)||fwrite($fp,$data)!==strlen($data)||!fflush($fp))throw new RuntimeException('Write failed');}finally{fclose($fp);}
        if(file_get_contents($tmp)!==$data||!rename($tmp,$path))throw new RuntimeException('Atomic write');$r=rg_read($path);if($r===null||$r['data']!==$data||$r['mode']!==$mode)throw new RuntimeException('Readback');
    }finally{if(is_file($tmp))unlink($tmp);}
}
function rg_restore(string $backup):void{
    if(!preg_match('~^rentmor-control-private/remove-gsc-backup-[a-f0-9]{24}$~D',$backup))throw new RuntimeException('Invalid backup');
    $rows=json_decode((string)file_get_contents($backup.'/manifest.json'),true,512,JSON_THROW_ON_ERROR);
    $active=dirname((string)realpath('public_html')).'/rentmor-private/search-console';$archived=$backup.'/google-connection-disabled';
    if(is_dir($archived)&&file_exists($active))throw new RuntimeException('Concurrent connection blocks rollback');
    foreach($rows as $row){$r=rg_read($row['path']);$h=$r===null?null:hash('sha256',$r['data']);if($h!==$row['before_sha']&&$h!==$row['after_sha'])throw new RuntimeException('Concurrent source blocks rollback');}
    foreach(array_reverse($rows)as $row){$r=rg_read($row['path']);if(($r===null?null:hash('sha256',$r['data']))===$row['before_sha'])continue;
        if($row['before_sha']===null){if(!unlink($row['path']))throw new RuntimeException('Restore unlink');}
        else{$b=(string)file_get_contents($backup.'/'.$row['index']);if(hash('sha256',$b)!==$row['before_sha'])throw new RuntimeException('Backup mismatch');rg_put($row['path'],$b,$row['before_mode']);}
    }
    if(is_dir($archived)&&!rename($archived,$active))throw new RuntimeException('Connection restore');
}
$phase='private-directory';$lock=null;$googleLock=null;
try{
    umask(0077);foreach(['rentmor-control-private','rentmor-control-private/progress']as $d)if(is_link($d)||!is_dir($d)||(fileperms($d)&0077))throw new RuntimeException('Private directory');
    $lp='rentmor-control-private/remove-gsc.lock';if(is_link($lp))throw new RuntimeException('Lock symlink');$lock=fopen($lp,'c+');if(!$lock||!chmod($lp,0600)||!flock($lock,LOCK_EX|LOCK_NB))throw new RuntimeException('Busy');
    if(($payload['mode']??'')==='rollback'){rg_restore($payload['backup']);echo "REMOVE_GSC_RESTORED\n";exit;}
    if(($payload['mode']??'')!=='publish')throw new RuntimeException('Bad mode');
    $phase='progress-baseline';$changes=[];
    foreach(['CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md']as $n){$p='rentmor-control-private/progress/'.$n;$before=rg_read($p);
        if($before===null||($before['mode']&0077)||sha1('blob '.strlen($before['data'])."\0".$before['data'])!==$payload['docs'][$n]['before_blob'])throw new RuntimeException('Private progress changed');
        $new=base64_decode($payload['docs'][$n]['content'],true);if($new===false)throw new RuntimeException('Payload');$changes[]=['path'=>$p,'before'=>$before,'after'=>$new,'mode'=>0600];}
    // Match the removed helper's dirname(__DIR__), including hosting webroot aliases.
    $connection=dirname((string)realpath('public_html')).'/rentmor-private/search-console';$hasConnection=false;
    if(empty($payload['progress_only'])){
        $phase='source-baseline';$sources=[];
        foreach(['admin.php','protocol_sync.php']as $name){$p='public_html/'.$name;$before=rg_read($p);if($before===null||hash('sha256',$before['data'])!==$payload['source_before'][$name])throw new RuntimeException('Source changed');$new=$before['data'];
            if($name==='admin.php'){
                foreach($payload['admin_remove']as $fragment){if(substr_count($new,$fragment)!==1)throw new RuntimeException('Unknown admin hook');$new=str_replace($fragment,'',$new);}
                if(substr_count($new,$payload['admin_auth'])!==1)throw new RuntimeException('Unknown admin auth');$new=str_replace($payload['admin_auth'],$payload['admin_auth'].$payload['admin_redirect'],$new);
                if(str_contains($new,'rm_gsc_admin_'))throw new RuntimeException('Remaining admin integration');
            }else{if(substr_count($new,$payload['api_remove'])!==1)throw new RuntimeException('Unknown API hook');$new=str_replace($payload['api_remove'],$payload['api_tombstone'],$new);if(str_contains($new,'rm_gsc_app_'))throw new RuntimeException('Remaining API integration');}
            token_get_all($new,TOKEN_PARSE);$sources[]=['path'=>$p,'before'=>$before,'after'=>$new,'mode'=>$before['mode']];
        }
        $removed=[];
        foreach(['search_console.php','search_console_admin.php','search_console_app.php']as $name){$p='public_html/'.$name;$before=rg_read($p);if($before===null||sha1('blob '.strlen($before['data'])."\0".$before['data'])!==$payload['helper_blobs'][$name])throw new RuntimeException('Helper changed');$removed[]=['path'=>$p,'before'=>$before,'after'=>null,'mode'=>$before['mode']];}
        $phase='apk-baseline';if(!preg_match('~^rentmor-control-private/remove-gsc-stage-[a-f0-9]{24}\.apk$~D',$payload['staged_apk']))throw new RuntimeException('Staging path');
        $new=rg_read($payload['staged_apk']);$old=rg_read('public_html/RentMor.apk');$meta=rg_read('public_html/app-release.json');
        if($new===null||$new['mode']!==0600||hash('sha256',$new['data'])!==$payload['apk_sha256']||strlen($new['data'])!==$payload['apk_size'])throw new RuntimeException('Staged APK differs');
        if($old===null||hash('sha256',$old['data'])!==$payload['apk_before_sha256']||$meta===null)throw new RuntimeException('APK changed');
        $metadata=json_decode($meta['data'],true,512,JSON_THROW_ON_ERROR);if(($metadata['version_code']??0)!==45||($metadata['sha256']??'')!==$payload['apk_before_sha256'])throw new RuntimeException('Metadata changed');
        $newMeta=json_encode(['version'=>'1.7.31','version_code'=>46,'size'=>strlen($new['data']),'sha256'=>$payload['apk_sha256']],JSON_PRETTY_PRINT|JSON_THROW_ON_ERROR)."\n";
        $changes=array_merge($sources,[['path'=>'public_html/RentMor.apk','before'=>$old,'after'=>$new['data'],'mode'=>$old['mode']],['path'=>'public_html/app-release.json','before'=>$meta,'after'=>$newMeta,'mode'=>$meta['mode']]],$removed,$changes);
    }
    if(empty($payload['progress_only'])||!empty($payload['disable_connection'])){
        if(is_link($connection))throw new RuntimeException('Connection symlink');$hasConnection=is_dir($connection);
        if($hasConnection){if(fileperms($connection)&0077)throw new RuntimeException('Connection permissions');$gl=$connection.'/operation.lock';if(is_link($gl))throw new RuntimeException('Google lock symlink');$googleLock=fopen($gl,'c+');if(!$googleLock||!chmod($gl,0600)||!flock($googleLock,LOCK_EX|LOCK_NB))throw new RuntimeException('Google request busy');}
    }
    $phase='backup';$backup='rentmor-control-private/remove-gsc-backup-'.bin2hex(random_bytes(12));if(!mkdir($backup,0700))throw new RuntimeException('Backup directory');$rows=[];
    foreach($changes as $i=>$row){if($row['before']!==null)rg_put($backup.'/'.$i,$row['before']['data'],0600);$rows[]=['index'=>$i,'path'=>$row['path'],'before_sha'=>$row['before']===null?null:hash('sha256',$row['before']['data']),'before_mode'=>$row['before']['mode']??null,'after_sha'=>$row['after']===null?null:hash('sha256',$row['after'])];}
    rg_put($backup.'/manifest.json',json_encode($rows,JSON_THROW_ON_ERROR),0600);
    $phase='removal';
    try{
        foreach($changes as $row){$current=rg_read($row['path']);if(($current['data']??null)!==($row['before']['data']??null))throw new RuntimeException('Concurrent edit');if($row['after']===null){if(!unlink($row['path']))throw new RuntimeException('Remove helper');}else rg_put($row['path'],$row['after'],$row['mode']);}
        // Move the whole credential/report directory without reading any of its contents.
        if($hasConnection&&!rename($connection,$backup.'/google-connection-disabled'))throw new RuntimeException('Disable connection');
        $hashes=[];foreach($changes as $row){$read=rg_read($row['path']);if($row['after']===null){if($read!==null)throw new RuntimeException('Helper remains');$hashes[basename($row['path'])]=null;}else{if($read===null||$read['data']!==$row['after']||$read['mode']!==$row['mode'])throw new RuntimeException('Final verification');$hashes[basename($row['path'])]=hash('sha256',$read['data']);}}
        if($hasConnection&&(file_exists($connection)||!is_dir($backup.'/google-connection-disabled')))throw new RuntimeException('Connection still active');
    }catch(Throwable $e){rg_restore($backup);throw new RuntimeException('Removal reverted after failure');}
    echo 'REMOVE_GSC_RESULT '.json_encode(['backup'=>$backup,'sha256'=>$hashes,'connection_disabled'=>$hasConnection],JSON_THROW_ON_ERROR)."\n";
}catch(Throwable $e){echo 'REMOVE_GSC_STOPPED '.$phase.' '.get_class($e)."\n";exit(1);}
finally{foreach([$googleLock,$lock]as $fp)if(is_resource($fp)){flock($fp,LOCK_UN);fclose($fp);}if(isset($payload['staged_apk'])&&preg_match('~^rentmor-control-private/remove-gsc-stage-[a-f0-9]{24}\.apk$~D',$payload['staged_apk'])&&is_file($payload['staged_apk']))unlink($payload['staged_apk']);}
