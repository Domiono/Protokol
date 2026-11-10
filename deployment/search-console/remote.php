<?php
declare(strict_types=1);
// Executed only over the owner-authorized, host-key-pinned SSH stdin; never installed publicly.
$payload=json_decode(base64_decode('__GSC_PAYLOAD__',true),true,512,JSON_THROW_ON_ERROR);
function gsc_deploy_read(string $path):?array {
    if(is_link($path))throw new RuntimeException('Symlink');
    if(!file_exists($path))return null;
    if(!is_file($path)||filesize($path)>2000000)throw new RuntimeException('Unsafe source');
    $data=file_get_contents($path);
    if($data===false)throw new RuntimeException('Read failed');
    return ['data'=>$data,'mode'=>fileperms($path)&0777];
}
function gsc_deploy_put(string $path,string $data,int $mode):void {
    $tmp='rentmor-control-private/gsc-upload-'.bin2hex(random_bytes(12));
    try {
        if(file_put_contents($tmp,$data,LOCK_EX)!==strlen($data)||!chmod($tmp,$mode)||file_get_contents($tmp)!==$data||!rename($tmp,$path))throw new RuntimeException('Atomic write');
        $read=gsc_deploy_read($path);
        if($read===null||$read['data']!==$data||$read['mode']!==$mode)throw new RuntimeException('Readback');
    }finally{if(is_file($tmp))unlink($tmp);}
}
function gsc_deploy_rollback(string $backup):void {
    if(!preg_match('~^rentmor-control-private/search-console-ssh-[a-f0-9]{24}$~D',$backup))throw new RuntimeException('Invalid backup');
    $manifest=json_decode((string)file_get_contents($backup.'/manifest.json'),true,512,JSON_THROW_ON_ERROR);
    // Preflight the entire rollback before restoring any file.
    foreach($manifest as $row){
        $current=gsc_deploy_read($row['path']);$hash=$current===null?null:hash('sha256',$current['data']);
        if($hash!==$row['before_sha']&&$hash!==$row['after_sha'])throw new RuntimeException('Concurrent change blocks rollback');
    }
    foreach(array_reverse($manifest) as $i=>$row){
        $current=gsc_deploy_read($row['path']);
        if(($current===null?null:hash('sha256',$current['data']))===$row['before_sha'])continue;
        if($current===null||hash('sha256',$current['data'])!==$row['after_sha'])throw new RuntimeException('Concurrent change blocks rollback');
        if($row['before_sha']===null){if(!unlink($row['path']))throw new RuntimeException('Rollback unlink');}
        else {
            $before=(string)file_get_contents($backup.'/'.$row['index']);
            if(hash('sha256',$before)!==$row['before_sha'])throw new RuntimeException('Backup mismatch');
            gsc_deploy_put($row['path'],$before,$row['before_mode']);
        }
    }
}
$stage='preflight';$lock=null;
try {
    umask(0077);
    foreach(['rentmor-control-private','rentmor-control-private/progress'] as $dir){
        if(is_link($dir)||!is_dir($dir)||(fileperms($dir)&0077))throw new RuntimeException('Private directory');
    }
    $lockpath='rentmor-control-private/search-console-ssh.lock';
    if(is_link($lockpath))throw new RuntimeException('Lock symlink');
    $lock=fopen($lockpath,'c+');if(!$lock||!chmod($lockpath,0600)||!flock($lock,LOCK_EX|LOCK_NB))throw new RuntimeException('Publication busy');
    if(($payload['mode']??'')==='rollback'){
        gsc_deploy_rollback($payload['backup']);echo 'GSC_SSH_ROLLBACK_OK'."\n";exit;
    }
    if(($payload['mode']??'')!=='publish')throw new RuntimeException('Invalid mode');
    $changes=[];$stage='progress-baseline';
    foreach(['CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md'] as $name){
        $path='rentmor-control-private/progress/'.$name;$old=gsc_deploy_read($path);
        if($old===null||($old['mode']&0077)||sha1('blob '.strlen($old['data'])."\0".$old['data'])!==$payload['docs'][$name]['before_blob'])throw new RuntimeException('Private progress differs');
        $new=base64_decode($payload['docs'][$name]['content'],true);
        if($new===false)throw new RuntimeException('Invalid payload');
        $changes[]=['path'=>$path,'before'=>$old,'after'=>$new,'mode'=>0600];
    }
    if(!empty($payload['progress_only'])){
        $stage='progress-only';
    }else{
        $stage='source-preflight';$helpers=[];
        foreach(['search_console.php','search_console_admin.php'] as $name){
            $path='public_html/'.$name;$before=gsc_deploy_read($path);$new=base64_decode($payload['helpers'][$name],true);
            if($new===false||($before!==null&&$before['data']!==$new))throw new RuntimeException('Existing helper differs');
            token_get_all($new,TOKEN_PARSE);
            $helpers[]=['path'=>$path,'before'=>$before,'after'=>$new,'mode'=>0644];
        }
        $path='public_html/admin.php';$before=gsc_deploy_read($path);
        if($before===null)throw new RuntimeException('Panel missing');
        if(hash('sha256',$before['data'])!==$payload['admin_before_sha'])throw new RuntimeException('Panel changed since read-only inspection');
        $new=$before['data'];
        foreach($payload['hooks'] as $index=>$hook){
            [$anchor,$insertion,$marker]=$hook;
            if(substr_count($new,$anchor)!==1||substr_count($new,$marker)>1)throw new RuntimeException('Unknown panel template');
            if(!str_contains($new,$marker))$new=str_replace($anchor,$index===0?$anchor.$insertion:$insertion.$anchor,$new);
        }
        token_get_all($new,TOKEN_PARSE);
        if(!function_exists('curl_init'))throw new RuntimeException('Hosting cURL missing');
        $changes=array_merge($helpers,[['path'=>$path,'before'=>$before,'after'=>$new,'mode'=>$before['mode']]],$changes);
    }
    $stage='backup';$backup='rentmor-control-private/search-console-ssh-'.bin2hex(random_bytes(12));
    if(!mkdir($backup,0700))throw new RuntimeException('Backup directory');
    $manifest=[];
    foreach($changes as $i=>$row){
        if($row['before']!==null)gsc_deploy_put($backup.'/'.$i,$row['before']['data'],0600);
        $manifest[]=['index'=>$i,'path'=>$row['path'],'before_mode'=>$row['before']['mode']??null,
            'before_sha'=>$row['before']===null?null:hash('sha256',$row['before']['data']),'after_sha'=>hash('sha256',$row['after'])];
    }
    gsc_deploy_put($backup.'/manifest.json',json_encode($manifest,JSON_THROW_ON_ERROR),0600);
    $stage='publication';
    try {
        foreach($changes as $row){
            $current=gsc_deploy_read($row['path']);
            if(($current['data']??null)!==($row['before']['data']??null))throw new RuntimeException('Concurrent edit');
            gsc_deploy_put($row['path'],$row['after'],$row['mode']);
        }
        $hashes=[];
        foreach($changes as $row){$read=gsc_deploy_read($row['path']);if($read===null||$read['data']!==$row['after']||$read['mode']!==$row['mode'])throw new RuntimeException('Final verification');$hashes[basename($row['path'])]=hash('sha256',$read['data']);}
    }catch(Throwable $e){gsc_deploy_rollback($backup);throw new RuntimeException('Publication rolled back');}
    echo 'GSC_SSH_RESULT '.json_encode(['backup'=>$backup,'sha256'=>$hashes],JSON_THROW_ON_ERROR)."\n";
}catch(Throwable $e){echo 'GSC_SSH_STOPPED '.$stage.' '.get_class($e)."\n";exit(1);}
finally{if(is_resource($lock)){flock($lock,LOCK_UN);fclose($lock);}}
