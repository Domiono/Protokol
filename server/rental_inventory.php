<?php
declare(strict_types=1);
require_once __DIR__ . '/private_rental_files.php';

// A copy occupies both boundary dates; the calendar flags legacy unassigned bookings.
function rm_read_assets(): array {
    $path = rm_rental_file('rental_assets.php');
    if (!file_exists($path)) return [];
    $raw = is_file($path) ? @file_get_contents($path) : false;
    $cut = is_string($raw) ? strpos($raw, "\n") : false;
    $data = $cut === false ? null : json_decode(substr($raw, $cut + 1), true);
    if (!is_array($data) || !array_is_list($data)) {
        if (function_exists('rm_json_response')) rm_json_response(503, ['ok'=>false,'error'=>'Baza egzemplarzy jest nieczytelna. Zapis wstrzymany.']);
        if (function_exists('rm_admin_storage_error')) rm_admin_storage_error();
        http_response_code(503);
        exit('Baza egzemplarzy jest nieczytelna. Zapis wstrzymany.');
    }
    return $data;
}
// Serialize the full read/modify/write in protocol_sync.php for asset edits.
function rm_lock_assets() {
    $fp = @fopen(rm_rental_file('rental_assets.php') . '.lock', 'c+');
    if (!$fp) return false;
    if (!@flock($fp, LOCK_EX)) { fclose($fp); return false; }
    return $fp;
}
function rm_write_assets(array $assets): bool {
    $path=rm_rental_file('rental_assets.php');$tmp=$path.'.'.bin2hex(random_bytes(6)).'.php';
    $data=json_encode(array_values($assets),JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);
    if($data===false)return false;
    $contents="<?php http_response_code(404); exit; ?>\n".$data;
    $ok=@file_put_contents($tmp,$contents,LOCK_EX)===strlen($contents)&&@rename($tmp,$path);
    if(!$ok&&is_file($tmp))unlink($tmp);return $ok;
}
// An internal equipment name must never become an incomplete public offer.
// Keep the private Android-only name registry in a non-executable PHP envelope.
function rm_equipment_names_path(): string {
    return __DIR__ . '/rental_equipment_names.php';
}
function rm_read_equipment_names(): array {
    $path=rm_equipment_names_path();
    if (!file_exists($path)) return [];
    $raw=@file_get_contents($path);
    $cut=is_string($raw)?strpos($raw,"\n"):false;
    $names=$cut===false?null:json_decode(substr($raw,$cut+1),true);
    if (!is_array($names) || !array_is_list($names)) {
        if (function_exists('rm_json_response')) rm_json_response(503,['ok'=>false,'error'=>'Wewnętrzna lista sprzętu jest nieczytelna.']);
        http_response_code(503);exit('Wewnętrzna lista sprzętu jest nieczytelna.');
    }
    return array_values(array_filter($names,fn($name)=>is_string($name)&&trim($name)!==''));
}
function rm_write_equipment_names(array $names): bool {
    $path=rm_equipment_names_path();
    $json=json_encode(array_values($names),JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);
    if ($json===false) return false;
    $payload="<?php http_response_code(404); exit; ?>\n".$json;
    $tmp=$path.'.'.bin2hex(random_bytes(8)).'.php';
    $bytes=@file_put_contents($tmp,$payload,LOCK_EX);
    $ok=$bytes===strlen($payload)&&@rename($tmp,$path);
    if (!$ok) @unlink($tmp);
    return $ok;
}

function rm_inventory_normalize(string $name): string {
    // Transliterate before strtolower so this works without PHP mbstring (php -n tests).
    $map=['Ą'=>'a','Ć'=>'c','Ę'=>'e','Ł'=>'l','Ń'=>'n','Ó'=>'o','Ś'=>'s','Ź'=>'z','Ż'=>'z',
          'ą'=>'a','ć'=>'c','ę'=>'e','ł'=>'l','ń'=>'n','ó'=>'o','ś'=>'s','ź'=>'z','ż'=>'z'];
    return strtolower(strtr($name,$map));
}
function rm_equipment_kind(string $name): string {
    $name=rm_inventory_normalize($name);
    if(str_contains($name,'zyrafa'))return 'giraffe';
    if(str_contains($name,'odkurzacz')&&!str_contains($name,'puzzi')&&!str_contains($name,'piorac')&&!str_contains($name,'ekstrakcyj'))return 'vacuum';
    return 'standard';
}
function rm_asset_kind(array $asset): string {
    $kind=(string)($asset['asset_kind']??'');
    return in_array($kind,['standard','giraffe','vacuum'],true)?$kind:rm_equipment_kind((string)($asset['equipment']??''));
}
function rm_bundle_offer(string $equipment): bool {
    if(rm_equipment_kind($equipment)!=='giraffe')return false;
    $name=rm_inventory_normalize($equipment);
    return str_contains($name,'odkurzacz')||str_contains($name,'zestaw');
}
function rm_needs_vacuum(array $r): bool {
    if(rm_equipment_kind((string)($r['equipment']??''))!=='giraffe')return false;
    return rm_bundle_offer((string)($r['equipment']??''))||!empty($r['needs_vacuum']);
}
function rm_reservation_covers(array $r,string $role): bool {
    if($role==='vacuum')return rm_equipment_kind((string)($r['equipment']??''))==='vacuum'||rm_needs_vacuum($r);
    return $role==='giraffe'&&rm_equipment_kind((string)($r['equipment']??''))==='giraffe';
}
function rm_matching_assets(array $item,array $assets,string $role): array {
    $list=array_values(array_filter($assets,function($a)use($item,$role){
        return $role!=='standard'?rm_asset_kind($a)===$role:(string)($a['equipment']??'')===(string)($item['equipment']??'');
    }));
    usort($list,fn($a,$b)=>strcasecmp((string)($a['label']??''),(string)($b['label']??'')));
    return $list;
}
function rm_valid_reservation_dates(array $item): bool {
    $start=(string)($item['start_date']??'');$end=(string)($item['end_date']??'');
    foreach([$start,$end] as $day){$d=DateTimeImmutable::createFromFormat('!Y-m-d',$day);if(!$d||$d->format('Y-m-d')!==$day)return false;}
    return $end>=$start;
}
/**
 * Historic assigned rentals can use an older offer name than the current asset.
 * Accept the mismatch only for the original reservation with the same copy and
 * equipment name; still check service and other reservations for collisions.
 */
/**
 * Project a physical copy's interval from its dates and pickup clock.
 * Android reservations store a pickup clock but no return_time: under RentMor's
 * 24-hour day rule their scheduled return is at that clock on end_date.
 * Truly date-only or invalid legacy entries stay conservatively blocked by day.
 */
function rm_inventory_interval(array $r): ?array {
    if (!rm_valid_reservation_dates($r)) return null;
    $clock=$r['time']??null;
    if (!is_string($clock)||!preg_match('/^(?:[01][0-9]|2[0-3]):[0-5][0-9]$/D',$clock))return null;
    $returnClock=$r['return_time']??null;
    if ($returnClock===null||$returnClock==='')$returnClock=$clock;
    if (!is_string($returnClock)||!preg_match('/^(?:[01][0-9]|2[0-3]):[0-5][0-9]$/D',$returnClock))return null;
    $tz=new DateTimeZone('Europe/Warsaw');
    $startText=$r['start_date'].' '.$clock;
    $endText=$r['end_date'].' '.$returnClock;
    $start=new DateTimeImmutable($startText,$tz);
    $end=new DateTimeImmutable($endText,$tz);
    if ($start->format('Y-m-d H:i')!==$startText||$end->format('Y-m-d H:i')!==$endText||$end<=$start)return null;
    return [$start->getTimestamp(),$end->getTimestamp()];
}
/** Timed stays require a 90-minute gap; date-only legacy stays occupy both dates. */
function rm_inventory_overlap(array $candidate,array $reserved): bool {
    $a=rm_inventory_interval($candidate);
    $b=rm_inventory_interval($reserved);
    if ($a!==null&&$b!==null)return $a[0]<$b[1]+5400&&$a[1]+5400>$b[0];
    return $candidate['start_date']<=$reserved['end_date']
        &&$candidate['end_date']>=$reserved['start_date'];
}
function rm_keeps_legacy_assignment(array $item,array $asset,string $role,array $reservations): bool {
    if($role!=='standard')return false;
    $bookingId=(string)($item['id']??'');$assetId=(string)($item['asset_id']??'');
    if($bookingId===''||$assetId===''||$assetId!==(string)($asset['id']??''))return false;
    foreach($reservations as $previous){
        if(!is_array($previous)||((string)($previous['id']??''))!==$bookingId)continue;
        return (string)($previous['asset_id']??'')===$assetId
            &&(string)($previous['equipment']??'')===(string)($item['equipment']??'');
    }
    return false;
}
function rm_copy_conflict(array $item,array $asset,string $role,array $reservations): string {
    $reservations=rm_flatten_rental_items($reservations);
    if(($role==='standard'?(($asset['equipment']??'')!==($item['equipment']??'')):rm_asset_kind($asset)!==$role)
        &&!rm_keeps_legacy_assignment($item,$asset,$role,$reservations))
        return 'Egzemplarz należy do innej grupy sprzętu.';
    if(!empty($asset['in_service']))return 'Egzemplarz jest w serwisie.';
    $id=(string)($asset['id']??'');$start=(string)($item['start_date']??'');$end=(string)($item['end_date']??'');
    foreach($reservations as $r){
        if(!is_array($r)||($r['_booking_id']??$r['id']??'')===($item['_booking_id']??$item['id']??'')||!empty($r['settled'])||!empty($r['returned']))continue;
        $reserved=$id!==''&&(($r['asset_id']??'')===$id||($r['vacuum_asset_id']??'')===$id);
        $ambiguous=$role==='standard'&&($r['equipment']??'')===($item['equipment']??'')&&empty($r['asset_id']);
        if($role!=='standard'&&rm_reservation_covers($r,$role)){
            $ambiguous=$role==='giraffe'?empty($r['asset_id']):
                (empty($r['vacuum_asset_id'])&&(rm_equipment_kind((string)($r['equipment']??''))!=='vacuum'||empty($r['asset_id'])));
        }
        if(!$reserved&&!$ambiguous)continue;
        $rs=(string)($r['start_date']??'');$re=(string)($r['end_date']??'');
        if(!rm_valid_reservation_dates($r))return 'Sprawdź daty starszej rezerwacji sprzętu.';
        if(rm_inventory_overlap($item,$r)||(!empty($r['done'])&&$re<date('Y-m-d'))){
            if($ambiguous)return "Rezerwacja $rs – $re nie ma przypisanego egzemplarza. Uzupełnij przypisanie.";
            if(!empty($r['done'])&&$re<date('Y-m-d'))
                return "Wydany, brak potwierdzonego zwrotu (planowany: $re).";
            return "Zajęty przez rezerwację $rs – $re.";
        }
    }
    return '';
}
function rm_single_rental_conflict(array $item,array $reservations,array $assets): string {
    if(!empty($item['returned'])||!empty($item['settled']))return '';
    $role=rm_equipment_kind((string)($item['equipment']??''));
    $needs=rm_needs_vacuum($item);$id=(string)($item['asset_id']??'');
    if($id==='')return $needs?'Przypisz żyrafę oraz odkurzacz do zestawu.':($role==='standard'?'':'Przypisz konkretny egzemplarz przed zapisaniem rezerwacji.');
    if(!rm_valid_reservation_dates($item))return 'Nieprawidłowy termin.';
    $primary=null;foreach($assets as $a)if(($a['id']??'')===$id)$primary=$a;
    if(!$primary)return 'Nie znaleziono egzemplarza.';
    $reason=rm_copy_conflict($item,$primary,$role,$reservations);if($reason!=='')return $reason;
    $second=(string)($item['vacuum_asset_id']??'');
    if(!$needs)return $second===''?'':'Rezerwacja bez zestawu nie może blokować dodatkowego odkurzacza.';
    if($second==='')return 'Przypisz odkurzacz przemysłowy do zestawu.';
    if($second===$id)return 'Zestaw wymaga dwóch różnych urządzeń.';
    $secondary=null;foreach($assets as $a)if(($a['id']??'')===$second)$secondary=$a;
    if(!$secondary)return 'Nie znaleziono odkurzacza do zestawu.';
    return rm_copy_conflict($item,$secondary,'vacuum',$reservations);
}
/** Summarize blocked physical copies without exposing customer details. */
function rm_unavailable_copies(array $item,array $reservations,array $assets,string $role): string {
    $reasons=[];
    foreach(rm_matching_assets($item,$assets,$role) as $copy){
        $reason=rm_copy_conflict($item,$copy,$role,$reservations);
        if($reason!=='')$reasons[]='• '.(string)($copy['label']??'Egzemplarz').': '.$reason;
    }
    return implode("\n",$reasons);
}
/** Choose both devices under the reservations.json lock (the inventory file is read inside it). */
function rm_auto_pick_asset(array $item,array $reservations,array $assets): array {
    if(!rm_valid_reservation_dates($item))return ['asset'=>null,'vacuum'=>null,'error'=>'Sprawdź daty rezerwacji.'];
    $role=rm_equipment_kind((string)($item['equipment']??''));$needs=rm_needs_vacuum($item);
    $mains=rm_matching_assets($item,$assets,$role);
    if(!$mains)return ['asset'=>null,'vacuum'=>null,'error'=>$role==='standard'&&!$needs?'':'Dodaj egzemplarze żyraf i odkurzaczy w Dostępność przed rezerwacją.'];
    $primary=null;
    foreach($mains as $a)if(rm_copy_conflict($item,$a,$role,$reservations)===''){$primary=$a;break;}
    if($primary===null)return ['asset'=>null,'vacuum'=>null,'error'=>"Brak wolnego egzemplarza sprzętu w wybranym terminie.\n".
        rm_unavailable_copies($item,$reservations,$assets,$role).
        "\nJeżeli sprzęt wrócił, potwierdź zwrot lub rozlicz najem."];
    $secondary=null;
    if($needs){
        foreach(rm_matching_assets($item,$assets,'vacuum') as $a){
            if(($a['id']??'')!==($primary['id']??'')&&rm_copy_conflict($item,$a,'vacuum',$reservations)===''){$secondary=$a;break;}
        }
        if($secondary===null)return ['asset'=>null,'vacuum'=>null,'error'=>"Brak wolnego odkurzacza przemysłowego do zestawu.\n".
            (rm_matching_assets($item,$assets,'vacuum')?
                rm_unavailable_copies($item,$reservations,$assets,'vacuum'):
                'Nie zarejestrowano odkurzaczy w grupie Odkurzacz przemysłowy.').
            "\nJeżeli sprzęt wrócił, potwierdź zwrot lub rozlicz najem."];
    }
    return ['asset'=>$primary,'vacuum'=>$secondary,'error'=>''];
}
/**
 * Reassign a future booking to another registered physical copy.
 * Labels always come from inventory; availability is checked by the caller
 * against all bookings under the shared reservations lock.
 */
function rm_select_reservation_copies(array &$item, array $original, array $selection, array $assets): string {
    foreach (['asset_id'=>'asset_label', 'vacuum_asset_id'=>'vacuum_asset_label'] as $field=>$label) {
        if (!array_key_exists($field, $selection)) continue;
        if (!is_string($selection[$field])) return 'Nieprawidłowy egzemplarz.';
        $requested=trim($selection[$field]);
        $previous=(string)($original[$field]??'');
        if ($requested===$previous) continue;
        if (!empty($original['settled']) || !empty($original['returned'])
            || (!empty($original['done']) && $previous!=='')) {
            return 'Wydanego lub zwróconego egzemplarza nie można podmienić. Zmień przyszłą rezerwację.';
        }
        if ($requested==='') {
            if ($previous!=='') return 'Nie usuwaj istniejącego przypisania. Wybierz inny egzemplarz.';
            $item[$field]='';
            $item[$label]='';
            continue;
        }
        $copy=null;
        foreach($assets as $asset) {
            if(is_array($asset)&&(string)($asset['id']??'')===$requested) {$copy=$asset;break;}
        }
        if($copy===null) return 'Nie znaleziono wybranego egzemplarza.';
        $item[$field]=$requested;
        $item[$label]=(string)($copy['label']??'');
    }
    return '';
}

function rm_hide_rental_finance(array $r): array {
    foreach(['income','loss','loss_note','settled_at','daily_rate','paid','deposit','settlement'] as $key)unset($r[$key]);
    if(isset($r['additional_items']))foreach($r['additional_items'] as &$line)unset($line['daily_rate']);unset($line);
    unset($r['primary_daily_rate']);
    return $r;
}


/** Project inventory lines only; the stored booking remains one financial/customer record. */
function rm_rental_items(array $r): array {
    $primary=$r;unset($primary['additional_items']);
    $primary['_booking_id']=$r['_booking_id']??$r['id']??'';$out=[$primary];
    foreach(($r['additional_items']??[]) as $line){
        $row=$primary;
        foreach(['equipment','asset_id','asset_label','vacuum_asset_id','vacuum_asset_label','daily_rate'] as $key)$row[$key]=$line[$key]??'';
        $row['needs_vacuum']=!empty($line['needs_vacuum']);$row['auto_select']=!empty($line['auto_select']);
        $row['id']=($r['id']??'').'::'.($line['line_id']??'');$out[]=$row;
    }
    return $out;
}
function rm_flatten_rental_items(array $rows): array {
    $out=[];foreach($rows as $row)foreach(rm_rental_items($row) as $line)$out[]=$line;return $out;
}
function rm_rental_uses(array $r,string $id): bool {
    foreach(rm_rental_items($r) as $line)if(($line['asset_id']??'')===$id||($line['vacuum_asset_id']??'')===$id)return true;return false;
}
function rm_rental_conflict(array $item,array $reservations,array $assets): string {
    if(!empty($item['settled'])||!empty($item['returned']))return '';
    $lines=rm_rental_items($item);$used=[];$flat=rm_flatten_rental_items($reservations);
    foreach($lines as $line){
        foreach(['asset_id','vacuum_asset_id'] as $key){$id=(string)($line[$key]??'');if($id==='')continue;
            if(isset($used[$id]))return 'Ten sam egzemplarz wybrano więcej niż raz.';$used[$id]=true;}
        if(count($lines)>1&&empty($line['asset_id']))return 'Przypisz egzemplarz: '.($line['equipment']??'');
        $reason=rm_single_rental_conflict($line,$flat,$assets);if($reason!=='')return $reason;
    }
    return '';
}
/** Allocate every new/missing line before the single atomic booking write. */
function rm_assign_rental_items(array &$item,array $reservations,array $assets,bool $all): string {
    $working=$item;$busy=array_values(array_filter($reservations,fn($r)=>($r['id']??'')!==($item['id']??'')));
    foreach(rm_rental_items($working) as $index=>$held){
        if(!empty($held['asset_id'])&&!($all&&($index===0||!empty($held['auto_select'])))){
            $held['_booking_id']='held::'.$index;$busy[]=$held;
        }
    }
    foreach(rm_rental_items($working) as $index=>$line){
        if(($all&&($index===0||!empty($line['auto_select'])))||empty($line['asset_id'])){
            $pick=rm_auto_pick_asset($line,$busy,$assets);if($pick['error']!=='')return ($line['equipment']??'').': '.$pick['error'];
            $line['asset_id']=$pick['asset']['id']??'';$line['asset_label']=$pick['asset']['label']??'';
            $line['vacuum_asset_id']=$pick['vacuum']['id']??'';$line['vacuum_asset_label']=$pick['vacuum']['label']??'';
        }
        if(empty($line['asset_id']))return 'Dodaj fizyczny egzemplarz: '.($line['equipment']??'');
        foreach(['asset_id','asset_label','vacuum_asset_id','vacuum_asset_label'] as $key){
            if($index===0)$working[$key]=$line[$key]??'';else $working['additional_items'][$index-1][$key]=$line[$key]??'';
        }
        $line['_booking_id']='allocated::'.$index; $busy[]=$line;
    }
    $item=$working;return '';
}
/** Strict allowlist: a line cannot override parent dates, lifecycle or customer fields. */
function rm_prepare_additional_items(array &$item,array $op,?array $existing,array $assets,string $role): string {
    $previous=$existing['additional_items']??[];
    $raw=$op['additional_items']??$previous;
    if(!is_array($raw)||!array_is_list($raw)||count($raw)>19)return 'Nieprawidłowa lista sprzętów (maksymalnie 20 pozycji).';
    if($previous && (!array_key_exists('items_revision',$op)||(int)$op['items_revision']!==(int)($existing['items_revision']??0)))return 'Lista sprzętów zmieniła się. Odśwież rezerwację przed zapisem.';
    $clean=[];$seen=[];
    foreach($raw as $line){
        if(!is_array($line))return 'Nieprawidłowa pozycja sprzętu.';
        $id=$line['line_id']??null;$name=$line['equipment']??null;
        if(!is_string($id)||!preg_match('/^[a-zA-Z0-9_-]{1,80}$/D',$id)||isset($seen[$id])||!is_string($name)||trim($name)===''||strlen($name)>800)return 'Sprawdź nazwę i identyfikator pozycji.';
        $seen[$id]=true;$row=['line_id'=>$id,'equipment'=>trim($name),'needs_vacuum'=>!empty($line['needs_vacuum']),'auto_select'=>!empty($line['auto_select'])];
        $old=null;foreach($previous as $saved)if(($saved['line_id']??'')===$id)$old=$saved;
        foreach(['asset_id'=>'asset_label','vacuum_asset_id'=>'vacuum_asset_label'] as $key=>$label){
            $copyId=$line[$key]??'';if(!is_string($copyId)||strlen($copyId)>200)return 'Nieprawidłowy egzemplarz.';
            $row[$key]=$copyId;$row[$label]='';foreach($assets as $asset)if(($asset['id']??'')===$copyId)$row[$label]=(string)($asset['label']??'');
        }
        if($role==='admin'){
            $price=$line['daily_rate']??0;$n=is_scalar($price)?filter_var(str_replace(',','.',(string)$price),FILTER_VALIDATE_FLOAT):false;
            if($n===false||!is_finite((float)$n)||$n<0||$n>10000000)return 'Nieprawidłowa cena pozycji.';$row['daily_rate']=round((float)$n,2);
        }elseif($old!==null&&array_key_exists('daily_rate',$old))$row['daily_rate']=$old['daily_rate'];
        if($old!==null){
            $before=$old;$before['done']=$existing['done']??false;$before['returned']=$existing['returned']??false;$before['settled']=$existing['settled']??false;
            $error=rm_select_reservation_copies($row,$before,array_intersect_key($row,array_flip(['asset_id','vacuum_asset_id'])),$assets);if($error!=='')return $error;
        }
        $clean[]=$row;
    }
    if($existing!==null&&(!empty($existing['done'])||!empty($existing['returned'])||!empty($existing['settled']))){
        $shape=fn($rows)=>array_map(fn($r)=>array_map(fn($k)=>$r[$k]??($k==='needs_vacuum'?false:''),['line_id','equipment','asset_id','vacuum_asset_id','needs_vacuum']),$rows);
        if($shape($previous)!==$shape($clean))return 'Nie można zmienić listy wydanych lub zakończonych sprzętów.';
    }
    if(array_key_exists('additional_items',$op)||$previous){
        $item['additional_items']=$clean;$item['items_revision']=(int)($existing['items_revision']??0)+1;
        if($role==='admin'){
            $primary=$op['primary_daily_rate']??$existing['primary_daily_rate']??$existing['daily_rate']??$op['daily_rate']??0;
            $n=is_scalar($primary)?filter_var(str_replace(',','.',(string)$primary),FILTER_VALIDATE_FLOAT):false;
            if($n===false||!is_finite((float)$n)||$n<0||$n>10000000)return 'Nieprawidłowa stawka głównego sprzętu.';
            $item['primary_daily_rate']=round((float)$n,2);
            $total=$item['primary_daily_rate'];foreach($clean as $row)$total+=(float)($row['daily_rate']??0);
            if($total>10000000)return 'Suma stawek jest za duża.';$item['daily_rate']=round($total,2);
        }else{
            if(isset($existing['primary_daily_rate']))$item['primary_daily_rate']=$existing['primary_daily_rate'];
            $total=(float)($existing['primary_daily_rate']??$existing['daily_rate']??0);foreach($clean as $row)$total+=(float)($row['daily_rate']??0);$item['daily_rate']=round($total,2);
        }
    }
    return '';
}
