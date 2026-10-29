<?php
require_once __DIR__.'/../server/admin_auth.php';
function ok($v) {if(!$v)throw new RuntimeException('Failed admin auth test');}
$s='GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ';
ok(rm_totp_at($s,1)==='287082');
ok(rm_totp_verify($s,'287082',59));
ok(!rm_totp_verify($s,'287083',59));
ok(!rm_totp_verify($s,'287082',150));
ok(strlen(rm_totp_secret())===32);
$f=sys_get_temp_dir().'/rentmor-auth-'.bin2hex(random_bytes(8));
file_put_contents($f,json_encode(['password_hash'=>password_hash('fixture',PASSWORD_BCRYPT),'totp_secret'=>'']));
ok(rm_auth_read($f)['totp_secret']==='');
ok(rm_auth_enroll($s,$f));
ok(!rm_auth_enroll($s,$f));
ok(rm_auth_read($f)['totp_secret']===$s);
file_put_contents($f,'{}');
try {rm_auth_read($f);throw new Exception('invalid accepted');}catch(RuntimeException $e){ok($e->getMessage()==='Admin config invalid');}
$limit=sys_get_temp_dir().'/rentmor-otp-limit-'.bin2hex(random_bytes(8));
ok(!rm_otp_limit(false,$limit));
ok(!rm_otp_limit(false,$limit));
ok(!rm_otp_limit(false,$limit));
ok(!rm_otp_limit(false,$limit));
ok(!rm_otp_limit(false,$limit));
ok(!rm_otp_limit(true,$limit)); // Correct code is blocked after five failed attempts.
@unlink($limit);
file_put_contents($f,json_encode(['password_hash'=>password_hash('current-test-passphrase',PASSWORD_BCRYPT),'totp_secret'=>$s]));
$currentCode=rm_totp_at($s,intdiv(time(),30));
ok(!rm_auth_rotate_password('wrong','another-test-passphrase',$currentCode,$f));
ok(!rm_auth_rotate_password('current-test-passphrase','short',$currentCode,$f));
ok(!rm_auth_rotate_password('current-test-passphrase','another-test-passphrase','000000',$f));
ok(rm_auth_rotate_password('current-test-passphrase','another-test-passphrase',$currentCode,$f));
ok(password_verify('another-test-passphrase',rm_auth_read($f)['password_hash']));
ok(rm_auth_read($f)['totp_secret']===$s);
@unlink($f);@unlink($f.'.lock');
echo "PASS private auth, mandatory TOTP and fail closed\n";
