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
@unlink($f);@unlink($f.'.lock');
echo "PASS private auth, mandatory TOTP and fail closed\n";
