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
echo "Rental rules passed\n";
