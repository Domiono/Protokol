<?php
// Extract pure validation helpers, without loading live endpoint authentication or databases.
$source=file_get_contents(__DIR__.'/../server/protocol_sync.php');
$start=strpos($source,'function rm_rental_conflict(');$end=strpos($source,'$role = rm_device_role',$start);
eval(substr($source,$start,$end-$start));
function check($ok,$name){if(!$ok)throw new RuntimeException($name);}
$a=['id'=>'a','equipment'=>'Puzzi'];$r=['id'=>'r','asset_id'=>'a','equipment'=>'Puzzi','start_date'=>'2030-10-30','end_date'=>'2030-11-02'];
check(rm_rental_conflict($r,[],[$a])==='','free');
$other=$r;$other['id']='other';check(rm_rental_conflict($r,[$other],[$a])!=='','overlap');
check(rm_rental_conflict($r,[$r],[$a])==='','ignore self');
$a['in_service']=true;check(rm_rental_conflict($r,[],[$a])!=='','service');
$r['returned']=true;check(rm_rental_conflict($r,[],[$a])==='','returned');
$private=rm_hide_rental_finance(['daily_rate'=>2,'paid'=>3,'deposit'=>4,'settlement'=>[],'equipment'=>'Puzzi']);check(count($private)===1,'role privacy');
$assets=[['id'=>'a','label'=>'Puzzi 01','equipment'=>'Puzzi'],['id'=>'b','label'=>'Puzzi 02','equipment'=>'Puzzi']];
$other=['id'=>'r1','equipment'=>'Puzzi','asset_id'=>'a','start_date'=>'2030-10-25','end_date'=>'2030-10-27'];
$candidate=['id'=>'r2','equipment'=>'Puzzi','start_date'=>'2030-10-26','end_date'=>'2030-10-28'];
$auto=rm_auto_pick_asset($candidate,[$other],$assets);check(($auto['asset']['id']??null)==='b','auto chooses free copy');
$assets[1]['in_service']=true;
$auto=rm_auto_pick_asset($candidate,[$other],$assets);check($auto['error']!=='','busy and service blocks');
unset($assets[1]['in_service']);
$auto=rm_auto_pick_asset($candidate,[['id'=>'legacy','equipment'=>'Puzzi','start_date'=>'2030-10-26','end_date'=>'2030-10-27']],$assets);
check($auto['error']!=='','legacy unassigned booking blocks auto');
$auto=rm_auto_pick_asset($candidate,[],[]);check($auto['asset']===null&&$auto['error']==='','no registered copies remains unassigned');
// Two physical devices per rental set: three of each, shared with solo offers.
$shared=[];
for($i=1;$i<=3;$i++){
    $shared[]=['id'=>'g'.$i,'label'=>'Żyrafa '.$i,'equipment'=>'Szlifierka żyrafa Stanley','asset_kind'=>'giraffe'];
    $shared[]=['id'=>'v'.$i,'label'=>'Odkurzacz '.$i,'equipment'=>'Odkurzacz przemysłowy Bosch','asset_kind'=>'vacuum'];
}
check(rm_equipment_kind('Żyrafa + odkurzacz')==='giraffe','uppercase Polish label');
check(rm_needs_vacuum(['equipment'=>'Żyrafa + odkurzacz']),'old set inference');
$vac=['id'=>'v-book','equipment'=>'Odkurzacz przemysłowy Metabo','start_date'=>'2030-10-25','end_date'=>'2030-10-27'];
$v=rm_auto_pick_asset($vac,[],$shared);
check(($v['asset']['id']??'')==='v1'&&$v['vacuum']===null,'solo vacuum uses shared pool');
$vac['asset_id']='v1';
$set1=['id'=>'set1','equipment'=>'Żyrafa + odkurzacz','start_date'=>'2030-10-26','end_date'=>'2030-10-27'];
$assigned=rm_auto_pick_asset($set1,[$vac],$shared);
check(($assigned['asset']['id']??'')==='g1'&&($assigned['vacuum']['id']??'')==='v2','first set reserves giraffe and second vacuum');
$set1['asset_id']='g1';$set1['vacuum_asset_id']='v2';
$set2=['id'=>'set2','equipment'=>'Żyrafa Stanley','needs_vacuum'=>true,'start_date'=>'2030-10-26','end_date'=>'2030-10-27'];
$assigned=rm_auto_pick_asset($set2,[$vac,$set1],$shared);
check(($assigned['asset']['id']??'')==='g2'&&($assigned['vacuum']['id']??'')==='v3','second set uses third vacuum');
$set2['asset_id']='g2';$set2['vacuum_asset_id']='v3';
$set3=['id'=>'set3','equipment'=>'Żyrafa + odkurzacz','start_date'=>'2030-10-27','end_date'=>'2030-10-28'];
check(rm_auto_pick_asset($set3,[$vac,$set1,$set2],$shared)['error']!=='','no fourth set without fourth vacuum');
$soloG=['id'=>'g-only','equipment'=>'Żyrafa Stanley','needs_vacuum'=>false,'start_date'=>'2030-10-27','end_date'=>'2030-10-28'];
check((rm_auto_pick_asset($soloG,[$vac,$set1,$set2],$shared)['asset']['id']??'')==='g3','solo giraffe still available');
$vac['returned']=true;
check((rm_auto_pick_asset($set3,[$vac,$set1,$set2],$shared)['vacuum']['id']??'')==='v1','returned vacuum can be reused');
$legacy=['id'=>'old','equipment'=>'Żyrafa + odkurzacz','asset_id'=>'g1','start_date'=>'2030-10-25','end_date'=>'2030-10-27'];
check(rm_auto_pick_asset($soloG,[$legacy],$shared)['error']!=='','unassigned old giraffe blocked');
$vacSolo=['id'=>'vac2','equipment'=>'Odkurzacz przemysłowy','start_date'=>'2030-10-26','end_date'=>'2030-10-27'];
check(rm_auto_pick_asset($vacSolo,[$legacy],$shared)['error']!=='','old bundle without vacuum blocked');
$legacy['vacuum_asset_id']='v1';
check((rm_auto_pick_asset($vacSolo,[$legacy],$shared)['asset']['id']??'')==='v2','reconciled bundle releases others');
check(rm_rental_conflict($set1,[$vac],$shared)==='','valid dual reservation');
check(rm_rental_conflict($set2,[$set1],$shared)==='','different copies can share dates');
check(rm_rental_conflict($set3+['asset_id'=>'g3','vacuum_asset_id'=>'v2'],[$set1],$shared)!=='','reserved auxiliary vacuum conflicts');
echo "Rental rules passed\n";
