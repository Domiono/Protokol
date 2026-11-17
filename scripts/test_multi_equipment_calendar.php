<?php
declare(strict_types=1);
require dirname(__DIR__).'/server/rental_inventory.php';
require dirname(__DIR__).'/server/booking_schedule.php';
require dirname(__DIR__).'/server/booking_calendar_rules.php';
$r=['id'=>'group','equipment'=>'Bruzdownica','asset_id'=>'w','start_date'=>'2030-01-17','end_date'=>'2030-01-18','time'=>'08:30',
    'additional_items'=>[['line_id'=>'extra','equipment'=>'Wiertnica','asset_id'=>'d']]];
$assets=[['id'=>'w','equipment'=>'Bruzdownica'],['id'=>'d','equipment'=>'Wiertnica']];
$schedule=['weekly'=>array_fill(1,7,'07:00-18:00'),'dates'=>[]];
$cat=['products'=>[['name'=>'Wiertnica']]];
$in=['equipment'=>'Wiertnica','month'=>'2030-01','rental_days'=>'1'];
$now=new DateTimeImmutable('2030-01-01',new DateTimeZone('Europe/Warsaw'));
$cal=rm_calendar_month($in,$schedule,$cat,[$r],$assets,$now);
$days=array_column($cal['days'],null,'date');
if($days['2030-01-17']['status']!=='busy'||!in_array('10:00',$days['2030-01-18']['slots'],true)||in_array('09:45',$days['2030-01-18']['slots'],true))throw new RuntimeException('Additional item calendar and 90-minute buffer');
$before=$r;$op=$r;$op['additional_items'][0]['start_date']='2099-01-01';
if(rm_prepare_additional_items($r,$op,null,$assets,'admin')!==''||isset($r['additional_items'][0]['start_date']))throw new RuntimeException('Nested field allowlist');
if(rm_rental_items($r)[1]['start_date']!==$before['start_date'])throw new RuntimeException('Parent dates inherited');
echo "Multi-equipment calendar, buffer and field isolation passed\n";
