<?php
// Test the exact pure summary helpers without executing the admin/session endpoint.
$source=file_get_contents(__DIR__.'/../server/admin.php');
$start=strpos($source,'function rm_admin_settlement_month(');
$end=strpos($source,'function rm_admin_phone_key(',$start);
if($start===false||$end===false)throw new RuntimeException('Admin statistics helpers not found');
eval(substr($source,$start,$end-$start));
function verify_stat(bool $ok,string $name): void {if(!$ok)throw new RuntimeException($name);}
$rentals=[
 ['settled'=>true,'month'=>'2026-09','start_date'=>'2026-09-30','settled_at'=>'2026-10-02 12:00:00','equipment'=>'Karcher Puzzi','income'=>150.25,'loss'=>10],
 ['settled'=>true,'month'=>'2026-10','start_date'=>'2026-10-01','settled_at'=>'2026-10-03 14:12:00','equipment'=>'Karcher Puzzi','income'=>80,'loss'=>0],
 ['settled'=>false,'month'=>'2026-10','start_date'=>'2026-10-02','equipment'=>'Karcher Puzzi'],
 ['settled'=>true,'month'=>'2026-10','start_date'=>'2026-10-01','equipment'=>'Karcher Puzzi','income'=>90,'loss'=>0]
];
$october=array_values(array_filter($rentals,fn($r)=>rm_admin_settlement_month($r)==='2026-10'));
verify_stat(count($october)===2,'Only October settlements should count');
verify_stat(abs(array_sum(array_column($october,'income'))-230.25)<0.001,'October revenue');
verify_stat(rm_admin_settlement_month($rentals[3])==='','Undated settlements must not be invented');
verify_stat(rm_admin_settlement_month(['settled'=>true,'settled_at'=>'2026-02-31'])==='','Reject invalid date');
verify_stat(rm_admin_equipment_name(['equipment'=>' Karcher  Puzzi - wynajem Kraków '])==='Karcher Puzzi','Clean equipment labels');
verify_stat(rm_admin_equipment_name(['text'=>'2026-10-01 – 2026-10-03 • Puzzi • 500500500'])==='Puzzi','Recognize modern reservation text');
echo "Admin rental statistics tests passed\n";
