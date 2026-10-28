<?php
declare(strict_types=1);
require __DIR__.'/../server/notebook_sync.php';
function check(bool $okay,string $reason): void {
    if (!$okay) {fwrite(STDERR,$reason."\n");exit(1);}
}
$id='11111111-1111-4111-a111-111111111111';
$op1='22222222-2222-4222-a222-222222222222';
$op2='33333333-3333-4333-a333-333333333333';
$note=['id'=>$id,'op_id'=>$op1,'base_revision'=>0,'text'=>'Wydrukować umowy',
       'category'=>'Umowy','done'=>false,'deleted'=>false];
$data=rm_notebook_initial();
$results=rm_notebook_apply($data,[$note]);
check($results[0]['ok']&&$results[0]['revision']===1,'initial save');
check(count($data['notes'])===1,'initial persistence');
$replay=rm_notebook_apply($data,[$note]);
check($replay[0]['ok']&&$replay[0]['revision']===1 && $data['revision']===1,'idempotent replay');
$stale=$note;$stale['op_id']=$op2;
$conflict=rm_notebook_apply($data,[$stale]);
check(!$conflict[0]['ok']&&$conflict[0]['conflict']===true,'stale write detection');
$stale['base_revision']=1;$stale['deleted']=true;
$deleted=rm_notebook_apply($data,[$stale]);
check($deleted[0]['ok']&&$data['notes'][$id]['deleted'],'tombstone');
$again=rm_notebook_apply($data,[$note]);
check($again[0]['revision']===1 && $data['notes'][$id]['deleted'],'replayed stale edit resurrected deletion');
$path=sys_get_temp_dir().'/rentmor-notebook-test-'.bin2hex(random_bytes(8)).'.json';
try {
    rm_notebook_write($path,$data);
    check(rm_notebook_read($path)===$data,'round-trip backup');
    file_put_contents($path,'{broken json');
    try {rm_notebook_read($path);throw new RuntimeException('Invalid JSON accepted');}
    catch (RuntimeException $e) {check($e->getMessage()!=='Invalid JSON accepted','corrupt content rejected');}
} finally { @unlink($path); }
echo "Private notebook API: revisions, idempotency, conflicts and tombstones passed.\n";
