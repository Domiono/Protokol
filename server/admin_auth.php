<?php
declare(strict_types=1);
/** Admin credentials and audit never belong to public_html. */
function rm_auth_path(): string {
    // A disposable directory is permitted for isolated CLI-server regression tests only.
    $fixture=PHP_SAPI==='cli-server' ? getenv('RENTMOR_TEST_PRIVATE_DIR') : false;
    return ($fixture!==false && $fixture!=='' ? $fixture : dirname(__DIR__).'/rentmor-private').'/admin-security.json';
}
function rm_auth_read(?string $file=null): array {
    $file=$file??rm_auth_path();
    if (!is_file($file) || !is_readable($file)) throw new RuntimeException('Admin config missing');
    $raw=@file_get_contents($file);
    $data=is_string($raw)?json_decode($raw,true):null;
    if (!is_array($data) || !is_string($data['password_hash']??null) ||
        !preg_match('/^\$2[aby]\$\d{2}\$[.\/A-Za-z0-9]{53}$/D',$data['password_hash']) ||
        !is_string($data['totp_secret']??null) ||
        ($data['totp_secret']!=='' && !preg_match('/^[A-Z2-7]{32}$/D',$data['totp_secret'])))
        throw new RuntimeException('Admin config invalid');
    return $data;
}
function rm_auth_enroll(string $secret,?string $file=null): bool {
    $file=$file??rm_auth_path();
    if (!preg_match('/^[A-Z2-7]{32}$/D',$secret))return false;
    $lock=@fopen($file.'.lock','c');
    if (!$lock || !flock($lock,LOCK_EX)) {if($lock)fclose($lock);return false;}
    try {
        $auth=rm_auth_read($file);
        if ($auth['totp_secret']!=='')return false;
        $auth['totp_secret']=$secret;
        $json=json_encode($auth,JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR);
        $temp=$file.'.'.bin2hex(random_bytes(8)).'.tmp';
        if (@file_put_contents($temp,$json,LOCK_EX)!==strlen($json)) {@unlink($temp);return false;}
        @chmod($temp,0600);
        if (!@rename($temp,$file)) {@unlink($temp);return false;}
        return true;
    } finally {flock($lock,LOCK_UN);fclose($lock);}
}
function rm_totp_secret(): string {
    $abc='ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';$bits='';
    foreach(unpack('C*',random_bytes(20)) as $n)$bits.=str_pad(decbin($n),8,'0',STR_PAD_LEFT);
    $out='';for($i=0;$i<160;$i+=5)$out.=$abc[bindec(substr($bits,$i,5))];
    return $out;
}
function rm_totp_at(string $secret,int $step): string {
    if (!preg_match('/^[A-Z2-7]{32}$/D',$secret) || $step<0)return '';
    $abc='ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';$bits='';
    foreach(str_split($secret) as $ch)$bits.=str_pad(decbin(strpos($abc,$ch)),5,'0',STR_PAD_LEFT);
    $key='';for($i=0;$i<160;$i+=8)$key.=chr(bindec(substr($bits,$i,8)));
    $hash=hash_hmac('sha1',pack('N2',intdiv($step,4294967296),$step%4294967296),$key,true);
    $o=ord(substr($hash,-1))&15;
    return str_pad((string)((unpack('N',substr($hash,$o,4))[1]&0x7fffffff)%1000000),6,'0',STR_PAD_LEFT);
}
function rm_totp_verify(string $secret,string $code,?int $when=null): bool {
    if (!preg_match('/^[0-9]{6}$/D',$code))return false;
    $step=intdiv($when??time(),30);
    for($d=-1;$d<=1;$d++)if($step+$d>=0&&hash_equals(rm_totp_at($secret,$step+$d),$code))return true;
    return false;
}
/** IP-based persistent OTP throttle independent of browser sessions or correct passwords. */
function rm_otp_limit(bool $matched, ?string $file=null): bool {
    $dir=sys_get_temp_dir().'/rentmor-admin-otp-'.substr(hash('sha256',__DIR__),0,20);
    if ($file===null && !is_dir($dir) && !@mkdir($dir,0700,true) && !is_dir($dir)) return false;
    $ip=(string)($_SERVER['REMOTE_ADDR']??'unknown');
    $file=$file??$dir.'/'.hash('sha256',$ip).'.json';
    $fp=@fopen($file,'c+');
    if(!$fp || !flock($fp,LOCK_EX)){if($fp)fclose($fp);return false;}
    try {
        @chmod($file,0600);
        $now=time();rewind($fp);
        $state=json_decode((string)stream_get_contents($fp),true);
        if(!is_array($state))$state=[];
        $first=(int)($state['first']??0);
        $failed=(int)($state['failed']??0);
        $blocked=(int)($state['blocked']??0);
        if($first<=0 || $first>$now || $first+900<=$now) {$first=$now;$failed=0;$blocked=0;}
        if($blocked>$now)return false;
        if($matched){$failed=0;$blocked=0;$first=$now;}
        else {++$failed;if($failed>=5)$blocked=$now+900;}
        $json=json_encode(['first'=>$first,'failed'=>$failed,'blocked'=>$blocked],JSON_THROW_ON_ERROR);
        rewind($fp);
        if(fwrite($fp,$json)!==strlen($json) || !ftruncate($fp,strlen($json)) || !fflush($fp))return false;
        return $matched;
    }finally{flock($fp,LOCK_UN);fclose($fp);}
}
/** Rotate the previously published bcrypt credential from the authenticated admin panel. */
function rm_auth_rotate_password(string $oldPassword,string $newPassword,string $code,?string $file=null): bool {
    $file=$file??rm_auth_path();
    $length=strlen($newPassword);
    if ($length<14 || $length>72 || $oldPassword===$newPassword) return false;
    $fp=@fopen($file.'.lock','c');
    if (!$fp || !@flock($fp,LOCK_EX)) {if($fp)fclose($fp);return false;}
    try {
        $data=rm_auth_read($file);
        if ($data['totp_secret']==='' || !password_verify($oldPassword,$data['password_hash'])
            || !rm_totp_verify($data['totp_secret'],$code))return false;
        $data['password_hash']=password_hash($newPassword,PASSWORD_BCRYPT,['cost'=>12]);
        if(!is_string($data['password_hash']))return false;
        $json=json_encode($data,JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR);
        $temp=$file.'.'.bin2hex(random_bytes(8)).'.tmp';
        if(@file_put_contents($temp,$json,LOCK_EX)!==strlen($json)) {@unlink($temp);return false;}
        @chmod($temp,0600);
        if(!@rename($temp,$file)) {@unlink($temp);return false;}
        return password_verify($newPassword,rm_auth_read($file)['password_hash']);
    } finally {flock($fp,LOCK_UN);fclose($fp);}
}
function rm_security_log(string $event): void {
    if(!in_array($event,['password_failed','password_rate_limited','password_ok','otp_failed','otp_ok','otp_enrolled','api_unauthorized','password_changed'],true))return;
    $dir=dirname(rm_auth_path());
    if(!is_dir($dir))return;
    $file=$dir.'/security-events-'.gmdate('Y-m-d').'.log';
    $fp=@fopen($file,'ab');
    if(!$fp)return;
    try {
        if(!flock($fp,LOCK_EX))return;
        @chmod($file,0600);
        if((@fstat($fp)['size']??0)>1048576)return;
        $origin=substr(hash_hmac('sha256',(string)($_SERVER['REMOTE_ADDR']??''),__DIR__),0,12);
        @fwrite($fp,json_encode(['time'=>gmdate('c'),'event'=>$event,'origin'=>$origin],JSON_UNESCAPED_SLASHES)."\n");
    }finally{flock($fp,LOCK_UN);fclose($fp);}
}
