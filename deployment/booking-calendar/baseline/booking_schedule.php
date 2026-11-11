<?php
declare(strict_types=1);
/** Owner handover calendar. All times Europe/Warsaw; empty day = closed. */
function rm_booking_time(string $s): bool { return preg_match('/^(?:[01][0-9]|2[0-3]):[0-5][0-9]$/D',$s)===1; }
function rm_booking_day(string $s): bool {
    $d=DateTimeImmutable::createFromFormat('!Y-m-d',$s,new DateTimeZone('Europe/Warsaw'));
    return $d!==false&&$d->format('Y-m-d')===$s;
}
function rm_booking_windows(string $s): array {
    if(trim($s)==='')return [];
    $out=[];
    foreach(explode(',',$s) as $part){
        $pair=explode('-',str_replace(['–','—','−'],'-',trim($part)));
        $pair=array_map(static function(string $clock): string {
            $clock=trim($clock);
            if(preg_match('/^([0-9]{1,2})(?:[:.]([0-9]{2}))?$/D',$clock,$m))return sprintf('%02d:%02d',(int)$m[1],(int)($m[2]??0));
            return $clock;
        },$pair);
        if(count($pair)!==2||!rm_booking_time($pair[0])||!rm_booking_time($pair[1])||$pair[0]>=$pair[1])throw new InvalidArgumentException('Godziny: np. 12:00-18:00 lub 09:00-12:00,15:00-18:00.');
        $out[]=$pair;
    }
    if(count($out)>4)throw new InvalidArgumentException('Maksymalnie cztery przedziały dziennie.');
    usort($out,fn($a,$b)=>strcmp($a[0],$b[0]));
    for($i=1;$i<count($out);$i++)if($out[$i][0]<=$out[$i-1][1])throw new InvalidArgumentException('Przedziały nie mogą się nakładać.');
    return $out;
}
function rm_booking_schedule_read(string $dir): array {
    $path=$dir.'/handover.json';
    if(is_link($path))throw new RuntimeException('Nieprawidłowa ścieżka godzin.');
    if(!file_exists($path))return ['weekly'=>array_fill(1,7,''),'dates'=>[]];
    if(!is_file($path)||filesize($path)>100000)throw new RuntimeException('Nie można odczytać godzin.');
    $data=json_decode((string)file_get_contents($path),true);
    if(!is_array($data)||!is_array($data['weekly']??null)||!is_array($data['dates']??null)||count($data['dates'])>366)throw new RuntimeException('Uszkodzone ustawienia godzin.');
    foreach(range(1,7) as $i){if(!is_string($data['weekly'][$i]??null))throw new RuntimeException('Brak godzin dnia.');rm_booking_windows($data['weekly'][$i]);}
    foreach($data['dates'] as $day=>$hours){if(!rm_booking_day((string)$day)||!is_string($hours))throw new RuntimeException('Nieprawidłowy wyjątek godzin.');rm_booking_windows($hours);}
    return $data;
}
function rm_booking_windows_for(array $schedule,string $day): array {
    if(!rm_booking_day($day))throw new InvalidArgumentException('Nieprawidłowa data.');
    $n=(int)(new DateTimeImmutable($day,new DateTimeZone('Europe/Warsaw')))->format('N');
    return rm_booking_windows($schedule['dates'][$day]??$schedule['weekly'][$n]);
}
function rm_booking_in_window(array $schedule,string $day,string $time): bool {
    if(!rm_booking_time($time))return false;
    foreach(rm_booking_windows_for($schedule,$day) as [$from,$to])if($time>=$from&&$time<=$to)return true;
    return false;
}
/** Only explicitly timed bookings may share a boundary date; legacy dates stay blocked. */
function rm_booking_interval(array $r): ?array {
    if(!rm_booking_day((string)($r['start_date']??''))||!rm_booking_day((string)($r['end_date']??''))
        ||!rm_booking_time((string)($r['time']??''))||!rm_booking_time((string)($r['return_time']??'')))return null;
    $tz=new DateTimeZone('Europe/Warsaw');
    $start=new DateTimeImmutable($r['start_date'].' '.$r['time'],$tz);
    $end=new DateTimeImmutable($r['end_date'].' '.$r['return_time'],$tz);
    if($start->format('Y-m-d H:i')!==$r['start_date'].' '.$r['time']||$end->format('Y-m-d H:i')!==$r['end_date'].' '.$r['return_time'])return null;
    return $end>$start?[$start->getTimestamp(),$end->getTimestamp()]:null;
}
/** A rental day is exactly 24 hours; return fields are server-derived. */
function rm_booking_duration(array $input): array {
    $day=$input['start_date']??null;$clock=$input['time']??null;$days=$input['rental_days']??null;
    if(!is_string($day)||!is_string($clock)||!is_string($days)||!rm_booking_day($day)||!rm_booking_time($clock)
        ||!preg_match('/^[1-9][0-9]{0,2}$/D',$days)|| (int)$days>365)throw new InvalidArgumentException('Podaj datę, godzinę odbioru i liczbę dób od 1 do 365.');
    $start=new DateTimeImmutable($day.' '.$clock,new DateTimeZone('Europe/Warsaw'));
    if($start->format('Y-m-d H:i')!==$day.' '.$clock)throw new InvalidArgumentException('Ta godzina nie istnieje w dniu zmiany czasu.');
    $end=$start->setTimestamp($start->getTimestamp()+(int)$days*86400);
    return ['rental_days'=>(int)$days,'end_date'=>$end->format('Y-m-d'),'return_time'=>$end->format('H:i')];
}
function rm_booking_overlap(array $a,array $b): bool {
    $ai=rm_booking_interval($a);$bi=rm_booking_interval($b);
    if($ai!==null&&$bi!==null)return $ai[0]<$bi[1]+5400&&$ai[1]+5400>$bi[0];
    return $a['start_date']<=$b['end_date']&&$a['end_date']>=$b['start_date'];
}
function rm_booking_validate(array $r,array $schedule): void {
    if(rm_booking_interval($r)===null)throw new InvalidArgumentException('Wpisz daty i godziny; zwrot musi być po odbiorze.');
    $now=new DateTimeImmutable('now',new DateTimeZone('Europe/Warsaw'));
    if(rm_booking_interval($r)[0]<=$now->getTimestamp())throw new InvalidArgumentException('Odbiór musi być w przyszłości.');
    if(!rm_booking_in_window($schedule,$r['start_date'],$r['time']))throw new InvalidArgumentException('Odbiór poza Twoimi godzinami wydawania.');
    // Returns are recorded explicitly; the owner currently configures issue windows only.
}

/** Time picker payload, independent of JavaScript. */
function rm_booking_selection(array $entry): string {
    if(($entry['mode']??'')==='closed')return '';
    if(($entry['mode']??'')!=='open'||!is_array($entry['slots']??null)||count($entry['slots'])>4)throw new InvalidArgumentException('Wybierz godziny lub brak wydawania.');
    $hours=[];
    foreach($entry['slots'] as $slot){
        if(!is_array($slot)||!is_string($slot['from']??null)||!is_string($slot['to']??null))throw new InvalidArgumentException('Nieprawidłowy wybór godzin.');
        if($slot['from']===''&&$slot['to']==='')continue;
        if(!rm_booking_time($slot['from'])||!rm_booking_time($slot['to']))throw new InvalidArgumentException('Wybierz obie godziny: od i do.');
        $hours[]=$slot['from'].'-'.$slot['to'];
    }
    if(!$hours)throw new InvalidArgumentException('Wybierz przynajmniej jeden przedział godzin.');
    $text=implode(',',$hours);rm_booking_windows($text);return $text;
}
