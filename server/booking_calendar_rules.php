<?php
declare(strict_types=1);
/** Pure availability projection. No contacts, copy IDs or reservation details leave this function. */
function rm_calendar_month(array $in,array $schedule,array $catalogue,array $items,array $assets,?DateTimeImmutable $now=null): array {
    $tz=new DateTimeZone('Europe/Warsaw');$now=($now??new DateTimeImmutable('now',$tz))->setTimezone($tz);
    $month=$in['month']??null;$name=$in['equipment']??null;$days=$in['rental_days']??null;
    if(!is_string($month)||!preg_match('/^\d{4}-\d{2}$/D',$month)||!rm_booking_day($month.'-01')
        ||!is_string($name)||strlen($name)>300||!is_string($days)||!preg_match('/^[1-9]\d{0,2}$/D',$days)||(int)$days>365)
        throw new InvalidArgumentException('Wybierz sprzęt, miesiąc i liczbę dób od 1 do 365.');
    $first=new DateTimeImmutable($month.'-01',$tz);$horizon=$now->modify('+365 days')->format('Y-m-d');
    if($month<$now->format('Y-m')||$month>substr($horizon,0,7))throw new InvalidArgumentException('Wybierz miesiąc w ciągu najbliższego roku.');
    $product=null;foreach($catalogue['products'] as $p)if(($p['name']??null)===$name){$product=$p;break;}
    if($product===null)throw new InvalidArgumentException('Wybierz sprzęt z aktualnego katalogu.');
    // Candidate availability can open at a timed return + buffer, even between quarter-hours.
    $edges=[];
    foreach($items as $r){
        $interval=rm_booking_interval($r);
        if($interval!==null){$edge=$first->setTimestamp($interval[1]+5400);$edges[$edge->format('Y-m-d')][]=$edge->format('H:i');}
    }
    $nextMinute=$now->setTimestamp((int)(floor($now->getTimestamp()/60)+1)*60);
    $edges[$nextMinute->format('Y-m-d')][]=$nextMinute->format('H:i');
    $result=[];
    for($i=0;$i<(int)$first->format('t');$i++){
        $date=$first->modify('+'.$i.' days')->format('Y-m-d');$slots=[];
        if($date<$now->format('Y-m-d')){$result[]=['date'=>$date,'status'=>'past','slots'=>[]];continue;}
        $windows=$date<=$horizon?rm_booking_windows_for($schedule,$date):[];
        $eligible=false;
        foreach($windows as [$from,$to]){
            $clocks=[$from,$to];$begin=(int)substr($from,0,2)*60+(int)substr($from,3,2);$end=(int)substr($to,0,2)*60+(int)substr($to,3,2);
            for($minute=$begin;$minute<=$end;$minute+=15)$clocks[]=sprintf('%02d:%02d',intdiv($minute,60),$minute%60);
            foreach($edges[$date]??[] as $clock)if($clock>=$from&&$clock<=$to)$clocks[]=$clock;
            foreach(array_unique($clocks) as $clock){
                $start=new DateTimeImmutable($date.' '.$clock,$tz);
                if($start->format('Y-m-d H:i')!==$date.' '.$clock||$start<=$now)continue;
                $eligible=true;
                $candidate=['id'=>'calendar-probe-not-saved','equipment'=>$name,'start_date'=>$date,'time'=>$clock];
                $candidate+=rm_booking_duration(['start_date'=>$date,'time'=>$clock,'rental_days'=>$days]);
                $picked=rm_auto_pick_asset($candidate,$items,$assets);
                if(($picked['error']??'')===''&&!empty($picked['asset']['id']))$slots[$clock]=true;
            }
        }
        $available=array_keys($slots);sort($available,SORT_STRING);
        $result[]=['date'=>$date,'status'=>$available?'available':($eligible?'busy':'closed'),'slots'=>$available];
    }
    return ['month'=>$month,'rental_days'=>(int)$days,'days'=>$result];
}
