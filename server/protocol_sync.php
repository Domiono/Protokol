<?php
// RentMor protocol sync API. Upload beside protocol.php.
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');

function rm_json_response(int $status, array $payload): never {
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}
function rm_read_json_file(string $path, array $fallback): array {
    if (!is_file($path)) return $fallback;
    $raw = file_get_contents($path);
    $data = is_string($raw) ? json_decode($raw, true) : null;
    return is_array($data) ? $data : $fallback;
}
function rm_write_json_atomic(string $path, array $data): bool {
    $json = json_encode(array_values($data), JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    if ($json === false) return false;
    $tmp = $path . '.' . bin2hex(random_bytes(6)) . '.tmp';
    if (file_put_contents($tmp, $json, LOCK_EX) === false) return false;
    return rename($tmp, $path);
}
function rm_device_role(string $file): string {
    $token = (string)($_COOKIE['rm_protocol_device'] ?? '');
    if ($token === '') return '';
    $data = rm_read_json_file($file, ['devices'=>[]]);
    foreach (($data['devices'] ?? []) as $device) {
        if (!empty($device['active']) && !empty($device['token_hash']) &&
            hash_equals((string)$device['token_hash'], hash('sha256', $token))) {
            return (($device['role'] ?? '') === 'assistant') ? 'assistant' : 'admin';
        }
    }
    return '';
}
function rm_text(array $input, string $key, int $max = 4000): string {
    $value = trim((string)($input[$key] ?? ''));
    return function_exists('mb_substr') ? mb_substr($value, 0, $max, 'UTF-8') : substr($value, 0, $max);
}

function rm_phone_key(string $value): string {
    $value = trim($value);
    if ($value === '' || !preg_match('/^[+0-9() .\\-]+$/', $value)) return '';
    $digits = preg_replace('/\\D+/', '', $value);
    if (strlen($digits) === 11 && str_starts_with($digits, '48')) $digits = substr($digits, 2);
    if (strlen($digits) === 9) return $digits;
    if (strlen($digits) >= 10 && strlen($digits) <= 15) return '+' . $digits;
    return '';
}

function rm_expire_protocol_photos(string $file): void {
    if (!is_file($file)) return;
    $lock = fopen($file . '.lock', 'c+');
    if (!$lock || !flock($lock, LOCK_EX)) { if ($lock) fclose($lock); return; }
    $protocols = rm_read_json_file($file, []);
    $changed = false;
    $cutoff = time() - (90 * 86400);
    foreach ($protocols as &$protocol) {
        if (!is_array($protocol) || empty($protocol['photos_base64']) || !is_array($protocol['photos_base64'])) continue;
        $uploadedAt = strtotime((string)($protocol['photos_uploaded_at'] ?? $protocol['created_at'] ?? ''));
        if ($uploadedAt !== false && $uploadedAt <= $cutoff) {
            unset($protocol['photos_base64'], $protocol['photo_base64']);
            $protocol['photos_expired'] = true;
            $protocol['photos_deleted_at'] = date('Y-m-d H:i:s');
            $changed = true;
        }
    }
    unset($protocol);
    if ($changed) rm_write_json_atomic($file, $protocols);
    flock($lock, LOCK_UN);
    fclose($lock);
}

// A copy occupies both boundary dates; the calendar flags legacy unassigned bookings.
function rm_read_assets(): array {
    $path=__DIR__.'/rental_assets.php';if(!is_file($path))return [];
    $raw=file_get_contents($path);$data=json_decode(substr((string)$raw,strpos((string)$raw,"\n")+1),true);return is_array($data)?$data:[];
}
function rm_write_assets(array $assets): bool {
    $path=__DIR__.'/rental_assets.php';$tmp=$path.'.'.bin2hex(random_bytes(6)).'.php';
    $data=json_encode(array_values($assets),JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);
    if($data===false)return false;
    $ok=file_put_contents($tmp,"<?php http_response_code(404); exit; ?>\n".$data,LOCK_EX)!==false&&rename($tmp,$path);
    if(!$ok&&is_file($tmp))unlink($tmp);return $ok;
}
function rm_equipment_kind(string $name): string {
    $name=strtr((function_exists('mb_strtolower')?mb_strtolower($name,'UTF-8'):strtolower($name)),['ą'=>'a','ć'=>'c','ę'=>'e','ł'=>'l','ń'=>'n','ó'=>'o','ś'=>'s','ź'=>'z','ż'=>'z']);
    if(str_contains($name,'zyrafa'))return 'giraffe';
    if(str_contains($name,'odkurzacz')&&!str_contains($name,'puzzi')&&!str_contains($name,'piorac')&&!str_contains($name,'ekstrakcyj'))return 'vacuum';
    return 'standard';
}
function rm_asset_kind(array $asset): string {
    $kind=(string)($asset['asset_kind']??'');
    return in_array($kind,['standard','giraffe','vacuum'],true)?$kind:rm_equipment_kind((string)($asset['equipment']??''));
}
function rm_needs_vacuum(array $r): bool {
    if(rm_equipment_kind((string)($r['equipment']??''))!=='giraffe')return false;
    if(array_key_exists('needs_vacuum',$r))return !empty($r['needs_vacuum']);
    return str_contains(strtr((function_exists('mb_strtolower')?mb_strtolower((string)($r['equipment']??''),'UTF-8'):strtolower((string)($r['equipment']??''))),['ą'=>'a','ć'=>'c','ę'=>'e','ł'=>'l','ń'=>'n','ó'=>'o','ś'=>'s','ź'=>'z','ż'=>'z']),'odkurzacz');
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
function rm_copy_conflict(array $item,array $asset,string $role,array $reservations): string {
    if($role==='standard'?(($asset['equipment']??'')!==($item['equipment']??'')):rm_asset_kind($asset)!==$role)
        return 'Egzemplarz należy do innej grupy sprzętu.';
    if(!empty($asset['in_service']))return 'Egzemplarz jest w serwisie.';
    $id=(string)($asset['id']??'');$start=(string)($item['start_date']??'');$end=(string)($item['end_date']??'');
    foreach($reservations as $r){
        if(!is_array($r)||($r['id']??'')===($item['id']??'')||!empty($r['settled'])||!empty($r['returned']))continue;
        $reserved=$id!==''&&(($r['asset_id']??'')===$id||($r['vacuum_asset_id']??'')===$id);
        $ambiguous=false;
        if($role!=='standard'&&rm_reservation_covers($r,$role)){
            $ambiguous=$role==='giraffe'?empty($r['asset_id']):
                (empty($r['vacuum_asset_id'])&&(rm_equipment_kind((string)($r['equipment']??''))!=='vacuum'||empty($r['asset_id'])));
        }
        if(!$reserved&&!$ambiguous)continue;
        $rs=(string)($r['start_date']??'');$re=(string)($r['end_date']??'');
        if(!rm_valid_reservation_dates($r))return 'Sprawdź daty starszej rezerwacji sprzętu.';
        if(($start<=$re&&$end>=$rs)||(!empty($r['done'])&&$re<date('Y-m-d')))
            return $ambiguous?'Wcześniejsza rezerwacja tej grupy nie ma przypisanego egzemplarza. Uzupełnij ją najpierw.':
                'Egzemplarz jest już zajęty w tym terminie lub po terminie zwrotu.';
    }
    return '';
}
function rm_rental_conflict(array $item,array $reservations,array $assets): string {
    if(!empty($item['returned'])||!empty($item['settled']))return '';
    $role=rm_equipment_kind((string)($item['equipment']??''));
    $needs=rm_needs_vacuum($item);$id=(string)($item['asset_id']??'');
    if($id==='')return $needs?'Przypisz żyrafę oraz odkurzacz do zestawu.':'';
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
/** Choose both devices under the reservations.json lock (the inventory file is read inside it). */
function rm_auto_pick_asset(array $item,array $reservations,array $assets): array {
    if(!rm_valid_reservation_dates($item))return ['asset'=>null,'vacuum'=>null,'error'=>'Sprawdź daty rezerwacji.'];
    $role=rm_equipment_kind((string)($item['equipment']??''));$needs=rm_needs_vacuum($item);
    $mains=rm_matching_assets($item,$assets,$role);
    if(!$mains)return ['asset'=>null,'vacuum'=>null,'error'=>$role==='standard'&&!$needs?'':'Dodaj egzemplarze żyraf i odkurzaczy w Dostępność przed rezerwacją.'];
    $primary=null;
    foreach($mains as $a)if(rm_copy_conflict($item,$a,$role,$reservations)===''){$primary=$a;break;}
    if($primary===null)return ['asset'=>null,'vacuum'=>null,'error'=>'Brak wolnego egzemplarza sprzętu w wybranym terminie.'];
    $secondary=null;
    if($needs){
        foreach(rm_matching_assets($item,$assets,'vacuum') as $a){
            if(($a['id']??'')!==($primary['id']??'')&&rm_copy_conflict($item,$a,'vacuum',$reservations)===''){$secondary=$a;break;}
        }
        if($secondary===null)return ['asset'=>null,'vacuum'=>null,'error'=>'Brak wolnego odkurzacza przemysłowego do zestawu. Sprawdź wcześniejsze rezerwacje i przypisania.'];
    }
    return ['asset'=>$primary,'vacuum'=>$secondary,'error'=>''];
}
function rm_hide_rental_finance(array $r): array {
    foreach(['income','loss','loss_note','settled_at','daily_rate','paid','deposit','settlement'] as $key)unset($r[$key]);
    return $r;
}

$role = rm_device_role(__DIR__ . '/protocol_devices.json');
if ($role === '') rm_json_response(401, ['ok'=>false, 'error'=>'Urządzenie nieaktywne. Połącz się z internetem i aktywuj je ponownie.']);

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
// Token registration never returns Firebase credentials or other devices' tokens.
if (($_GET['view'] ?? '') === 'push_token') {
    if ($method !== 'POST') rm_json_response(405,['ok'=>false,'error'=>'Niedozwolona metoda.']);
    if (($_SERVER['HTTP_X_RENTMOR_SYNC'] ?? '') !== '1') rm_json_response(403,['ok'=>false,'error'=>'Brak nagłówka uwierzytelnienia aplikacji.']);
    $rawPush=file_get_contents('php://input');
    if(!is_string($rawPush)||strlen($rawPush)>6000)rm_json_response(400,['ok'=>false,'error'=>'Nieprawidłowe dane push.']);
    $input=json_decode($rawPush,true);
    if(!is_array($input))rm_json_response(400,['ok'=>false,'error'=>'Nieprawidłowe dane push.']);
    $token=$input['token']??'';
    if(!is_string($token)||($token!==''&&!preg_match('/^[A-Za-z0-9:._-]{40,4096}$/D',$token)))
        rm_json_response(400,['ok'=>false,'error'=>'Nieprawidłowy token.']);
    require_once __DIR__.'/push_bridge.php';
    // Non-admin devices may unregister a previously assigned token,
    // but only active admin devices may register new tokens.
    $enabled=($input['enabled'] ?? false) === true;
    $authorized=$role==='admin' && $enabled;
    $ok=rm_push_register(rm_push_device_hash(),$token,$authorized);
    if(!$ok)rm_json_response(503,['ok'=>false,'error'=>'Magazyn tokenów jest niedostępny.']);
    rm_json_response(200,['ok'=>true,'push_enabled'=>$authorized]);
}

// Safe read-only diagnostics: only this authenticated administrator device's state.
if (($_GET['view'] ?? '') === 'push_status') {
    if ($method !== 'GET') rm_json_response(405,['ok'=>false,'error'=>'Niedozwolona metoda.']);
    if ($role !== 'admin') rm_json_response(403,['ok'=>false,'error'=>'Dostęp tylko dla administratora.']);
    require_once __DIR__.'/push_bridge.php';
    $keyPath=rm_push_private_dir().'/fcm-service-account.json';
    $keyExists=is_file($keyPath);
    $keyReadable=$keyExists && is_readable($keyPath);
    $account=rm_push_read_json($keyPath);
    $registered=false;
    foreach (rm_push_read_json(rm_push_registry_file()) as $entry) {
        if (!is_array($entry) || !isset($entry['device_hash'])) continue;
        if (hash_equals((string)$entry['device_hash'],rm_push_device_hash()) &&
            !empty($entry['token']) && (int)($entry['updated'] ?? 0)>time()-90*86400) {
            $registered=true;break;
        }
    }
    $folder=rm_push_private_dir();
    $storageReady=is_dir($folder) ? is_writable($folder) : is_writable(dirname($folder));
    rm_json_response(200,[
        'ok'=>true,
        'server_configured'=>isset($account['client_email'],$account['private_key'],$account['project_id']),
        'key_exists'=>$keyExists, 'key_readable'=>$keyReadable,
        'registered'=>$registered, 'storage_writable'=>$storageReady
    ]);
}

// A small authenticated inbox response for Android background checks.
// Never send customer contact details to assistant devices.
if (($_GET['view'] ?? '') === 'messages') {
    if ($method !== 'GET') rm_json_response(405, ['ok'=>false,'error'=>'Niedozwolona metoda.']);
    if ($role !== 'admin') rm_json_response(403, ['ok'=>false,'error'=>'Wiadomości są dostępne tylko dla administratora.']);
    $rawMessages = rm_read_json_file(__DIR__ . '/messages.json', []);
    $messages = [];
    foreach ($rawMessages as $entry) {
        if (!is_array($entry)) continue;
        $entry = array_intersect_key($entry, array_flip(['id','date','client','contact','product','message']));
        // Older entries without IDs must remain stable across polls.
        $id = trim((string)($entry['id'] ?? ''));
        if ($id === '') $id = hash('sha256',json_encode($entry,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES));
        $messages[] = [
            'id'=>substr($id,0,128),
            'date'=>substr((string)($entry['date'] ?? ''),0,80),
            'client'=>substr((string)($entry['client'] ?? ''),0,200),
            'contact'=>substr((string)($entry['contact'] ?? ''),0,160),
            'product'=>substr((string)($entry['product'] ?? ''),0,200),
            'message'=>substr((string)($entry['message'] ?? ''),0,8000)
        ];
    }
    rm_json_response(200, ['ok'=>true, 'role'=>'admin', 'messages'=>$messages]);
}
rm_expire_protocol_photos(__DIR__ . '/protocols.json');
if ($method === 'GET') {
    $protocols = rm_read_json_file(__DIR__ . '/protocols.json', []);
    $reservations = rm_read_json_file(__DIR__ . '/reservations.json', []);
    $profiles = rm_read_json_file(__DIR__ . '/customers.json', []);
    $customers = [];
    foreach ($profiles as $profile) {
        $key = rm_phone_key((string)($profile['phone'] ?? ''));
        if ($key !== '') $customers[$key] = $profile + ['phone_key'=>$key,'reservations'=>[],'protocols'=>[]];
    }
    foreach ($reservations as $reservation) {
        $phone = (string)($reservation['phone'] ?? '');
        if ($phone === '') continue;
        $key = rm_phone_key($phone); if ($key === '') continue;
        if (!isset($customers[$key])) $customers[$key] = ['id'=>$key,'phone'=>$phone,'phone_key'=>$key,'name'=>'','notes'=>'','reservations'=>[],'protocols'=>[]];
        if (($customers[$key]['name'] ?? '') === '' && trim((string)($reservation['customer_name'] ?? '')) !== '') $customers[$key]['name'] = trim((string)$reservation['customer_name']);
        $customers[$key]['reservations'][] = $reservation;
    }
    foreach ($protocols as $protocol) {
        $phone = (string)($protocol['phone'] ?? '');
        if ($phone === '') continue;
        $key = rm_phone_key($phone); if ($key === '') continue;
        if (!isset($customers[$key])) $customers[$key] = ['id'=>$key,'phone'=>$phone,'phone_key'=>$key,'name'=>'','notes'=>'','reservations'=>[],'protocols'=>[]];
        if (($customers[$key]['name'] ?? '') === '' && trim((string)($protocol['client'] ?? '')) !== '') $customers[$key]['name'] = trim((string)$protocol['client']);
        $customers[$key]['protocols'][] = $protocol;
    }
    foreach ($customers as &$customer) {
        if (($customer['name'] ?? '') === '') foreach (($customer['protocols'] ?? []) as $protocol) {
            if (trim((string)($protocol['client'] ?? '')) !== '') { $customer['name'] = trim((string)$protocol['client']); break; }
        }
    }
    unset($customer);
    // Do not expose settlement income/loss through nested customer history to non-admin devices.
    if ($role !== 'admin') {
        foreach ($customers as &$customer) {
            foreach ($customer['reservations'] as &$reservation) {
                $reservation=rm_hide_rental_finance($reservation);
            }
            unset($reservation);
        }
        unset($customer);
    }
    $inventory = rm_read_json_file(__DIR__ . '/data.json', []);
    $siteStats = rm_read_json_file(__DIR__ . '/stats.json', []);
    // Count recent public-site heartbeats. A session expires 95 seconds after its last ping.
    $onlineSessions = rm_read_json_file(__DIR__ . '/online_sessions.json', []);
    $onlineCount = 0;
    $onlineCutoff = time() - 95;
    foreach ($onlineSessions as $lastSeen) {
        if (is_numeric($lastSeen) && (int)$lastSeen >= $onlineCutoff && (int)$lastSeen <= time() + 60) $onlineCount++;
    }
    $siteStats['online_visitors'] = $onlineCount;
    $siteStats['online_window_seconds'] = 95;
    $equipment = [];
    foreach ($inventory as $product) {
        $name = trim((string)($product['name'] ?? ''));
        if ($name !== '') $equipment[$name] = true;
    }
    rm_json_response(200, [
        'ok'=>true, 'role'=>$role, 'server_time'=>date(DATE_ATOM),
        'assets'=>array_map(function($a)use($role){if($role!=='admin')foreach(($a['repairs']??[]) as $i=>$r)unset($a['repairs'][$i]['cost']);return $a;},rm_read_assets()),
        'protocols'=>array_values($protocols), 'customers'=>array_values($customers), 'expenses'=>($role === 'admin' ? array_values(rm_read_json_file(__DIR__ . '/costs.json', [])) : []), 'reservations'=>array_values(array_map(function($r) use ($role) { if ($role !== 'admin') { $r=rm_hide_rental_finance($r); } return $r; }, rm_read_json_file(__DIR__ . '/reservations.json', []))), 'equipment'=>array_keys($equipment), 'site_stats'=>($role === 'admin' ? $siteStats : []), 'site_products'=>($role === 'admin' ? array_values($inventory) : [])
    ]);
}
if ($method !== 'POST') rm_json_response(405, ['ok'=>false, 'error'=>'Niedozwolona metoda.']);

$raw = file_get_contents('php://input');
$body = is_string($raw) ? json_decode($raw, true) : null;
if (!is_array($body)) rm_json_response(400, ['ok'=>false, 'error'=>'Nieprawidłowy JSON.']);
$operations = $body['operations'] ?? [];
if (!is_array($operations) || count($operations) > 100) rm_json_response(400, ['ok'=>false, 'error'=>'Nieprawidłowa lista operacji.']);

$file = __DIR__ . '/protocols.json';
$fp = fopen($file, 'c+');
if (!$fp) rm_json_response(500, ['ok'=>false, 'error'=>'Nie można otworzyć bazy protokołów.']);
if (!flock($fp, LOCK_EX)) { fclose($fp); rm_json_response(503, ['ok'=>false, 'error'=>'Baza jest chwilowo zajęta.']); }
$rawDb = stream_get_contents($fp);
$protocols = json_decode((string)$rawDb, true);
if (!is_array($protocols)) $protocols = [];
$results = [];

foreach ($operations as $op) {
    if (!is_array($op)) { $results[]=['ok'=>false,'error'=>'Nieprawidłowa operacja']; continue; }
    $opId = preg_replace('/[^a-zA-Z0-9_-]/', '', (string)($op['op_id'] ?? ''));
    $action = (string)($op['action'] ?? '');
    if ($opId === '' || strlen($opId) > 80) { $results[]=['ok'=>false,'error'=>'Brak poprawnego identyfikatora operacji']; continue; }



    if ($action === 'asset_save') {
        if($role!=='admin'){$results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Tylko administrator może zmieniać egzemplarze.'];continue;}
        $id=preg_replace('/[^a-zA-Z0-9_-]/','',(string)($op['id']??''));$label=rm_text($op,'label',100);$equipment=rm_text($op,'equipment',200);
        if($id===''||$label===''||$equipment===''){$results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Uzupełnij egzemplarz.'];continue;}
        $assets=rm_read_assets();$invalid=false;$index=null;
        foreach($assets as $i=>$a){if(($a['id']??'')===$id){$index=$i;if(($a['equipment']??'')!==$equipment)$invalid=true;}elseif(($a['label']??'')===$label)$invalid=true;}
        $next=rm_text($op,'next_service',10);$date=$next===''?null:DateTimeImmutable::createFromFormat('!Y-m-d',$next);
        if($invalid||($next!==''&&(!$date||$date->format('Y-m-d')!==$next))){$results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Nazwa egzemplarza jest zajęta, zmieniono model lub data przeglądu jest błędna.'];continue;}
        $asset=['id'=>$id,'label'=>$label,'equipment'=>$equipment,'in_service'=>!empty($op['in_service']),'next_service'=>$next,'service_every'=>max(0,min(10000,(int)($op['service_every']??0))),'service_at_count'=>max(0,(int)($op['service_at_count']??0)),'asset_kind'=>(in_array(($op['asset_kind']??''),['standard','giraffe','vacuum'],true)?$op['asset_kind']:(($index!==null&&isset($assets[$index]['asset_kind']))?$assets[$index]['asset_kind']:rm_equipment_kind($equipment)))];
        // Merge immutable repair entries by ID so another device cannot erase service history.
        $repairs=[];foreach(($index===null?[]:($assets[$index]['repairs']??[])) as $r)$repairs[$r['id']]=$r;
        foreach(($op['repairs']??[]) as $r){if(!is_array($r))continue;$rid=preg_replace('/[^a-zA-Z0-9_-]/','',(string)($r['id']??''));$cost=filter_var($r['cost']??null,FILTER_VALIDATE_FLOAT);if($rid===''||$cost===false||!is_finite($cost)||$cost<0||$cost>10000000)continue;if(!isset($repairs[$rid]))$repairs[$rid]=['id'=>$rid,'date'=>rm_text($r,'date',10),'note'=>rm_text($r,'note',1000),'cost'=>round($cost,2)];}
        $asset['repairs']=array_values($repairs);if($index===null)$assets[]=$asset;else $assets[$index]=$asset;
        $ok=rm_write_assets($assets);$results[]=['op_id'=>$opId,'ok'=>$ok,'error'=>$ok?'':'Nie udało się zapisać egzemplarza.'];continue;
    }
    if ($action === 'expense_save' || $action === 'expense_delete') {
        if ($role !== 'admin') { $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Koszty są dostępne tylko dla administratora.']; continue; }
        $costFile=__DIR__.'/costs.json'; $costLock=fopen($costFile.'.lock','c+');
        if (!$costLock || !flock($costLock,LOCK_EX)) { if($costLock)fclose($costLock); $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Lista kosztów jest chwilowo zajęta.']; continue; }
        $costs=rm_read_json_file($costFile,[]); $id=preg_replace('/[^a-zA-Z0-9_-]/','',(string)($op['id']??''));
        if($id===''){flock($costLock,LOCK_UN);fclose($costLock);$results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Brak identyfikatora kosztu.'];continue;}
        if($action==='expense_save'){
            $date=rm_text($op,'date',10);$name=rm_text($op,'name',160);$month=rm_text($op,'month',7);
            $amount=filter_var($op['amount']??null,FILTER_VALIDATE_FLOAT);$type=(($op['type']??'')==='investment')?'investment':'expense';
            if(!preg_match('/^\\d{4}-\\d{2}-\\d{2}$/',$date)||!preg_match('/^\\d{4}-\\d{2}$/',$month)||substr($date,0,7)!==$month||$name===''||$amount===false||$amount<=0||$amount>100000000){flock($costLock,LOCK_UN);fclose($costLock);$results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Nieprawidłowe dane kosztu.'];continue;}
            $rawType=(string)($op['type']??'');$type=($rawType==='investment')?'investment':'expense';
            $item=['id'=>$id,'date'=>$date,'month'=>$month,'name'=>$name,'amount'=>round((float)$amount,2),'type'=>$type,'equipment_name'=>rm_text($op,'equipment_name',160),'note'=>rm_text($op,'note',1000)];
            $found=false;foreach($costs as &$saved){if((string)($saved['id']??'')===$id){$saved=$item;$found=true;break;}}unset($saved);if(!$found)$costs[]=$item;
        } else {$costs=array_values(array_filter($costs,fn($saved)=>(string)($saved['id']??'')!==$id));}
        $json=json_encode(array_values($costs),JSON_PRETTY_PRINT|JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);$tmp=$costFile.'.'.bin2hex(random_bytes(6)).'.tmp';
        $ok=$json!==false&&file_put_contents($tmp,$json,LOCK_EX)!==false&&rename($tmp,$costFile);if(!$ok&&is_file($tmp))@unlink($tmp);
        flock($costLock,LOCK_UN);fclose($costLock);$results[]=$ok?['op_id'=>$opId,'ok'=>true,'id'=>$id]:['op_id'=>$opId,'ok'=>false,'error'=>'Nie udało się zapisać kosztów.'];continue;
    }

    if ($action === 'customer_save') {
        if ($role !== 'admin') { $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Edycja kart klientów jest dostępna tylko dla administratora.']; continue; }
        $phone = rm_text($op, 'phone', 50); $key = rm_phone_key($phone);
        if ($key === '') { $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Podaj prawidłowy numer telefonu.']; continue; }
        $customerFile=__DIR__.'/customers.json'; $customerLock=fopen($customerFile.'.lock','c+');
        if (!$customerLock || !flock($customerLock,LOCK_EX)) { if($customerLock)fclose($customerLock); $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Baza klientów jest chwilowo zajęta.']; continue; }
        $customers=rm_read_json_file($customerFile,[]); $found=false;
        $item=['id'=>$key,'phone'=>$phone,'phone_key'=>$key,'name'=>rm_text($op,'name',200),'notes'=>rm_text($op,'notes',4000)];
        foreach($customers as &$saved) { if(rm_phone_key((string)($saved['phone']??''))===$key) { $item['phone']=$saved['phone']??$phone; $saved=array_merge($saved,$item); $found=true; break; } } unset($saved);
        if(!$found)$customers[]=$item;
        $ok=rm_write_json_atomic($customerFile,$customers);
        flock($customerLock,LOCK_UN); fclose($customerLock);
        $results[]=$ok?['op_id'=>$opId,'ok'=>true,'id'=>$key]:['op_id'=>$opId,'ok'=>false,'error'=>'Nie udało się zapisać karty klienta.'];
        continue;
    }

    if ($action === 'product_save' || $action === 'product_delete') {
        if ($role !== 'admin') { $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Zarządzanie ofertą jest dostępne tylko dla administratora.']; continue; }
        $file = __DIR__ . '/data.json';
        $lock = fopen($file . '.lock', 'c+');
        if (!$lock || !flock($lock, LOCK_EX)) { if ($lock) fclose($lock); $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Baza sprzętu jest chwilowo zajęta.']; continue; }
        $items = rm_read_json_file($file, []);
        $id = preg_replace('/[^a-zA-Z0-9_-]/', '', (string)($op['product_id'] ?? $op['id'] ?? ''));
        if ($action === 'product_delete') {
            $before=count($items);
            $items=array_values(array_filter($items,fn($p)=>(string)($p['id']??'')!==$id));
            $ok=$id!=='' && count($items)<$before;
        } else {
            $name=rm_text($op,'name',160); $desc=rm_text($op,'desc',4000);
            $category=rm_text($op,'category',40);
            if(!in_array($category,['budowlane','ogrodnicze','czyszczace'],true)) $category='budowlane';
            $price=filter_var(str_replace(',','.',(string)($op['price']??'')),FILTER_VALIDATE_FLOAT);
            $deposit=filter_var(str_replace(',','.',(string)($op['deposit']??'0')),FILTER_VALIDATE_FLOAT);
            if($name==='' || $price===false || $price<0 || $deposit===false || $deposit<0) {
                flock($lock,LOCK_UN); fclose($lock); $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Podaj nazwę oraz prawidłową cenę i kaucję.']; continue;
            }
            $found=-1; foreach($items as $idx=>$p) if((string)($p['id']??'')===$id && $id!=='') {$found=$idx;break;}
            if($id==='') $id=bin2hex(random_bytes(10));
            $old=$found>=0?$items[$found]:[];
            $images=is_array($old['images']??null)?$old['images']:[];
            $photoData=$op['photos_base64']??[];
            if(is_array($photoData)) foreach(array_slice($photoData,0,6) as $encoded) {
                if(!is_string($encoded) || strlen($encoded)>4000000) continue;
                $binary=base64_decode($encoded,true); if($binary===false || strlen($binary)>2800000) continue;
                $info=@getimagesizefromstring($binary);
                if(!$info || !in_array($info['mime'],['image/jpeg','image/png','image/webp'],true)) continue;
                $ext=['image/jpeg'=>'jpg','image/png'=>'png','image/webp'=>'webp'][$info['mime']];
                $dir=__DIR__.'/uploads'; if(!is_dir($dir) && !mkdir($dir,0755,true)) continue;
                $filename='sprzet_'.bin2hex(random_bytes(12)).'.'.$ext;
                if(file_put_contents($dir.'/'.$filename,$binary,LOCK_EX)!==false) $images[]=$filename;
            }
            $item=['id'=>$id,'name'=>htmlspecialchars($name,ENT_QUOTES,'UTF-8'),'category'=>$category,
                'desc'=>htmlspecialchars($desc,ENT_QUOTES,'UTF-8'),'seo_title'=>rm_text($op,'seo_title',180),
                'seo_description'=>rm_text($op,'seo_description',320),'price'=>round((float)$price,2),
                'deposit'=>round((float)$deposit,2),'olx'=>rm_text($op,'olx',500),'images'=>$images,
                'date'=>date('Y-m-d H:i'),'phone_clicks'=>(int)($old['phone_clicks']??0)];
            if($found>=0) $items[$found]=$item; else array_unshift($items,$item);
            $ok=true;
        }
        $ok=$ok && rm_write_json_atomic($file,$items);
        flock($lock,LOCK_UN); fclose($lock);
        $results[]=$ok?['op_id'=>$opId,'ok'=>true,'id'=>$id]:['op_id'=>$opId,'ok'=>false,'error'=>'Nie udało się zapisać oferty sprzętu.'];
        continue;
    }

    if ($action === 'equipment_add') {
        if ($role !== 'admin') { $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Dodawanie sprzętu jest dostępne tylko dla administratora.']; continue; }
        $name = rm_text($op, 'name', 120);
        if ($name === '') { $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Podaj nazwę sprzętu.']; continue; }
        $file = __DIR__ . '/data.json';
        $lock = fopen($file . '.lock', 'c+');
        if (!$lock || !flock($lock, LOCK_EX)) { if ($lock) fclose($lock); $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Lista sprzętu jest chwilowo zajęta.']; continue; }
        $inventory = rm_read_json_file($file, []);
        $exists = false;
        foreach ($inventory as $product) if (isset($product['name']) && mb_strtolower(trim((string)$product['name']), 'UTF-8') === mb_strtolower($name, 'UTF-8')) { $exists=true; break; }
        if (!$exists) { $inventory[] = ['name'=>$name]; $ok = rm_write_json_atomic($file, $inventory); }
        else $ok = true;
        flock($lock, LOCK_UN); fclose($lock);
        $results[]=$ok?['op_id'=>$opId,'ok'=>true,'name'=>$name,'exists'=>$exists]:['op_id'=>$opId,'ok'=>false,'error'=>'Nie udało się zapisać wspólnej listy sprzętu.'];
        continue;
    }

    if ($action === 'reservation_save' || $action === 'reservation_delete' || $action === 'reservation_settle') {
        if ($action === 'reservation_settle' && $role !== 'admin') {
            $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Rozliczanie najmu jest dostępne tylko dla administratora.'];
            continue;
        }
        $reservationFile = __DIR__ . '/reservations.json';
        $reservationLock = fopen($reservationFile . '.lock', 'c+');
        if (!$reservationLock || !flock($reservationLock, LOCK_EX)) {
            if ($reservationLock) fclose($reservationLock);
            $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Lista rezerwacji jest chwilowo zajęta.']; continue;
        }
        $reservations = rm_read_json_file($reservationFile, []);
        $id = preg_replace('/[^a-zA-Z0-9_-]/', '', (string)($op['id'] ?? ''));
        if ($id === '') {
            flock($reservationLock, LOCK_UN); fclose($reservationLock);
            $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Brak identyfikatora rezerwacji.']; continue;
        }
        if ($action === 'reservation_save') {
            $month = rm_text($op, 'month', 7);
            $text = rm_text($op, 'text', 500);
            if (!preg_match('/^\d{4}-\d{2}$/', $month) || $text === '') {
                flock($reservationLock, LOCK_UN); fclose($reservationLock);
                $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Nieprawidłowa rezerwacja.']; continue;
            }
            $item=['id'=>$id,'month'=>$month,'text'=>$text,'done'=>!empty($op['done'])];
            foreach (['equipment','day','start_date','end_date','end_day','end_month','time','phone','client','customer_name','asset_id','asset_label','vacuum_asset_id','vacuum_asset_label','actual_return'] as $field) {
                if (array_key_exists($field,$op)) $item[$field] = rm_text($op,$field,($field==='text'?500:200));
            }
            $existing=null;foreach($reservations as $saved)if(($saved['id']??'')===$id)$existing=$saved;
            // Preserve new metadata when an older APK edits a reservation.
            foreach(['asset_id','asset_label','vacuum_asset_id','vacuum_asset_label','needs_vacuum','actual_return','returned','daily_rate','paid','deposit'] as $key){if($existing!==null&&array_key_exists($key,$existing)&&!array_key_exists($key,$op))$item[$key]=$existing[$key];}
            if(array_key_exists('returned',$op))$item['returned']=!empty($op['returned']);
            $bad=false;
            foreach(['daily_rate','paid','deposit'] as $key){
                if($role!=='admin'){if($existing!==null&&array_key_exists($key,$existing))$item[$key]=$existing[$key];continue;}
                if(array_key_exists($key,$op)){$value=filter_var($op[$key],FILTER_VALIDATE_FLOAT);if($value===false||!is_finite($value)||$value<0||$value>10000000){$bad=true;break;}$item[$key]=round($value,2);}
            }
            if(array_key_exists('needs_vacuum',$op))$item['needs_vacuum']=!empty($op['needs_vacuum']);
            if(!array_key_exists('needs_vacuum',$item))$item['needs_vacuum']=rm_needs_vacuum($item);
            $assets=rm_read_assets();
            $autoError='';
            if(!$bad&&!empty($op['auto_assign'])&&empty($item['done'])&&empty($item['returned'])){
                // Always re-evaluate on the server; another phone may have booked the locally selected copy.
                $chosen=rm_auto_pick_asset($item,$reservations,$assets);
                $autoError=$chosen['error'];
                $item['asset_id']=$chosen['asset']['id']??'';
                $item['asset_label']=$chosen['asset']['label']??'';
                $item['vacuum_asset_id']=$chosen['vacuum']['id']??'';
                $item['vacuum_asset_label']=$chosen['vacuum']['label']??'';
            }
            $conflict=$bad?'Nieprawidłowa kwota.':($autoError!==''?$autoError:rm_rental_conflict($item,$reservations,$assets));
            if($conflict!==''){flock($reservationLock,LOCK_UN);fclose($reservationLock);$results[]=['op_id'=>$opId,'ok'=>false,'error'=>$conflict];continue;}
            $found=false;
            foreach ($reservations as &$saved) { if (($saved['id'] ?? '') === $id) {
                foreach (['settled','income','loss','loss_note','settled_at','settlement'] as $field) if (array_key_exists($field,$saved)) $item[$field]=$saved[$field];
                $saved=$item; $found=true; break;
            } }
            unset($saved);
            if (!$found) $reservations[]=$item;
        } elseif ($action === 'reservation_settle') {
            $income = filter_var($op['income'] ?? null, FILTER_VALIDATE_FLOAT);
            $loss = filter_var($op['loss'] ?? null, FILTER_VALIDATE_FLOAT);
            $found = false;
            if ($income === false || $loss === false || $income < 0 || $loss < 0 || !is_finite($income) || !is_finite($loss) || $income>10000000 || $loss>10000000) {
                flock($reservationLock, LOCK_UN); fclose($reservationLock);
                $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Nieprawidłowe kwoty rozliczenia.']; continue;
            }
            // New APKs submit an all-or-nothing category breakdown. Older versions
            // still submit settlements without these keys and remain supported.
            $addonKeys=['add_powder','add_paper','add_disc','add_other'];
            $details=(isset($op['settlement'])&&is_array($op['settlement']))?$op['settlement']:null;
            if($details!==null){
                $provided=0;foreach($addonKeys as $key)if(array_key_exists($key,$details))$provided++;
                if($provided>0){
                    $valid=$provided===count($addonKeys);
                    $gross=0;
                    foreach($addonKeys as $key){
                        if(!$valid)break;
                        $raw=$details[$key]??null;
                        $v=is_scalar($raw)?filter_var(str_replace(',','.',(string)$raw),FILTER_VALIDATE_FLOAT):false;
                        if($v===false||!is_finite((float)$v)||$v<0||$v>10000000){$valid=false;break;}
                        $details[$key]=round((float)$v,2);$gross+=(int)round($v*100);
                    }
                    $extraRaw=$details['extra']??null;
                    $extra=is_scalar($extraRaw)?filter_var(str_replace(',','.',(string)$extraRaw),FILTER_VALIDATE_FLOAT):false;
                    if($extra===false||!is_finite((float)$extra)||$extra<0||$extra>10000000||$gross!==(int)round($extra*100))$valid=false;
                    if(!$valid){
                        flock($reservationLock,LOCK_UN);fclose($reservationLock);
                        $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Sprawdź kwoty dodatków. Suma musi zgadzać się z dopłatami.'];continue;
                    }
                    $op['settlement']=$details;
                }
            }
            foreach ($reservations as &$saved) if ((string)($saved['id'] ?? '') === $id) {
                $saved['settled']=true; $saved['income']=round((float)$income,2); $saved['loss']=round((float)$loss,2);
                if(isset($op['settlement'])&&is_array($op['settlement'])){$saved['settlement']=array_intersect_key($op['settlement'],array_flip(['days','rate','extra','discount','paid','deposit','retained','due','deposit_refund','add_powder','add_paper','add_disc','add_other']));$saved['returned']=true;$saved['actual_return']=rm_text($op,'actual_return',10);}
                $saved['loss_note']=rm_text($op,'loss_note',300); $saved['settled_at']=date('Y-m-d H:i:s'); $found=true; break;
            }
            unset($saved);
            if (!$found) {
                flock($reservationLock, LOCK_UN); fclose($reservationLock);
                $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Nie znaleziono rezerwacji do rozliczenia.']; continue;
            }
        } else {
            if ($role !== 'admin') {
                flock($reservationLock, LOCK_UN); fclose($reservationLock);
                $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Usuwanie rezerwacji jest dostępne tylko dla administratora.']; continue;
            }
            $reservations=array_values(array_filter($reservations, fn($saved)=>(string)($saved['id'] ?? '') !== $id));
        }
        $json=json_encode(array_values($reservations), JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        $tmp=$reservationFile.'.'.bin2hex(random_bytes(6)).'.tmp';
        $ok=$json!==false && file_put_contents($tmp,$json,LOCK_EX)!==false && rename($tmp,$reservationFile);
        if (!$ok && is_file($tmp)) @unlink($tmp);
        flock($reservationLock, LOCK_UN); fclose($reservationLock);
        if (!$ok) { $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Nie udało się zapisać listy rezerwacji.']; continue; }
        $results[]=['op_id'=>$opId,'ok'=>true,'id'=>$id];
        continue;
    }

    // Idempotency: replaying the same create/return is safe.
    $already = false;
    foreach ($protocols as $saved) {
        if (($saved['sync_op_id'] ?? '') === $opId || ($saved['return_sync_op_id'] ?? '') === $opId) { $already = true; break; }
    }
    if ($already) { $results[]=['op_id'=>$opId,'ok'=>true,'duplicate'=>true]; continue; }

    if ($action === 'create') {
        $client = rm_text($op, 'client', 200);
        $equipment = rm_text($op, 'equipment', 200);
        if ($client === '' || $equipment === '') { $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Wymagane są dane klienta i sprzętu']; continue; }
        $id = preg_replace('/[^a-zA-Z0-9_-]/', '', (string)($op['id'] ?? ''));
        if ($id === '' || strlen($id) > 80) $id = bin2hex(random_bytes(8));
        $exists = false;
        foreach ($protocols as $saved) if (($saved['id'] ?? '') === $id) { $exists = true; break; }
        if ($exists) { $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Identyfikator protokołu już istnieje']; continue; }
        $number = preg_replace('/[^A-Z0-9-]/', '', strtoupper(rm_text($op, 'number', 40)));
        if ($number === '') $number = 'RM-' . date('Ymd') . '-' . strtoupper(substr($id, 0, 4));
        $protocols[] = [
            'id'=>$id, 'number'=>$number, 'status'=>'wydany', 'created_at'=>date('Y-m-d H:i:s'),
            'asset_id'=>rm_text($op,'asset_id',80),'asset_label'=>rm_text($op,'asset_label',100),
            'client'=>$client, 'phone'=>rm_text($op,'phone',50), 'equipment'=>$equipment, 'reservation_id'=>preg_replace('/[^a-zA-Z0-9_-]/', '', (string)($op['reservation_id'] ?? '')),
            'rental_date'=>rm_text($op,'rental_date',40), 'condition'=>rm_text($op,'condition',2000),
            'accessories'=>rm_text($op,'accessories',2000), 'notes'=>rm_text($op,'notes',2000),
            'return_date'=>'', 'return_condition'=>'', 'return_notes'=>'', 'sync_op_id'=>$opId
        ];
        if (isset($op['photos_base64']) && is_array($op['photos_base64'])) {
            $photos = array_values(array_filter($op['photos_base64'], fn($photo) => is_string($photo) && $photo !== '' && strlen($photo) <= 2500000));
            if (count($photos) > 0) {
                $protocols[count($protocols)-1]['photos_base64'] = $photos;
                $protocols[count($protocols)-1]['photos_uploaded_at'] = date('Y-m-d H:i:s');
                $protocols[count($protocols)-1]['photos_expired'] = false;
            }
        }
        foreach (['signature_base64'] as $imageField) {
            if (!empty($op[$imageField]) && is_string($op[$imageField]) && strlen($op[$imageField]) <= 1500000)
                $protocols[count($protocols)-1][$imageField] = $op[$imageField];
        }
        $results[]=['op_id'=>$opId,'ok'=>true,'id'=>$id,'number'=>$number];
    } elseif ($action === 'return') {
        $id = preg_replace('/[^a-zA-Z0-9_-]/', '', (string)($op['id'] ?? ''));
        $found = false; $conflict = false;
        foreach ($protocols as &$item) {
            if (($item['id'] ?? '') !== $id) continue;
            $found = true;
            if (($item['status'] ?? '') === 'zwrócony') { $conflict = true; break; }
            $item['status'] = 'zwrócony';
            $item['return_date'] = rm_text($op,'return_date',40);
            $item['return_condition'] = rm_text($op,'return_condition',2000);
            $item['return_notes'] = rm_text($op,'return_notes',2000);
            if (!empty($op['return_signature_base64']) && is_string($op['return_signature_base64']) && strlen($op['return_signature_base64']) <= 1500000)
                $item['return_signature_base64'] = $op['return_signature_base64'];
            if(isset($op['return_checklist'])&&is_array($op['return_checklist']))$item['return_checklist']=array_slice($op['return_checklist'],0,100);
            if(isset($op['return_photos_base64'])&&is_array($op['return_photos_base64'])){$item['return_photos_base64']=array_slice(array_values(array_filter($op['return_photos_base64'],fn($photo)=>is_string($photo)&&strlen($photo)<=2500000)),0,12);$item['return_photos_uploaded_at']=date('Y-m-d H:i:s');}
            $item['return_sync_op_id'] = $opId;
            break;
        }
        unset($item);
        if (!$found) $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Nie znaleziono protokołu na serwerze. Najpierw zsynchronizuj wydanie.'];
        elseif ($conflict) $results[]=['op_id'=>$opId,'ok'=>false,'conflict'=>true,'error'=>'Ten protokół został już zwrócony na innym urządzeniu.'];
        else $results[]=['op_id'=>$opId,'ok'=>true,'id'=>$id];
    } elseif ($action === 'delete') {
        if ($role !== 'admin') {
            $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Usuwanie protokołów jest dostępne tylko dla administratora.'];
            continue;
        }
        $id = preg_replace('/[^a-zA-Z0-9_-]/', '', (string)($op['id'] ?? ''));
        $before = count($protocols);
        $protocols = array_values(array_filter($protocols, function($saved) use ($id) {
            return (string)($saved['id'] ?? '') !== $id;
        }));
        $results[]=['op_id'=>$opId,'ok'=>true,'id'=>$id,'already_deleted'=>(count($protocols) === $before)];
    } else {
        $results[]=['op_id'=>$opId,'ok'=>false,'error'=>'Nieobsługiwana operacja'];
    }
}
rewind($fp);
$encoded = json_encode(array_values($protocols), JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
$writeOk = $encoded !== false && ftruncate($fp, 0) && fwrite($fp, $encoded) !== false && fflush($fp);
flock($fp, LOCK_UN);
fclose($fp);
if (!$writeOk) rm_json_response(500, ['ok'=>false,'error'=>'Nie udało się zapisać danych. Operacje należy ponowić.']);
rm_json_response(200, ['ok'=>true,'role'=>$role,'results'=>$results]);
