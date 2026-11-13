<?php
declare(strict_types=1);
// CLI-only, all fixtures. Never reads production reservations or inventory.
if(PHP_SAPI!=='cli'){http_response_code(404);exit;}
$source=dirname(__DIR__).'/deployment/booking-calendar/baseline';
$rules=dirname(__DIR__).'/server/booking_calendar_rules.php';
$tmp=sys_get_temp_dir().'/rentmor-calendar-'.bin2hex(random_bytes(8));mkdir($tmp,0700);
try{
    foreach(['booking_schedule.php','rental_inventory.php'] as $name)copy($source.'/'.$name,$tmp.'/'.$name);
    copy($rules,$tmp.'/booking_calendar_rules.php');
    file_put_contents($tmp.'/private_rental_files.php','<?php function rm_rental_file(string $name): string { throw new RuntimeException("No real storage in fixture tests"); }');
    require $tmp.'/rental_inventory.php';require $tmp.'/booking_calendar_rules.php';
    $checks=0;
    function check(bool $ok,string $label):void {global $checks;$checks++;if(!$ok)throw new RuntimeException($label);}
    $schedule=['weekly'=>array_fill(1,7,'12:00-18:00'),'dates'=>[]];
    $cat=['products'=>[['name'=>'Fixture'],['name'=>'Zestaw żyrafa z odkurzaczem'],['name'=>'Odkurzacz przemysłowy']]];
    $assets=[];foreach(range(1,3) as $n)$assets[]=['id'=>'p'.$n,'label'=>'Fixture '.$n,'equipment'=>'Fixture','asset_kind'=>'standard'];
    $now=new DateTimeImmutable('2030-01-01 08:00',new DateTimeZone('Europe/Warsaw'));
    $in=['equipment'=>'Fixture','month'=>'2030-01','rental_days'=>'1'];
    function row(array $result,string $date):array {foreach($result['days'] as $day)if($day['date']===$date)return $day;throw new RuntimeException('Missing date');}
    $calc=fn($reservations,$copies,$request=null,$hours=null)=>rm_calendar_month($request??$in,$hours??$schedule,$cat,$reservations,$copies,$now);
    $r=['id'=>'r1','equipment'=>'Fixture','asset_id'=>'p1','start_date'=>'2030-01-10','time'=>'12:00','end_date'=>'2030-01-11','return_time'=>'18:00'];
    check(count($calc([],$assets)['days'])===31,'Month length');
    check(row($calc([$r],$assets),'2030-01-10')['status']==='available','One of three booked must leave capacity');
    check(row($calc([$r],[$assets[0]]),'2030-01-10')['status']==='busy','One copy fully booked');
    $all=[];foreach(range(1,3) as $n)$all[]=array_merge($r,['id'=>'r'.$n,'asset_id'=>'p'.$n]);
    check(row($calc($all,$assets),'2030-01-10')['status']==='busy','All three booked');
    $returned=$r;$returned['returned']=true;
    check(row($calc([$returned],[$assets[0]]),'2030-01-10')['status']==='available','Returned copy released');
    $settled=$r;$settled['settled']=true;
    check(row($calc([$settled],[$assets[0]]),'2030-01-10')['status']==='available','Settled copy released');
    $service=$assets[0];$service['in_service']=true;
    check(row($calc([],[$service]),'2030-01-10')['status']==='busy','Service copy blocked');
    check(row($calc([],[]),'2030-01-10')['status']==='busy','Missing registered copy blocked');
    $missing=$r;unset($missing['asset_id']);
    check(row($calc([$missing],$assets),'2030-01-10')['status']==='busy','Unassigned legacy reservation is conservative');
    $legacy=$r;unset($legacy['time'],$legacy['return_time']);
    check(row($calc([$legacy],[$assets[0]]),'2030-01-11')['status']==='busy','Legacy boundary date remains blocked');
    $boundary=array_merge($r,['start_date'=>'2030-01-09','end_date'=>'2030-01-10','return_time'=>'13:00']);
    $slots=row($calc([$boundary],[$assets[0]]),'2030-01-10')['slots'];
    check(in_array('14:30',$slots,true)&&!in_array('14:15',$slots,true),'90-minute buffer');
    $odd=array_merge($boundary,['return_time'=>'10:37']);$hours=$schedule;$hours['dates']['2030-01-10']='12:00-12:14';
    check(in_array('12:07',row($calc([$odd],[$assets[0]],null,$hours),'2030-01-10')['slots'],true),'Exact non-quarter-hour release');
    $long=$in;$long['rental_days']='3';$a=array_merge($r,['start_date'=>'2030-01-12','end_date'=>'2030-01-13']);$b=array_merge($r,['id'=>'r2','asset_id'=>'p2']);
    check(row($calc([$a,$b],array_slice($assets,0,2),$long),'2030-01-10')['status']==='busy','Same physical copy must span the whole rental');
    $hours=$schedule;$hours['dates']['2030-01-10']='';
    check(row($calc([],$assets,null,$hours),'2030-01-10')['status']==='closed','Date override closes pickup');
    $kitAssets=[];foreach(range(1,3) as $n){$kitAssets[]=['id'=>'g'.$n,'equipment'=>'Żyrafa','asset_kind'=>'giraffe'];$kitAssets[]=['id'=>'v'.$n,'equipment'=>'Odkurzacz przemysłowy','asset_kind'=>'vacuum'];}
    $kit=$in;$kit['equipment']='Zestaw żyrafa z odkurzaczem';$vac=$in;$vac['equipment']='Odkurzacz przemysłowy';
    $vacRows=[];foreach(range(1,3) as $n)$vacRows[]=array_merge($r,['id'=>'v-r'.$n,'equipment'=>'Odkurzacz przemysłowy','asset_id'=>'v'.$n]);
    check(row($calc(array_slice($vacRows,0,1),$kitAssets,$kit),'2030-01-10')['status']==='available','Standalone vacuum leaves two bundles');
    check(row($calc($vacRows,$kitAssets,$kit),'2030-01-10')['status']==='busy','No vacuum means no bundle');
    $kitRows=[];foreach(range(1,3) as $n)$kitRows[]=array_merge($r,['id'=>'kit'.$n,'equipment'=>$kit['equipment'],'asset_id'=>'g'.$n,'vacuum_asset_id'=>'v'.$n]);
    check(row($calc($kitRows,$kitAssets,$vac),'2030-01-10')['status']==='busy','Bundle blocks standalone vacuum');
    $overdue=array_merge($r,['done'=>true,'start_date'=>'2000-01-01','end_date'=>'2000-01-02']);
    check(row($calc([$overdue],[$assets[0]]),'2030-01-10')['status']==='busy','Overdue issued copy remains blocked');
    $dstNow=new DateTimeImmutable('2027-03-01',$now->getTimezone());$dst=rm_calendar_month(['equipment'=>'Fixture','month'=>'2027-03','rental_days'=>'1'],$schedule,$cat,[],$assets,$dstNow);
    check(row($dst,'2027-03-28')['status']==='available','DST month accepted');
    // Android rows have pickup time but no return_time: the same clock on end_date
    // is the expected return. A 90-minute buffer makes 18 January open at 10:00.
    $mobile=['id'=>'mobile','equipment'=>'Fixture','asset_id'=>'p1','start_date'=>'2030-01-17',
        'end_date'=>'2030-01-18','time'=>'08:30'];
    $earlySchedule=['weekly'=>array_fill(1,7,'07:00-18:00'),'dates'=>[]];
    $mobileMonth=$calc([$mobile],[$assets[0]],null,$earlySchedule);
    $afterReturn=row($mobileMonth,'2030-01-18');
    check($afterReturn['status']==='available','Timed mobile booking releases return date');
    check(in_array('10:00',$afterReturn['slots'],true),'Pickup allowed at return plus 90 minutes');
    check(!in_array('09:45',$afterReturn['slots'],true),'Pickup blocked before buffer ends');
    check(row($mobileMonth,'2030-01-17')['status']==='busy','Rental start stays blocked');
    check(in_array('07:00',row($mobileMonth,'2030-01-16')['slots'],true),
        'Previous day can finish before next pickup with buffer');
    check(row($calc([$mobile],[$assets[0]]),'2030-01-16')['status']==='busy',
        'Previous day cannot start a 24-hour rental during 12-18 pickup hours');
    $noClock=$mobile;unset($noClock['time']);
    check(row($calc([$noClock],[$assets[0]],null,$earlySchedule),'2030-01-18')['status']==='busy',
        'Legacy date-only return remains conservatively blocked');
    $explicit=$mobile;$explicit['return_time']='09:15';
    $explicitSlots=row($calc([$explicit],[$assets[0]],null,$earlySchedule),'2030-01-18')['slots'];
    check(in_array('10:45',$explicitSlots,true)&&!in_array('10:30',$explicitSlots,true),
        'Explicit return time overrides inferred Android return time');
    $raw=json_encode($calc($all,$assets));check(!str_contains($raw,'asset_id')&&!str_contains($raw,'customer')&&!str_contains($raw,'r1'),'Public payload excludes private details');
    foreach([['rental_days'=>'0'],['rental_days'=>[]],['equipment'=>'Unknown'],['month'=>'2030-99'],['month'=>'2032-01']] as $bad){$caught=false;try{$calc([],$assets,array_merge($in,$bad));}catch(InvalidArgumentException $e){$caught=true;}check($caught,'Invalid input rejected');}
    echo 'PASS '.$checks." calendar fixture checks\n";
}finally{foreach(glob($tmp.'/*')?:[] as $file)unlink($file);rmdir($tmp);}
