<?php
declare(strict_types=1);

// A copy occupies both boundary dates; the calendar flags legacy unassigned bookings.
function rm_read_assets(): array {
    $path = __DIR__ . '/rental_assets.php';
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
function rm_write_assets(array $assets): bool {
    $path=__DIR__.'/rental_assets.php';$tmp=$path.'.'.bin2hex(random_bytes(6)).'.php';
    $data=json_encode(array_values($assets),JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);
    if($data===false)return false;
    $contents="<?php http_response_code(404); exit; ?>\n".$data;
    $ok=@file_put_contents($tmp,$contents,LOCK_EX)===strlen($contents)&&@rename($tmp,$path);
    if(!$ok&&is_file($tmp))unlink($tmp);return $ok;
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
    if(($role==='standard'?(($asset['equipment']??'')!==($item['equipment']??'')):rm_asset_kind($asset)!==$role)
        &&!rm_keeps_legacy_assignment($item,$asset,$role,$reservations))
        return 'Egzemplarz należy do innej grupy sprzętu.';
    if(!empty($asset['in_service']))return 'Egzemplarz jest w serwisie.';
    $id=(string)($asset['id']??'');$start=(string)($item['start_date']??'');$end=(string)($item['end_date']??'');
    foreach($reservations as $r){
        if(!is_array($r)||($r['id']??'')===($item['id']??'')||!empty($r['settled'])||!empty($r['returned']))continue;
        $reserved=$id!==''&&(($r['asset_id']??'')===$id||($r['vacuum_asset_id']??'')===$id);
        $ambiguous=$role==='standard'&&($r['equipment']??'')===($item['equipment']??'')&&empty($r['asset_id']);
        if($role!=='standard'&&rm_reservation_covers($r,$role)){
            $ambiguous=$role==='giraffe'?empty($r['asset_id']):
                (empty($r['vacuum_asset_id'])&&(rm_equipment_kind((string)($r['equipment']??''))!=='vacuum'||empty($r['asset_id'])));
        }
        if(!$reserved&&!$ambiguous)continue;
        $rs=(string)($r['start_date']??'');$re=(string)($r['end_date']??'');
        if(!rm_valid_reservation_dates($r))return 'Sprawdź daty starszej rezerwacji sprzętu.';
        if(($start<=$re&&$end>=$rs)||(!empty($r['done'])&&$re<date('Y-m-d'))){
            if($ambiguous)return "Rezerwacja $rs – $re nie ma przypisanego egzemplarza. Uzupełnij przypisanie.";
            if(!empty($r['done'])&&$re<date('Y-m-d'))
                return "Wydany, brak potwierdzonego zwrotu (planowany: $re).";
            return "Zajęty przez rezerwację $rs – $re.";
        }
    }
    return '';
}
function rm_rental_conflict(array $item,array $reservations,array $assets): string {
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
function rm_hide_rental_finance(array $r): array {
    foreach(['income','loss','loss_note','settled_at','daily_rate','paid','deposit','settlement'] as $key)unset($r[$key]);
    return $r;
}

