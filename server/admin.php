<?php
require_once __DIR__ . '/private_rental_files.php';
if (rm_rental_maintenance()) { http_response_code(503); header('Retry-After: 30'); exit('Trwa bezpieczne przenoszenie danych najmu.'); }
require_once __DIR__ . '/admin_safety.php';
rm_admin_start_session();

// PHP may discard the entire POST when post_max_size is exceeded.
// Never silently re-render a form as if an oversized upload had succeeded.
if (($_SERVER['REQUEST_METHOD'] ?? '') === 'POST' && (int)($_SERVER['CONTENT_LENGTH'] ?? 0) > 0
    && empty($_POST) && empty($_FILES)) {
    http_response_code(413);
    exit('Zbyt duży formularz lub zdjęcia. Zmniejsz pliki i spróbuj ponownie.');
}

require_once __DIR__.'/admin_auth.php';
try {$rm_auth=rm_auth_read();}
catch(Throwable $e){http_response_code(503);exit('Konfiguracja logowania jest niedostępna.');}
if(empty($_SESSION['login_csrf']))$_SESSION['login_csrf']=bin2hex(random_bytes(32));
if(!empty($_SESSION['logged_in'])&&empty($_SESSION['rm_mfa_verified']))unset($_SESSION['logged_in']);
if(!empty($_SESSION['logged_in'])&&(!isset($_SESSION['rm_password_version'])
    || !hash_equals((string)$_SESSION['rm_password_version'],hash('sha256',$rm_auth['password_hash'])))){
    unset($_SESSION['logged_in'],$_SESSION['rm_mfa_verified']);
}

$data_file = 'data.json';
$stats_file = 'stats.json';
$msgs_file = 'messages.json';
$upload_dir = 'uploads/';

function e_admin($value) {
    return htmlspecialchars((string)($value ?? ''), ENT_QUOTES, 'UTF-8');
}

// Revenue is attributed to settlement date, consistently with Android reports.
function rm_admin_settlement_month(array $r): string {
    if (empty($r['settled'])) return '';
    $day=substr((string)($r['settled_at']??''),0,10);
    $date=DateTimeImmutable::createFromFormat('!Y-m-d',$day);
    return $date && $date->format('Y-m-d')===$day ? substr($day,0,7) : '';
}
function rm_admin_equipment_name(array $r): string {
    $name=trim((string)($r['equipment']??''));
    if($name===''){
        $raw=(string)($r['text']??'');
        $parts=explode('•',$raw);
        if(count($parts)>=2)$name=trim($parts[1]);
        else {
            $name=preg_replace('/^\\s*\\d{1,2}\\s+/u','',trim($raw));
            $name=preg_replace('/\\s+\\d{1,2}\\s*\\/.*$/u','',$name);
        }
    }
    $name=preg_replace('/\\s+-\\s+wynajem.*$/iu','',$name);
    $name=preg_replace('/\\s+\\|.*$/u','',$name);
    $name=trim(preg_replace('/\\s+/u',' ',$name));
    return $name===''?'Nieprzypisany sprzęt':$name;
}

function rm_admin_phone_key($value) {
    $value = trim((string)$value);
    if ($value === '' || !preg_match('/^[+0-9() .\\-]+$/', $value)) return '';
    $digits = preg_replace('/\\D+/', '', $value);
    if (strlen($digits) === 11 && str_starts_with($digits, '48')) $digits = substr($digits, 2);
    if (strlen($digits) === 9) return $digits;
    if (strlen($digits) >= 10 && strlen($digits) <= 15) return '+' . $digits;
    return '';
}

$rm_pending=!empty($_SESSION['rm_pending']) && time()-(int)$_SESSION['rm_pending']<=300 && (int)$_SESSION['rm_pending']<=time();
if(!$rm_pending)unset($_SESSION['rm_pending'],$_SESSION['rm_setup_secret'],$_SESSION['rm_otp_tries']);
$rm_csrf_ok=($_SERVER['REQUEST_METHOD']??'')==='POST' && is_string($_POST['login_csrf']??null)
    && hash_equals($_SESSION['login_csrf'],$_POST['login_csrf']);
if(isset($_POST['login_pass'])){
    if(!$rm_csrf_ok||$rm_pending)$error='Odśwież stronę logowania.';
    else {
        $result=rm_admin_check_password(is_string($_POST['login_pass'])?$_POST['login_pass']:'',$rm_auth['password_hash']);
        if($result['retry_after']>0){
            rm_security_log('password_rate_limited');
            http_response_code(429);header('Retry-After: '.$result['retry_after']);
            $error='Zbyt wiele prób. Spróbuj ponownie za około 15 minut.';
        }elseif($result['ok']){
            rm_security_log('password_ok');
            session_regenerate_id(true);
            $_SESSION['rm_pending']=time();$_SESSION['rm_otp_tries']=0;
            $_SESSION['login_csrf']=bin2hex(random_bytes(32));
            if($rm_auth['totp_secret']==='')$_SESSION['rm_setup_secret']=rm_totp_secret();
            $rm_pending=true;
        }else{rm_security_log('password_failed');$error='Błędne hasło!';}
    }
}
if(isset($_POST['totp_code'])){
    if(!$rm_csrf_ok||!$rm_pending)$error='Sesja wygasła, zaloguj się ponownie.';
    else {
        $tries=++$_SESSION['rm_otp_tries'];
        $secret=$rm_auth['totp_secret']!==''?$rm_auth['totp_secret']:(string)($_SESSION['rm_setup_secret']??'');
        $match=$tries<=5 && rm_totp_verify($secret,(string)$_POST['totp_code']);
        $valid=rm_otp_limit($match);
        if($valid && $rm_auth['totp_secret']===''){
            $valid=rm_auth_enroll($secret);
            if($valid)rm_security_log('otp_enrolled');
        }
        if($valid){
            rm_security_log('otp_ok');
            session_regenerate_id(true);
            $_SESSION['logged_in']=true;$_SESSION['rm_mfa_verified']=true;$_SESSION['rm_password_version']=hash('sha256',$rm_auth['password_hash']);
            $_SESSION['admin_authenticated_at']=time();$_SESSION['admin_last_activity']=time();
            unset($_SESSION['rm_pending'],$_SESSION['rm_setup_secret'],$_SESSION['rm_otp_tries']);
            $rm_pending=false;
        }else {
            rm_security_log('otp_failed');
            $error='Nieprawidłowy kod.';
            if($tries>=5){unset($_SESSION['rm_pending'],$_SESSION['rm_setup_secret'],$_SESSION['rm_otp_tries']);$rm_pending=false;$error='Limit kodów. Zaloguj się ponownie hasłem.';}
        }
    }
}
if(isset($_GET['logout'])){session_destroy();header('Location: admin.php');exit();}
$is_logged=!empty($_SESSION['logged_in'])&&!empty($_SESSION['rm_mfa_verified']);
require_once __DIR__ . '/traffic_filter.php';
if ($is_logged) rm_stats_remember_owner();

// Protect legacy mutations as well as the individually protected newer forms.
if ($is_logged && empty($_SESSION['admin_csrf'])) $_SESSION['admin_csrf'] = bin2hex(random_bytes(32));
if ($is_logged && ($_POST['action'] ?? '') === 'change_admin_password'
    && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST') {
    $csrf=is_string($_POST['admin_csrf']??null) ? $_POST['admin_csrf'] : '';
    if(!hash_equals($_SESSION['admin_csrf'],$csrf)){
        http_response_code(403);exit('Sesja wygasła. Odśwież formularz.');
    }
    $old=is_string($_POST['password_old']??null) ? $_POST['password_old'] : '';
    $new=is_string($_POST['password_new']??null) ? $_POST['password_new'] : '';
    $repeat=is_string($_POST['password_repeat']??null) ? $_POST['password_repeat'] : '';
    $otp=is_string($_POST['password_otp']??null) ? $_POST['password_otp'] : '';
    if($new!==$repeat){
        $_SESSION['password_flash']='Nowe hasła nie są jednakowe.';
    }elseif(strlen($new)<14||strlen($new)>72){
        $_SESSION['password_flash']='Nowe hasło powinno mieć od 14 do 72 bajtów.';
    }elseif(rm_auth_rotate_password($old,$new,$otp)){
        rm_security_log('password_changed');
        $_SESSION=[];
        session_regenerate_id(true);
        session_destroy();
        header('Location: admin.php?password_changed=1');exit();
    }else {
        $_SESSION['password_flash']='Nie udało się zmienić hasła. Sprawdź obecne hasło i kod 2FA.';
    }
    header('Location: admin.php?tab=devices');exit();
}

if ($is_logged && isset($_GET['reset_phones'])) { http_response_code(405); exit('Użyj przycisku resetowania w panelu.'); }
$legacyActions = ['save_terms','add','update','delete','delete_msg','reset_phones'];
if ($is_logged && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST' && in_array($_POST['action'] ?? '', $legacyActions, true)) {
    if (!is_string($_POST['admin_csrf'] ?? null) || !hash_equals($_SESSION['admin_csrf'], $_POST['admin_csrf'])) {
        http_response_code(403); exit('Sesja wygasła. Odśwież formularz i spróbuj ponownie.');
    }
}
$inventoryHandle = null;
if ($is_logged && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST' && in_array($_POST['action'] ?? '', ['add','update','delete','reset_phones'], true)) {
    // Hold the same file lock across the entire read/modify/write transaction.
    $inventoryHandle = rm_admin_inventory_open($data_file);
    register_shutdown_function(static function () use ($inventoryHandle) { flock($inventoryHandle, LOCK_UN); fclose($inventoryHandle); });
}

// Rozliczanie rezerwacji – dane współdzielone z aplikacją Android.
if ($is_logged && empty($_SESSION['reservation_csrf'])) $_SESSION['reservation_csrf'] = bin2hex(random_bytes(32));
if ($is_logged && $_SERVER['REQUEST_METHOD'] === 'POST' && in_array(($_POST['action'] ?? ''), ['reservation_add','reservation_update','reservation_delete','reservation_toggle'], true)) {
    $action = (string)$_POST['action'];
    if (!hash_equals($_SESSION['reservation_csrf'] ?? '', (string)($_POST['reservation_csrf'] ?? ''))) {
        $_SESSION['reservation_flash'] = 'Sesja wygasła. Odśwież stronę i spróbuj ponownie.';
    } else {
        $rf = rm_rental_file('reservations.json');
        $lf = fopen($rf . '.lock', 'c+');
        if (!$lf || !flock($lf, LOCK_EX)) {
            if ($lf) fclose($lf);
            $_SESSION['reservation_flash'] = 'Lista rezerwacji jest chwilowo zajęta.';
        } else {
            $items = rm_admin_read_json_list($rf);
            $id = preg_replace('/[^a-zA-Z0-9_-]/', '', (string)($_POST['reservation_id'] ?? ''));
            $found = false;
            if ($action === 'reservation_delete') {
                $before = count($items);
                $items = array_values(array_filter($items, function($r) use ($id) { return (string)($r['id'] ?? '') !== $id; }));
                $found = $id !== '' && count($items) < $before;
            } elseif ($action === 'reservation_toggle') {
                foreach ($items as &$r) if ((string)($r['id'] ?? '') === $id) { $candidate=$r; $candidate['done']=!empty($_POST['reservation_done']); $reason=$candidate['done']?rm_admin_assign_reservation($candidate,$items):''; if($reason===''){$r=$candidate;$found=true;}else{$_SESSION['reservation_flash']=$reason;} break; }
                unset($r);
            } else {
                $start = trim((string)($_POST['start_date'] ?? ''));
                $finish = trim((string)($_POST['end_date'] ?? ''));
                $equipment = trim((string)($_POST['equipment'] ?? ''));
                $time = trim((string)($_POST['time'] ?? ''));
                $phone = trim((string)($_POST['phone'] ?? ''));
                $customerName = trim((string)($_POST['customer_name'] ?? ''));
                $legacyText = trim((string)($_POST['reservation_text'] ?? ''));
                $validDates = rm_valid_reservation_dates(['start_date'=>$start,'end_date'=>$finish]);
                if (!$validDates || ($equipment === '' && $legacyText === '') || strlen($equipment)>200 || strlen($phone)>100 || ($time !== '' && !rm_admin_valid_time($time))) {
                    $_SESSION['reservation_flash'] = 'Uzupełnij sprzęt i prawidłowy zakres dat. Godzina powinna mieć format 18:00.';
                } else {
                    $month = substr($start,0,7);
                    $text = $legacyText !== '' && $equipment === '' ? $legacyText : $start . ' – ' . $finish . ($time !== '' ? ' '.$time : '') . ' • ' . $equipment . ($phone !== '' ? ' • '.$phone : '');
                    $item = ['id'=>($action==='reservation_update'?$id:bin2hex(random_bytes(12))),'month'=>$month,'text'=>$text,'equipment'=>$equipment,'day'=>(int)substr($start,8,2),'start_date'=>$start,'end_date'=>$finish,'end_day'=>(int)substr($finish,8,2),'end_month'=>substr($finish,0,7),'time'=>$time,'phone'=>$phone,'customer_name'=>$customerName,'done'=>!empty($_POST['reservation_done']),'settled'=>false];
                    if ($action === 'reservation_update') {
                        foreach ($items as &$saved) if ((string)($saved['id'] ?? '') === $id) { foreach (['settled','income','loss','loss_note','settled_at','created_at'] as $field) if (array_key_exists($field,$saved)) $item[$field]=$saved[$field]; $item=array_merge($saved,$item); if(($saved['equipment']??'')!==$equipment){$item['asset_id']='';$item['asset_label']='';$item['vacuum_asset_id']='';$item['vacuum_asset_label']='';unset($item['needs_vacuum']);} $reason=rm_admin_assign_reservation($item,$items); if($reason!==''){$_SESSION['reservation_flash']=$reason;break;} $saved=$item; $found=true; break; }
                        unset($saved);
                        if (!$found && !isset($_SESSION['reservation_flash'])) $_SESSION['reservation_flash']='Nie znaleziono rezerwacji do edycji.';
                    } else { $item['created_at']=date('Y-m-d H:i:s'); $reason=rm_admin_assign_reservation($item,$items); if($reason===''){$items[]=$item; $found=true;}else{$_SESSION['reservation_flash']=$reason;} }
                }
            }
            if ($found) {
                $tmp=$rf.'.'.bin2hex(random_bytes(6)).'.tmp';
                $json=json_encode(array_values($items),JSON_PRETTY_PRINT|JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);
                $ok=$json!==false&&file_put_contents($tmp,$json,LOCK_EX)!==false&&rename($tmp,$rf);
                if(!$ok&&is_file($tmp))@unlink($tmp);
                $_SESSION['reservation_flash']=$ok ? ($action==='reservation_add'?'Dodano rezerwację.':($action==='reservation_update'?'Zapisano zmiany rezerwacji.':($action==='reservation_delete'?'Usunięto rezerwację.':'Zmieniono status wydania.'))) : 'Nie udało się zapisać zmian.';
            } elseif (!isset($_SESSION['reservation_flash'])) $_SESSION['reservation_flash']='Nie znaleziono wskazanej rezerwacji.';
            flock($lf,LOCK_UN);fclose($lf);
        }
    }
    header('Location: admin.php?tab=reservations&month=' . rawurlencode((string)($_POST['return_month'] ?? $_POST['reservation_month'] ?? date('Y-m')))); exit();
}
if ($is_logged && $_SERVER['REQUEST_METHOD'] === 'POST' && ($_POST['action'] ?? '') === 'reservation_history_delete') {
    if (!hash_equals($_SESSION['reservation_csrf'] ?? '', (string)($_POST['reservation_csrf'] ?? ''))) {
        $_SESSION['reservation_flash'] = 'Sesja wygasła. Odśwież stronę i spróbuj ponownie.';
    } else {
        $id=preg_replace('/[^a-zA-Z0-9_-]/','',(string)($_POST['reservation_id']??''));$rf=rm_rental_file('reservations.json');$lf=fopen($rf.'.lock','c+');
        if(!$lf||!flock($lf,LOCK_EX)){if($lf)fclose($lf);$_SESSION['reservation_flash']='Historia jest chwilowo zajęta.';}
        else{$items=rm_admin_read_json_list($rf);$before=count($items);$items=array_values(array_filter($items,function($r)use($id){return (string)($r['id']??'')!==$id;}));$tmp=$rf.'.'.bin2hex(random_bytes(6)).'.tmp';$json=json_encode($items,JSON_PRETTY_PRINT|JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);$ok=$id!==''&&count($items)<$before&&$json!==false&&file_put_contents($tmp,$json,LOCK_EX)!==false&&rename($tmp,$rf);if(!$ok&&is_file($tmp))@unlink($tmp);$_SESSION['reservation_flash']=$ok?'Usunięto wybrany zapis z historii.':'Nie znaleziono wpisu lub nie udało się go usunąć.';flock($lf,LOCK_UN);fclose($lf);}
    }
    header('Location: admin.php?tab=reservations&month='.rawurlencode((string)($_POST['return_month']??date('Y-m'))));exit();
}
if ($is_logged && $_SERVER['REQUEST_METHOD'] === 'POST' && ($_POST['action'] ?? '') === 'reservation_settle') {
    if (!hash_equals($_SESSION['reservation_csrf'] ?? '', (string)($_POST['reservation_csrf'] ?? ''))) {
        $_SESSION['reservation_flash'] = 'Sesja wygasła. Odśwież stronę i spróbuj ponownie.';
    } else {
        $id = preg_replace('/[^a-zA-Z0-9_-]/', '', (string)($_POST['reservation_id'] ?? ''));
        $income = filter_var($_POST['income'] ?? '0', FILTER_VALIDATE_FLOAT);
        $loss = filter_var($_POST['loss'] ?? '0', FILTER_VALIDATE_FLOAT);
        if ($id === '' || $income === false || $loss === false || $income < 0 || $loss < 0) {
            $_SESSION['reservation_flash'] = 'Wpisz prawidłowe, nieujemne kwoty.';
        } else {
            $rf = rm_rental_file('reservations.json');
            $lf = fopen($rf . '.lock', 'c+');
            if (!$lf || !flock($lf, LOCK_EX)) {
                if ($lf) fclose($lf);
                $_SESSION['reservation_flash'] = 'Lista rezerwacji jest chwilowo zajęta.';
            } else {
                $items = rm_admin_read_json_list($rf);
                $found = false;
                foreach ($items as &$item) {
                    if ((string)($item['id'] ?? '') !== $id) continue;
                    $item['settled'] = true;
                    $item['income'] = round((float)$income, 2);
                    $item['loss'] = round((float)$loss, 2);
                    $item['loss_note'] = trim((string)($_POST['loss_note'] ?? ''));
                    $item['settled_at'] = date('Y-m-d H:i:s');
                    $found = true;
                    break;
                }
                unset($item);
                $tmp = $rf . '.' . bin2hex(random_bytes(6)) . '.tmp';
                $json = json_encode(array_values($items), JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
                $ok = $found && $json !== false && file_put_contents($tmp, $json, LOCK_EX) !== false && rename($tmp, $rf);
                if (!$ok && is_file($tmp)) @unlink($tmp);
                flock($lf, LOCK_UN); fclose($lf);
                $_SESSION['reservation_flash'] = $ok ? 'Najem został rozliczony i zapisany w historii.' : 'Nie znaleziono rezerwacji lub nie udało się zapisać rozliczenia.';
            }
        }
    }
    header('Location: admin.php?tab=reservations'); exit();
}

// Ewidencja kosztów i inwestycji – wspólna baza z aplikacją Android.
if ($is_logged && empty($_SESSION['expense_csrf'])) $_SESSION['expense_csrf'] = bin2hex(random_bytes(32));
if ($is_logged && $_SERVER['REQUEST_METHOD'] === 'POST' && in_array(($_POST['action'] ?? ''), ['expense_save', 'expense_delete', 'equipment_cost_save', 'equipment_cost_delete'], true)) {
    if (!hash_equals($_SESSION['expense_csrf'] ?? '', (string)($_POST['expense_csrf'] ?? ''))) {
        $_SESSION['expense_flash'] = 'Sesja wygasła. Odśwież stronę i spróbuj ponownie.';
    } else {
        $cf = rm_rental_file('costs.json');
        $lock = fopen($cf . '.lock', 'c+');
        if (!$lock || !flock($lock, LOCK_EX)) {
            if ($lock) fclose($lock);
            $_SESSION['expense_flash'] = 'Lista kosztów jest chwilowo zajęta.';
        } else {
            $items = rm_admin_read_json_list($cf);
            $id = preg_replace('/[^a-zA-Z0-9_-]/', '', (string)($_POST['expense_id'] ?? ''));
            $ok = false;
            if (($_POST['action'] ?? '') === 'expense_delete') {
                $before = count($items);
                $items = array_values(array_filter($items, function($x) use ($id) { return (string)($x['id'] ?? '') !== $id; }));
                $ok = $id !== '' && count($items) < $before;
            } else {
                $date = (string)($_POST['expense_date'] ?? '');
                $name = trim((string)($_POST['expense_name'] ?? ($_POST['equipment_name'] ?? '')));
                $amountRaw = str_replace(',', '.', (string)($_POST['expense_amount'] ?? ''));
                $amount = filter_var($amountRaw, FILTER_VALIDATE_FLOAT);
                $type = (($_POST['expense_type'] ?? '') === 'investment') ? 'investment' : 'expense';
                $note = trim((string)($_POST['expense_note'] ?? ''));
                $dt = DateTime::createFromFormat('!Y-m-d', $date);
                if ($name !== '' && strlen($name) <= 160 && $dt && $dt->format('Y-m-d') === $date && $amount !== false && $amount > 0 && $amount <= 100000000) {
                    if ($id === '') $id = bin2hex(random_bytes(12));
                    $equipmentName = trim((string)($_POST['equipment_name'] ?? ''));
                    // Editing a model's repair in the general costs tab must not erase its attribution.
                    if (!array_key_exists('equipment_name', $_POST)) {
                        foreach ($items as $previous) if ((string)($previous['id']??'')===$id) {
                            $equipmentName=trim((string)($previous['equipment_name']??''));break;
                        }
                    }
                    $item = ['id'=>$id, 'date'=>$date, 'month'=>substr($date,0,7), 'name'=>function_exists('mb_substr')?mb_substr($name,0,160,'UTF-8'):substr($name,0,160), 'amount'=>round((float)$amount,2), 'type'=>$type, 'equipment_name'=>function_exists('mb_substr')?mb_substr($equipmentName,0,160,'UTF-8'):substr($equipmentName,0,160), 'note'=>function_exists('mb_substr')?mb_substr($note,0,1000,'UTF-8'):substr($note,0,1000)];
                    $found = false;
                    foreach ($items as &$saved) { if ((string)($saved['id'] ?? '') === $id) { $saved = $item; $found = true; break; } }
                    unset($saved);
                    if (!$found) $items[] = $item;
                    $ok = true;
                }
            }
            $tmp = $cf . '.' . bin2hex(random_bytes(6)) . '.tmp';
            $json = json_encode(array_values($items), JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
            $writeOk = $ok && $json !== false && file_put_contents($tmp, $json, LOCK_EX) !== false && rename($tmp, $cf);
            if (!$writeOk && is_file($tmp)) @unlink($tmp);
            flock($lock, LOCK_UN); fclose($lock);
            $_SESSION['expense_flash'] = $writeOk ? (($_POST['action'] === 'expense_delete') ? 'Usunięto wpis kosztowy.' : 'Zapisano koszt. Zmiana będzie widoczna w aplikacji po synchronizacji.') : 'Nie zapisano zmian. Sprawdź datę, nazwę i kwotę.';
        }
    }
    $redirectMonth = preg_match('/^\d{4}-\d{2}$/', (string)($_POST['return_month'] ?? '')) ? (string)$_POST['return_month'] : date('Y-m');
    if (in_array(($_POST['action'] ?? ''), ['equipment_cost_save','equipment_cost_delete'], true)) {
        header('Location: admin.php?tab=profitability&year=' . rawurlencode((string)($_POST['return_year'] ?? date('Y'))));
    } else {
        header('Location: admin.php?tab=costs&month=' . rawurlencode($redirectMonth));
    }
    exit();
}

// Zarządzanie dostępem do aplikacji protokołów (kody jednorazowe, urządzenia można unieważnić).
$protocol_devices_file = __DIR__ . '/protocol_devices.json';
function admin_load_protocol_devices($file) {
    if (!is_file($file)) return ['devices' => [], 'codes' => []];
    $data = json_decode((string)file_get_contents($file), true);
    if (!is_array($data)) rm_admin_storage_error();
    return ['devices' => is_array($data['devices'] ?? null) ? $data['devices'] : [], 'codes' => is_array($data['codes'] ?? null) ? $data['codes'] : []];
}
function admin_save_protocol_devices($file, $data) {
    rm_admin_write_json_file($file, $data);
}
if ($is_logged && empty($_SESSION['protocol_devices_csrf'])) $_SESSION['protocol_devices_csrf'] = bin2hex(random_bytes(32));

if ($is_logged && $_SERVER['REQUEST_METHOD'] === 'POST' && in_array(($_POST['action'] ?? ''), ['protocol_create_code', 'protocol_revoke_device'], true)) {
    $postedToken = (string)($_POST['protocol_devices_csrf'] ?? '');
    if (!hash_equals($_SESSION['protocol_devices_csrf'] ?? '', $postedToken)) {
        $_SESSION['protocol_devices_error'] = 'Sesja wygasła. Odśwież stronę i spróbuj ponownie.';
        header('Location: admin.php?tab=devices'); exit();
    }
    $deviceData = admin_load_protocol_devices($protocol_devices_file);
    if ($_POST['action'] === 'protocol_create_code') {
        $role = (($_POST['device_role'] ?? '') === 'assistant') ? 'assistant' : 'admin';
        $name = trim((string)($_POST['device_name'] ?? ''));
        if ($name === '') $name = $role === 'admin' ? 'Telefon administratora' : 'Tablet pomocnika';
        $code = bin2hex(random_bytes(16));
        $deviceData['codes'][] = ['hash' => hash('sha256', $code), 'role' => $role, 'name' => $name, 'created_at' => date('Y-m-d H:i:s'), 'expires_at' => time() + 86400];
        admin_save_protocol_devices($protocol_devices_file, $deviceData);
        $_SESSION['protocol_activation_flash'] = ['role' => $role, 'name' => $name, 'url' => 'https://rentmor.pl/protocol.php?activate=' . $code, 'expires' => date('Y-m-d H:i', time() + 86400)];
    } else {
        $deviceId = (string)($_POST['device_id'] ?? '');
        foreach ($deviceData['devices'] as &$device) {
            if (($device['id'] ?? '') === $deviceId) $device['active'] = false;
        }
        unset($device);
        admin_save_protocol_devices($protocol_devices_file, $deviceData);
        $_SESSION['protocol_devices_notice'] = 'Dostęp urządzenia został odebrany.';
    }
    header('Location: admin.php?tab=devices'); exit();
}

if ($is_logged && $_SERVER['REQUEST_METHOD'] === 'POST') {
    if (($_POST['action'] ?? '') === 'save_terms') {
        $terms = trim((string)($_POST['terms_content'] ?? ''));
        $terms_data = [
            'content' => $terms,
            'updated_at' => date('Y-m-d H:i:s')
        ];
        rm_admin_write_json_file('terms.json', $terms_data);
        header('Location: admin.php?tab=terms&saved=1');
        exit();
    }
    $current_data = $inventoryHandle ? rm_admin_inventory_read($inventoryHandle) : [];

    if (!is_array($current_data)) {
        $current_data = [];
    }

    /* DODAWANIE I EDYCJA */
    if (
        isset($_POST['action']) &&
        in_array($_POST['action'], ['add', 'update'], true)
    ) {
        $is_update = $_POST['action'] === 'update';
        $images = [];
        $old_item = null;

        if ($is_update) {
            foreach ($current_data as $p) {
                if (($p['id'] ?? '') === ($_POST['id'] ?? '')) {
                    $old_item = $p;
                    $images = $p['images'] ?? [];
                    break;
                }
            }

            if ($old_item === null) {
                header("Location: admin.php?tab=products");
                exit();
            }
        }

        // New and old photo changes are validated before any file is moved.
        $removedPhotos = [];
        if ($is_update) {
            $originalPhotos = is_array($old_item['images'] ?? null) ? array_values($old_item['images']) : [];
            $order = $_POST['photo_order'] ?? $originalPhotos;
            $remove = $_POST['photo_remove'] ?? [];
            if (!is_array($order) || !is_array($remove) ||
                count($order) !== count($originalPhotos) ||
                count(array_unique($order)) !== count($order) ||
                array_diff($order, $originalPhotos) || array_diff($originalPhotos, $order) ||
                array_diff($remove, $originalPhotos)) {
                rm_admin_reject('Nieprawidłowa kolejność lub lista zdjęć.');
            }
            $removedPhotos = array_values(array_intersect($originalPhotos, $remove));
            $images = array_values(array_diff($order, $removedPhotos));
        }
        // Validate monetary values and *every* upload before mutating the catalogue.
        $price = rm_admin_money($_POST['price'] ?? null, true, 'Cena');
        $deposit = rm_admin_money(($_POST['deposit'] ?? '') === '' ? '0' : $_POST['deposit'], false, 'Kaucja');
        $validatedPhotos = rm_admin_validate_photos($_FILES['photos'] ?? []);
        $newFiles = [];
        register_shutdown_function(static function () use (&$newFiles) {
            // Never leave orphaned uploads behind when a later step fails.
            foreach ($newFiles as $file) @unlink($file);
        });
        if ($validatedPhotos && !is_dir($upload_dir) && !@mkdir($upload_dir, 0755, true) && !is_dir($upload_dir)) {
            rm_admin_storage_error();
        }
        foreach ($validatedPhotos as $photo) {
            $name = 'sprzet_' . bin2hex(random_bytes(12)) . '.' . $photo['extension'];
            $dest = $upload_dir . $name;
            if (!move_uploaded_file($photo['tmp'], $dest)) rm_admin_storage_error();
            $newFiles[] = $dest;
            $images[] = $name;
        }

        $item_data = [
            'id' => $is_update ? $old_item['id'] : uniqid(),
            'name' => htmlspecialchars(trim($_POST['name'] ?? ''), ENT_QUOTES, 'UTF-8'),
            'category' => $_POST['category'] ?? 'budowlane',
            'desc' => htmlspecialchars(trim($_POST['desc'] ?? ''), ENT_QUOTES, 'UTF-8'),

            /* POLA SEO */
            'seo_title' => trim($_POST['seo_title'] ?? ''),
            'seo_description' => trim($_POST['seo_description'] ?? ''),

            'price' => $price,
            'deposit' => $deposit,
            'olx' => trim($_POST['olx'] ?? ''),
            'images' => $images,
            'date' => date('Y-m-d H:i'),
            'phone_clicks' => $is_update
                ? ($old_item['phone_clicks'] ?? 0)
                : 0
        ];

        if ($is_update) {
            foreach ($current_data as &$p) {
                if (($p['id'] ?? '') === ($_POST['id'] ?? '')) {
                    $p = $item_data;
                    break;
                }
            }
            unset($p);
        } else {
            array_unshift($current_data, $item_data);
        }

        rm_admin_inventory_write($inventoryHandle, array_values($current_data));
        $newFiles = []; // Commit complete: keep uploaded images.
        // Delete removed files only after a successful commit, and never if shared.
        $usedPhotos = [];
        foreach ($current_data as $entry) {
            foreach (($entry['images'] ?? []) as $photoName) $usedPhotos[(string)$photoName] = true;
        }
        foreach ($removedPhotos as $photoName) {
            if (isset($usedPhotos[$photoName])) continue;
            if (!preg_match('/^[a-zA-Z0-9_.-]+$/D', (string)$photoName)) continue;
            $file = $upload_dir . $photoName;
            if (is_file($file)) @unlink($file);
        }

        header("Location: admin.php?tab=products");
        exit();
    }

    /* USUWANIE SPRZĘTU */
    if (($_POST['action'] ?? '') === 'delete') {
        $imagesToDelete = [];
        foreach ($current_data as $key => $item) {
            if (($item['id'] ?? '') === ($_POST['id'] ?? '')) {
                $imagesToDelete = $item['images'] ?? [];
                unset($current_data[$key]);
                break;
            }
        }

        rm_admin_inventory_write($inventoryHandle, array_values($current_data));
        // A photo can be referenced by multiple products. Delete the file
        // only after committing the catalogue and checking all remaining offers.
        $usedPhotos = [];
        foreach ($current_data as $remaining) {
            foreach (($remaining['images'] ?? []) as $used) {
                if (is_string($used)) $usedPhotos[$used] = true;
            }
        }
        foreach ($imagesToDelete as $img) {
            if (!is_string($img) || isset($usedPhotos[$img])) continue;
            if (!preg_match('/^[a-zA-Z0-9_.-]+$/D', $img) || basename($img) !== $img) continue;
            $file = $upload_dir . $img;
            if (is_file($file)) @unlink($file);
        }
        header("Location: admin.php?tab=products");
        exit();
    }

    /* USUWANIE WIADOMOŚCI */
    if (($_POST['action'] ?? '') === 'delete_msg') {
        $msgs = rm_admin_read_json_list($msgs_file);

        foreach ($msgs as $key => $m) {
            if (($m['id'] ?? '') === ($_POST['id'] ?? '')) {
                unset($msgs[$key]);
                break;
            }
        }

        rm_admin_write_json_file($msgs_file, array_values($msgs));

        header("Location: admin.php?tab=messages");
        exit();
    }
}

/* RESET LICZNIKÓW TELEFONÓW */
if ($is_logged && ($_SERVER['REQUEST_METHOD'] ?? '') === 'POST' && ($_POST['action'] ?? '') === 'reset_phones') {
    $current_data = $inventoryHandle ? rm_admin_inventory_read($inventoryHandle) : [];

    if (!is_array($current_data)) {
        $current_data = [];
    }

    foreach ($current_data as &$p) {
        $p['phone_clicks'] = 0;
    }
    unset($p);

    rm_admin_inventory_write($inventoryHandle, $current_data);

    header("Location: admin.php?tab=products");
    exit();
}

/* WCZYTYWANIE DANYCH */
$products = file_exists($data_file)
    ? json_decode(file_get_contents($data_file), true)
    : [];

$messages = file_exists($msgs_file)
    ? json_decode(file_get_contents($msgs_file), true)
    : [];

$stats = file_exists($stats_file)
    ? json_decode(file_get_contents($stats_file), true)
    : [];

$terms_data = file_exists('terms.json') ? json_decode(file_get_contents('terms.json'), true) : [];
if (!is_array($terms_data)) $terms_data = [];
$terms_content = (string)($terms_data['content'] ?? '');

if (!is_array($products)) $products = [];
if (!is_array($messages)) $messages = [];
if (!is_array($stats)) $stats = [];

$days_keys = array_keys($stats['days'] ?? []);
$days_values = array_values($stats['days'] ?? []);

$total_search = 0;
$total_direct = 0;
$total_other = 0;

if (isset($stats['sources']) && is_array($stats['sources'])) {
    foreach ($stats['sources'] as $day_source) {
        $total_search += $day_source['search_social'] ?? 0;
        $total_direct += $day_source['direct'] ?? 0;
        $total_other += $day_source['other_ext'] ?? 0;
    }
}

// Karty klientów: wspólna baza z aplikacją Android.
if ($is_logged && empty($_SESSION['customer_csrf'])) $_SESSION['customer_csrf'] = bin2hex(random_bytes(32));
if ($is_logged && $_SERVER['REQUEST_METHOD'] === 'POST' && ($_POST['action'] ?? '') === 'customer_save') {
    if (!hash_equals($_SESSION['customer_csrf'] ?? '', (string)($_POST['customer_csrf'] ?? ''))) {
        $_SESSION['customer_flash'] = 'Sesja wygasła. Odśwież stronę i spróbuj ponownie.';
    } else {
        $phone = trim((string)($_POST['phone'] ?? '')); $key = rm_admin_phone_key($phone);
        if ($key === '') { $_SESSION['customer_flash'] = 'Podaj prawidłowy numer telefonu.'; }
        else {
            $cf = rm_rental_file('customers.json'); $lf = fopen($cf . '.lock', 'c+');
            if (!$lf || !flock($lf, LOCK_EX)) { if ($lf) fclose($lf); $_SESSION['customer_flash'] = 'Baza klientów jest chwilowo zajęta.'; }
            else {
                $items = rm_admin_read_json_list($cf);
                $item = ['id'=>$key,'phone'=>$phone,'phone_key'=>$key,'name'=>trim((string)($_POST['name'] ?? '')),'notes'=>trim((string)($_POST['notes'] ?? ''))];
                $found = false; foreach ($items as &$saved) { if (rm_admin_phone_key($saved['phone'] ?? '') === $key) { $item['phone'] = $saved['phone'] ?? $phone; $saved = array_merge($saved, $item); $found = true; break; } } unset($saved); if (!$found) $items[] = $item;
                $tmp = $cf . '.' . bin2hex(random_bytes(6)) . '.tmp'; $json = json_encode(array_values($items), JSON_PRETTY_PRINT|JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);
                $ok = $json !== false && file_put_contents($tmp, $json, LOCK_EX) !== false && rename($tmp, $cf); if (!$ok && is_file($tmp)) @unlink($tmp);
                $_SESSION['customer_flash'] = $ok ? 'Kartę klienta zapisano.' : 'Nie udało się zapisać karty klienta.'; flock($lf, LOCK_UN); fclose($lf);
            }
        }
    }
    header('Location: admin.php?tab=customers'); exit();
}

$active_tab = $_GET['tab'] ?? 'dashboard';
$edit_item = null;
$edit_mode = false;

if (isset($_GET['edit_id'])) {
    foreach ($products as $p) {
        if (($p['id'] ?? '') === $_GET['edit_id']) {
            $edit_item = $p;
            $edit_mode = true;
            $active_tab = 'products';
            break;
        }
    }
}
?>
<!DOCTYPE html>
<html lang="pl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Admin RentMor PRO</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>

    <style>
        * {
            box-sizing: border-box;
        }

        html, body {
            width: 100%;
            max-width: 100%;
            overflow-x: hidden;
        }

        body {
            margin: 0;
        }

        .custom-scrollbar::-webkit-scrollbar {
            height: 6px;
        }

        .custom-scrollbar::-webkit-scrollbar-track {
            background: #f1f1f1;
        }

        .custom-scrollbar::-webkit-scrollbar-thumb {
            background: #d1d5db;
            border-radius: 10px;
        }

        input, select, textarea, button {
            max-width: 100%;
            min-width: 0;
        }

        .admin-container {
            width: 100%;
            min-width: 0;
        }

        .product-form {
            width: 100%;
            min-width: 0;
        }

        .product-form input,
        .product-form select,
        .product-form textarea {
            width: 100%;
        }

        .admin-nav-inner {
            min-width: 0;
        }

        .admin-nav-menu {
            min-width: 0;
        }

        .product-row {
            min-width: 0;
        }

        @media (max-width: 640px) {
            .admin-container {
                padding: 20px 10px !important;
            }

            .admin-nav-inner {
                flex-direction: column;
                align-items: stretch;
                gap: 10px;
            }

            .admin-nav-inner > span {
                text-align: center;
                font-size: 17px;
            }

            .admin-nav-menu {
                display: grid !important;
                grid-template-columns: repeat(2, minmax(0, 1fr));
                gap: 6px;
                width: 100%;
            }

            .admin-nav-menu a {
                display: flex;
                align-items: center;
                justify-content: center;
                text-align: center;
                min-width: 0;
                min-height: 42px;
                padding: 10px 6px !important;
                margin: 0 !important;
                font-size: 11px !important;
                line-height: 1.2;
                white-space: normal;
                overflow-wrap: anywhere;
            }

            .product-form {
                padding: 16px !important;
                border-radius: 18px !important;
                margin-bottom: 28px !important;
            }

            .product-form .grid {
                grid-template-columns: minmax(0, 1fr) !important;
                gap: 10px !important;
            }

            .product-form input,
            .product-form select,
            .product-form textarea {
                width: 100%;
                min-width: 0;
                font-size: 16px;
            }

            .product-form textarea {
                overflow-wrap: anywhere;
            }

            .product-form h3 {
                font-size: 18px;
            }

            .product-row {
                flex-direction: column;
                align-items: stretch;
                gap: 12px;
            }

            .product-row-info {
                width: 100%;
                min-width: 0;
            }

            .product-row-actions {
                width: 100%;
                display: flex;
                justify-content: flex-end;
            }

            .product-row-actions a,
            .product-row-actions button {
                padding: 12px;
            }
        }
    </style>
</head>

<body class="bg-slate-50 pb-20 font-sans">

<?php if (!$is_logged): ?>

    <div class="min-h-screen flex items-center justify-center p-4">
        <form method="POST" class="bg-white p-8 sm:p-10 rounded-3xl shadow-2xl w-full max-w-sm text-center"><input type="hidden" name="login_csrf" value="<?php echo e_admin($_SESSION['login_csrf']); ?>">
            <h2 class="text-2xl font-black mb-6 uppercase">Admin RentMor</h2>
            <?php if(isset($_GET['password_changed'])): ?>
                <p class="text-green-700 font-bold mb-4">Hasło zmienione. Zaloguj się nowym hasłem i kodem 2FA.</p>
            <?php endif; ?>

            <?php if (isset($error)): ?>
                <p class="text-red-500 font-bold mb-4"><?php echo e_admin($error); ?></p>
            <?php endif; ?>

            <?php if($rm_pending): ?>
                <?php if($rm_auth['totp_secret']===''): ?>
                    <p class="text-left text-sm mb-4">Dodaj ręcznie nowe konto w Google Authenticator, Microsoft Authenticator lub innej aplikacji TOTP. Typ: czasowy, nazwa: RentMor Admin. Zachowaj ten klucz w bezpiecznym miejscu:</p>
                    <p class="bg-yellow-50 p-3 rounded-lg font-mono break-all mb-4 select-all"><?php echo e_admin($_SESSION['rm_setup_secret']??''); ?></p>
                <?php else: ?>
                    <p class="text-sm mb-4">Wpisz kod z aplikacji uwierzytelniającej.</p>
                <?php endif; ?>
                <input name="totp_code" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" minlength="6" class="w-full p-4 border rounded-2xl mb-4" placeholder="Kod 6 cyfr" required>
                <button type="submit" class="w-full bg-gray-900 text-white font-black py-4 rounded-2xl">POTWIERDŹ KOD</button>
                <a href="?logout=1" class="block mt-4 underline text-sm">Anuluj</a>
            <?php else: ?>
                <input type="password" name="login_pass" autocomplete="current-password" class="w-full p-4 border rounded-2xl mb-4" placeholder="Hasło" required>
                <button type="submit" class="w-full bg-gray-900 text-white font-black py-4 rounded-2xl">ZALOGUJ</button>
            <?php endif; ?>
        </form>
    </div>

<?php else: ?>

    <nav class="bg-gray-900 text-white p-4 sticky top-0 z-50">
        <div class="max-w-6xl mx-auto flex justify-between items-center admin-nav-inner">
            <span class="font-black text-yellow-400 tracking-widest">RENTMOR PRO</span>

            <div class="flex gap-1 bg-gray-800 p-1 rounded-xl admin-nav-menu">
                <a href="?tab=dashboard"
                   class="px-4 py-2 rounded-lg text-xs font-black transition <?php echo $active_tab == 'dashboard' ? 'bg-yellow-400 text-black' : ''; ?>">
                    STATY
                </a>

                <a href="?tab=messages"
                   class="px-4 py-2 rounded-lg text-xs font-black transition <?php echo $active_tab == 'messages' ? 'bg-yellow-400 text-black' : ''; ?>">
                    PYTANIA
                </a>

                <a href="?tab=products"
                   class="px-4 py-2 rounded-lg text-xs font-black transition <?php echo $active_tab == 'products' ? 'bg-yellow-400 text-black' : ''; ?>">
                    SPRZĘT
                </a>

                <a href="?tab=terms"
                   class="px-4 py-2 rounded-lg text-xs font-black transition <?php echo $active_tab == 'terms' ? 'bg-yellow-400 text-black' : ''; ?>">
                    ZASADY NAJMU
                </a>

                <a href="?tab=reservations"
                   class="px-4 py-2 rounded-lg text-xs font-black transition <?php echo $active_tab == 'reservations' ? 'bg-yellow-400 text-black' : ''; ?>">
                    REZERWACJE
                </a>

                <a href="?tab=customers" class="px-4 py-2 rounded-lg text-xs font-black transition <?php echo $active_tab == 'customers' ? 'bg-yellow-400 text-black' : ''; ?>">KLIENCI</a>

                <a href="?tab=costs"
                   class="px-4 py-2 rounded-lg text-xs font-black transition <?php echo $active_tab == 'costs' ? 'bg-yellow-400 text-black' : ''; ?>">
                    KOSZTY
                </a>

                <a href="?tab=profitability" class="px-4 py-2 rounded-lg text-xs font-black transition <?php echo $active_tab == 'profitability' ? 'bg-yellow-400 text-black' : ''; ?>">RENTOWNOŚĆ</a>

                <a href="?tab=devices"
                   class="px-4 py-2 rounded-lg text-xs font-black transition <?php echo $active_tab == 'devices' ? 'bg-yellow-400 text-black' : ''; ?>">
                    URZĄDZENIA
                </a>

                <a href="protocol.php"
                   class="px-4 py-2 rounded-lg text-xs font-black transition">
                    PROTOKOŁY
                </a>

                <a href="https://github.com/Domiono/Protokol/releases/download/latest/RentMor.apk"
                   class="bg-yellow-400 text-black px-4 py-2 rounded-lg text-xs font-black transition whitespace-nowrap">
                    POBIERZ APLIKACJĘ
                </a>

                <a href="?logout=true"
                   class="bg-red-600 px-4 py-2 rounded-lg text-xs font-black ml-1">
                    X
                </a>
            </div>
        </div>
    </nav>

    <div class="max-w-6xl mx-auto p-4 py-8 admin-container">

        <?php if ($active_tab == 'dashboard'): ?>
            <?php if (!empty($stats['browser_filter_since'])): ?>
            <p class="bg-white p-4 rounded-xl mb-4 text-sm">Wzmocniony filtr odsłon działa od <?php echo e_admin((new DateTimeImmutable($stats['browser_filter_since']))->setTimezone(new DateTimeZone('Europe/Warsaw'))->format('d.m.Y H:i')); ?>. Liczone są strony potwierdzone przez przeglądarkę w widocznej karcie. Starsze sumy zachowano. Odsłony nie oznaczają liczby osób ani sesji Google Analytics.</p>
            <?php endif; ?>
            <?php require __DIR__ . '/phone_events_view.php'; ?>

            <div class="bg-white p-6 rounded-3xl shadow-sm border border-yellow-300 mb-8 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div>
                    <h2 class="font-black text-lg">Aplikacja RentMor</h2>
                    <p class="text-sm text-gray-500 mt-1">Pobierz instalator APK, aby zainstalować aplikację na kolejnym telefonie lub tablecie.</p>
                </div>
                <a href="https://github.com/Domiono/Protokol/releases/download/latest/RentMor.apk"
                   class="inline-flex justify-center items-center bg-yellow-400 hover:bg-yellow-300 text-black font-black px-6 py-4 rounded-2xl whitespace-nowrap">
                    POBIERZ APK ↓
                </a>
            </div>

            <div class="grid grid-cols-2 md:grid-cols-5 gap-4 mb-8">

                <div class="bg-white p-5 rounded-3xl shadow-sm border-b-4 border-blue-500">
                    <p class="text-[10px] font-black text-gray-400 uppercase">Łączne Odsłony</p>
                    <p class="text-2xl font-black"><?php echo $stats['total_views'] ?? 0; ?></p>
                </div>

                <div class="bg-white p-5 rounded-3xl shadow-sm border-b-4 border-red-500">
                    <p class="text-[10px] font-black text-gray-400 uppercase">Suma Tel 📞</p>
                    <p class="text-2xl font-black"><?php echo $stats['total_phone_clicks'] ?? 0; ?></p>
                </div>

                <div class="bg-white p-5 rounded-3xl shadow-sm border-b-4 border-green-500">
                    <p class="text-[10px] font-black text-gray-400 uppercase">Unikalni Goście</p>
                    <p class="text-2xl font-black"><?php echo $stats['unique_visitors'] ?? 0; ?></p>
                </div>

                <div class="bg-white p-5 rounded-3xl shadow-sm border-b-4 border-yellow-500">
                    <p class="text-[10px] font-black text-gray-400 uppercase">Wyszukiwarka/Social</p>
                    <p class="text-2xl font-black"><?php echo $total_search; ?></p>
                </div>

                <div class="bg-white p-5 rounded-3xl shadow-sm border-b-4 border-purple-500">
                    <p class="text-[10px] font-black text-gray-400 uppercase">Bezpośrednie/Inne</p>
                    <p class="text-2xl font-black"><?php echo $total_direct + $total_other; ?></p>
                </div>

            </div>

            <div class="bg-white p-6 rounded-3xl shadow-sm mb-8">
                <div class="flex justify-between items-center mb-6">
                    <h3 class="font-black uppercase text-sm text-gray-400 tracking-widest">Ruch dzienny (Odsłony)</h3>
                    <span class="text-[10px] bg-gray-100 px-2 py-1 rounded text-gray-500">PRZEWIJAJ W BOK →</span>
                </div>

                <div class="overflow-x-auto custom-scrollbar pb-4">
                    <div style="min-width: <?php echo max(600, count($days_keys) * 50); ?>px; height: 300px;">
                        <canvas id="viewsChart"></canvas>
                    </div>
                </div>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-8">

                <div class="bg-white p-8 rounded-3xl shadow-sm">
                    <h3 class="font-black uppercase text-sm text-gray-400 tracking-widest mb-6 text-center">
                        Skąd przychodzą klienci?
                    </h3>

                    <div class="w-full h-64 flex justify-center">
                        <canvas id="sourceChart"></canvas>
                    </div>

                    <div class="mt-6 grid grid-cols-2 gap-2 text-center">
                        <div class="bg-yellow-50 p-3 rounded-2xl">
                            <p class="text-[9px] font-black uppercase text-yellow-600">Wyszukiwarka</p>
                            <p class="font-black text-xl"><?php echo $total_search; ?></p>
                        </div>

                        <div class="bg-blue-50 p-3 rounded-2xl">
                            <p class="text-[9px] font-black uppercase text-blue-600">Bezpośrednie</p>
                            <p class="font-black text-xl"><?php echo $total_direct; ?></p>
                        </div>
                    </div>
                </div>

                <div class="bg-white p-8 rounded-3xl shadow-sm overflow-hidden">
                    <h3 class="font-black uppercase text-sm text-gray-400 tracking-widest mb-6">Najpopularniejszy sprzęt</h3>

                    <div class="space-y-4 max-h-64 overflow-y-auto pr-2 custom-scrollbar">
                        <?php
                        $prod_views = $stats['product_views'] ?? [];
                        arsort($prod_views);

                        foreach (array_slice($prod_views, 0, 5, true) as $pid => $count):
                            foreach ($products as $p):
                                if (($p['id'] ?? '') == $pid):
                        ?>
                            <div class="flex justify-between items-center border-b pb-2">
                                <span class="text-xs font-bold truncate pr-4"><?php echo e_admin($p['name'] ?? ''); ?></span>
                                <span class="bg-slate-100 px-3 py-1 rounded-full text-[10px] font-black">
                                    <?php echo $count; ?> odsłon
                                </span>
                            </div>
                        <?php
                                    break;
                                endif;
                            endforeach;
                        endforeach;
                        ?>
                    </div>
                </div>

            </div>

            <script>
                new Chart(document.getElementById('viewsChart'), {
                    type: 'line',
                    data: {
                        labels: <?php echo json_encode($days_keys); ?>,
                        datasets: [{
                            label: 'Odsłony',
                            data: <?php echo json_encode($days_values); ?>,
                            borderColor: '#fbbf24',
                            borderWidth: 4,
                            backgroundColor: 'rgba(250, 204, 21, 0.05)',
                            fill: true,
                            tension: 0.4,
                            pointRadius: 4,
                            pointBackgroundColor: '#fff'
                        }]
                    },
                    options: {
                        maintainAspectRatio: false,
                        plugins: { legend: { display: false } },
                        scales: {
                            y: { beginAtZero: true },
                            x: { grid: { display: false } }
                        }
                    }
                });

                new Chart(document.getElementById('sourceChart'), {
                    type: 'doughnut',
                    data: {
                        labels: ['Wyszukiwarka/Social', 'Bezpośrednie', 'Inne'],
                        datasets: [{
                            data: [
                                <?php echo (int)$total_search; ?>,
                                <?php echo (int)$total_direct; ?>,
                                <?php echo (int)$total_other; ?>
                            ],
                            backgroundColor: ['#fbbf24', '#3b82f6', '#94a3b8'],
                            borderWidth: 0,
                            cutout: '75%'
                        }]
                    },
                    options: {
                        plugins: {
                            legend: {
                                position: 'bottom',
                                labels: {
                                    boxWidth: 12,
                                    font: { size: 10, weight: 'bold' }
                                }
                            }
                        }
                    }
                });
            </script>

        <?php endif; ?>


        <?php if ($active_tab == 'terms'): ?>
            <div class="bg-white p-6 sm:p-8 rounded-3xl shadow-sm border-t-8 border-yellow-400 max-w-4xl mx-auto">
                <div class="flex flex-wrap justify-between items-center gap-3 mb-5">
                    <div>
                        <h2 class="text-2xl font-black">Zasady wynajmu</h2>
                        <p class="text-sm text-gray-500 mt-1">Treść widoczna publicznie na stronie RentMor.pl.</p>
                    </div>
                    <a href="/zasady.php" target="_blank" class="text-sm font-bold text-blue-600 underline">Podgląd strony</a>
                </div>
                <?php if (isset($_GET['saved'])): ?>
                    <div class="bg-green-50 text-green-700 p-3 rounded-xl mb-4 font-bold">Zasady zostały zapisane.</div>
                <?php endif; ?>
                <form method="POST" class="space-y-4"><input type="hidden" name="admin_csrf" value="<?php echo e_admin($_SESSION['admin_csrf'] ?? ''); ?>">
                    <input type="hidden" name="action" value="save_terms">
                    <label class="block text-sm font-bold text-gray-700">Treść zasad (zachowaj podział na akapity i nagłówki)</label>
                    <textarea name="terms_content" rows="24" class="w-full border border-gray-200 rounded-2xl p-4 text-sm leading-7 focus:outline-none focus:border-yellow-400" required><?php echo e_admin($terms_content); ?></textarea>
                    <p class="text-xs text-gray-500">Możesz swobodnie zmieniać tekst. Puste linie tworzą odstępy między akapitami. Zmiany pojawią się na stronie po zapisaniu.</p>
                    <button type="submit" class="bg-yellow-400 hover:bg-yellow-300 text-black font-black px-7 py-4 rounded-xl">ZAPISZ ZASADY</button>
                </form>
            </div>
        <?php endif; ?>

        <?php if ($active_tab == 'reservations'): ?>
            <?php
                $reservation_file = rm_rental_file('reservations.json');
                $reservation_items = is_file($reservation_file) ? json_decode((string)file_get_contents($reservation_file), true) : [];
                if (!is_array($reservation_items)) $reservation_items = [];
                $reservation_month = preg_match('/^\d{4}-\d{2}$/', (string)($_GET['month'] ?? '')) ? (string)$_GET['month'] : date('Y-m');
                $active_reservations = array_values(array_filter($reservation_items, function($r) use ($reservation_month) { return ($r['month'] ?? '') === $reservation_month && empty($r['settled']); }));
                $history_reservations = array_values(array_filter($reservation_items, function($r) use ($reservation_month) { return rm_admin_settlement_month($r) === $reservation_month; }));
                $sum_income = 0; $sum_loss = 0;
                foreach ($history_reservations as $r) { $sum_income += (float)($r['income'] ?? 0); $sum_loss += (float)($r['loss'] ?? 0); }
                $equipment_stats = [];
                foreach ($history_reservations as $r) {
                    $equipment_name=rm_admin_equipment_name($r);
                    $model_key=function_exists('mb_strtolower')?mb_strtolower($equipment_name,'UTF-8'):strtolower($equipment_name);
                    if(!isset($equipment_stats[$model_key]))$equipment_stats[$model_key]=['name'=>$equipment_name,'count'=>0,'income'=>0,'loss'=>0];
                    $equipment_stats[$model_key]['count']++;
                    $equipment_stats[$model_key]['income']+=(float)($r['income']??0);
                    $equipment_stats[$model_key]['loss']+=(float)($r['loss']??0);
                }
                uasort($equipment_stats, function($a,$b){ return ($b['income']-$b['loss']) <=> ($a['income']-$a['loss']); });
                usort($active_reservations, function($a,$b){ return strcmp((string)($a['text'] ?? ''),(string)($b['text'] ?? '')); });
                usort($history_reservations, function($a,$b){ return strcmp((string)($a['text'] ?? ''),(string)($b['text'] ?? '')); });
            ?>
            <div class="space-y-6">
                <div class="flex flex-wrap items-end justify-between gap-3">
                    <div><h2 class="text-2xl font-black">Rezerwacje i rozliczenia</h2><p class="text-sm text-gray-500 mt-1">Rozliczone wpisy zostają w historii. Strata to dodatkowy koszt, np. naprawa lub uszkodzenie.</p></div>
                    <form method="GET" class="flex items-center gap-2"><input type="hidden" name="tab" value="reservations"><input type="month" name="month" value="<?php echo e_admin($reservation_month); ?>" class="border rounded-xl p-3 bg-white"><button class="bg-gray-900 text-white rounded-xl px-4 py-3 font-bold">Pokaż</button></form>
                </div>
                <?php if (!empty($_SESSION['reservation_flash'])): ?><div class="rounded-xl p-4 bg-blue-50 text-blue-800 font-bold"><?php echo e_admin($_SESSION['reservation_flash']); unset($_SESSION['reservation_flash']); ?></div><?php endif; ?>
                <?php $inventory = is_file(__DIR__.'/data.json') ? json_decode((string)file_get_contents(__DIR__.'/data.json'), true) : []; if (!is_array($inventory)) $inventory=[]; $equipment_names=[]; foreach($inventory as $product){$nm=trim((string)($product['name']??''));if($nm!=='')$equipment_names[$nm]=true;} $equipment_names=array_keys($equipment_names); ?>
                <section class="bg-white rounded-3xl p-5 sm:p-7 shadow-sm"><h3 class="text-xl font-black mb-3">Dodaj rezerwację</h3>
                    <form method="POST" class="grid sm:grid-cols-2 lg:grid-cols-3 gap-3 items-end"><input type="hidden" name="admin_csrf" value="<?php echo e_admin($_SESSION['admin_csrf'] ?? ''); ?>"><input type="hidden" name="action" value="reservation_add"><input type="hidden" name="reservation_csrf" value="<?php echo e_admin($_SESSION['reservation_csrf']); ?>"><input type="hidden" name="return_month" value="<?php echo e_admin($reservation_month); ?>">
                        <label class="text-xs font-bold">Data OD<input type="date" name="start_date" value="<?php echo e_admin($reservation_month.'-01'); ?>" required class="block w-full border rounded-xl p-3 mt-1"></label>
                        <label class="text-xs font-bold">Data DO<input type="date" name="end_date" value="<?php echo e_admin($reservation_month.'-01'); ?>" required class="block w-full border rounded-xl p-3 mt-1"></label>
                        <label class="text-xs font-bold">Sprzęt<select name="equipment" required class="block w-full border rounded-xl p-3 mt-1"><option value="">— Wybierz sprzęt —</option><?php foreach($equipment_names as $nm): ?><option value="<?php echo e_admin($nm); ?>"><?php echo e_admin($nm); ?></option><?php endforeach; ?></select></label>
                        <label class="text-xs font-bold">Godzina<input type="time" name="time" class="block w-full border rounded-xl p-3 mt-1"></label>
                        <label class="text-xs font-bold">Telefon lub nick z OLX<input name="phone" maxlength="100" placeholder="Telefon lub nick z OLX" class="block w-full border rounded-xl p-3 mt-1"></label><label class="text-xs font-bold">Nazwa klienta<input name="customer_name" maxlength="200" placeholder="Imię i nazwisko" class="block w-full border rounded-xl p-3 mt-1"></label>
                        <label class="flex items-center gap-2 text-sm font-semibold"><input type="checkbox" name="reservation_done" value="1"> Już wydane</label>
                        <button class="bg-yellow-400 rounded-xl p-3 font-black sm:col-span-2 lg:col-span-3">DODAJ REZERWACJĘ</button>
                    </form><p class="text-xs text-gray-500 mt-2">Dane będą zapisane w tym samym formacie, z którego korzysta aplikacja RentMor.</p>
                </section>
                <div class="grid sm:grid-cols-3 gap-3">
                    <div class="bg-white rounded-2xl p-5 shadow-sm"><p class="text-xs font-bold text-gray-500">PRZYCHÓD</p><p class="text-2xl font-black mt-1"><?php echo number_format($sum_income,2,',',' '); ?> zł</p></div>
                    <div class="bg-white rounded-2xl p-5 shadow-sm"><p class="text-xs font-bold text-gray-500">STRATY / KOSZTY</p><p class="text-2xl font-black mt-1 text-red-600"><?php echo number_format($sum_loss,2,',',' '); ?> zł</p></div>
                    <div class="bg-white rounded-2xl p-5 shadow-sm"><p class="text-xs font-bold text-gray-500">WYNIK NETTO</p><p class="text-2xl font-black mt-1 <?php echo ($sum_income-$sum_loss)<0?'text-red-600':'text-green-700'; ?>"><?php echo number_format($sum_income-$sum_loss,2,',',' '); ?> zł</p></div>
                </div>
                <section class="bg-white rounded-3xl p-5 sm:p-7 shadow-sm"><h3 class="text-xl font-black mb-4">Wynik według sprzętu</h3>
                    <?php if (!$equipment_stats): ?><p class="text-sm text-gray-500">Statystyki sprzętu pojawią się po pierwszych rozliczeniach.</p><?php else: ?>
                    <div class="overflow-x-auto"><table class="w-full text-sm"><thead><tr class="text-left border-b"><th class="p-2">Sprzęt</th><th class="p-2">Najmy</th><th class="p-2">Przychód</th><th class="p-2">Straty</th><th class="p-2">Wynik</th></tr></thead><tbody>
                    <?php foreach ($equipment_stats as $name=>$st): $net=$st['income']-$st['loss']; ?><tr class="border-b"><td class="p-2 font-semibold"><?php echo e_admin($st['name']); ?></td><td class="p-2"><?php echo (int)$st['count']; ?></td><td class="p-2"><?php echo number_format($st['income'],2,',',' '); ?> zł</td><td class="p-2 text-red-600"><?php echo number_format($st['loss'],2,',',' '); ?> zł</td><td class="p-2 font-black <?php echo $net<0?'text-red-600':'text-green-700'; ?>"><?php echo number_format($net,2,',',' '); ?> zł</td></tr><?php endforeach; ?>
                    </tbody></table></div><?php endif; ?>
                </section>
                <section class="bg-white rounded-3xl p-5 sm:p-7 shadow-sm"><h3 class="text-xl font-black mb-4">Rezerwacje / do rozliczenia <span class="text-sm text-gray-400">(<?php echo count($active_reservations); ?>)</span></h3>
                    <?php if (!$active_reservations): ?><p class="text-sm text-gray-500">Brak nierozliczonych rezerwacji w tym miesiącu.</p><?php endif; ?>
                    <div class="space-y-4"><?php foreach ($active_reservations as $r): $rid=(string)($r['id']??''); $sd=(string)($r['start_date']??''); $ed=(string)($r['end_date']??''); $eq=(string)($r['equipment']??''); $tm=(string)($r['time']??''); $ph=(string)($r['phone']??''); ?>
                        <div class="border rounded-2xl p-4"><div class="font-bold mb-2"><?php echo e_admin($eq!==''?$eq:($r['text']??'Rezerwacja')); ?> <span class="text-xs <?php echo !empty($r['done'])?'text-green-700':'text-amber-700'; ?>">· <?php echo !empty($r['done'])?'WYDANE':'DO WYDANIA'; ?></span></div>
                            <p class="text-sm text-gray-600 mb-3"><?php echo e_admin($sd?:'—'); ?> → <?php echo e_admin($ed?:'—'); ?><?php echo $tm?' · '.e_admin($tm):''; ?><?php echo $ph?' · '.e_admin($ph):''; ?></p>
                            <form method="POST" class="mb-3"><input type="hidden" name="admin_csrf" value="<?php echo e_admin($_SESSION['admin_csrf'] ?? ''); ?>"><input type="hidden" name="action" value="reservation_toggle"><input type="hidden" name="reservation_csrf" value="<?php echo e_admin($_SESSION['reservation_csrf']); ?>"><input type="hidden" name="reservation_id" value="<?php echo e_admin($rid); ?>"><input type="hidden" name="reservation_done" value="<?php echo !empty($r['done'])?'0':'1'; ?>"><input type="hidden" name="return_month" value="<?php echo e_admin($reservation_month); ?>"><button class="<?php echo !empty($r['done'])?'bg-green-100 text-green-800':'bg-red-100 text-red-800'; ?> rounded-xl px-4 py-3 font-black w-full sm:w-auto"><?php echo !empty($r['done'])?'COFNIJ WYDANIE':'WYDANO — OZNACZ JAKO WYDANE'; ?></button></form>
                            <?php if($ph!==''): ?><div class="flex flex-wrap gap-2 mb-3"><a class="bg-yellow-100 rounded-lg px-3 py-2 text-sm font-bold" href="tel:<?php echo e_admin(preg_replace('/[^0-9+]/','',$ph)); ?>">ZADZWOŃ</a><a class="bg-blue-100 rounded-lg px-3 py-2 text-sm font-bold" href="sms:<?php echo e_admin(preg_replace('/[^0-9+]/','',$ph)); ?>?body=<?php echo rawurlencode('Dzień dobry, kontaktuję się w sprawie rezerwacji sprzętu RentMor.'); ?>">WYŚLIJ WIADOMOŚĆ</a></div><?php endif; ?>
                            <details class="mb-3"><summary class="cursor-pointer font-bold text-sm">Edytuj rezerwację</summary><form method="POST" class="grid sm:grid-cols-2 lg:grid-cols-3 gap-2 mt-3"><input type="hidden" name="admin_csrf" value="<?php echo e_admin($_SESSION['admin_csrf'] ?? ''); ?>"><input type="hidden" name="action" value="reservation_update"><input type="hidden" name="reservation_csrf" value="<?php echo e_admin($_SESSION['reservation_csrf']); ?>"><input type="hidden" name="reservation_id" value="<?php echo e_admin($rid); ?>"><input type="hidden" name="return_month" value="<?php echo e_admin($reservation_month); ?>"><label class="text-xs font-bold">Data OD<input type="date" name="start_date" value="<?php echo e_admin($sd); ?>" required class="block w-full border rounded-lg p-2 mt-1"></label><label class="text-xs font-bold">Data DO<input type="date" name="end_date" value="<?php echo e_admin($ed); ?>" required class="block w-full border rounded-lg p-2 mt-1"></label><label class="text-xs font-bold">Sprzęt<select name="equipment" required class="block w-full border rounded-lg p-2 mt-1"><option value="">— Wybierz —</option><?php foreach($equipment_names as $nm): ?><option value="<?php echo e_admin($nm); ?>" <?php echo $eq===$nm?'selected':''; ?>><?php echo e_admin($nm); ?></option><?php endforeach; ?><?php if($eq!==''&&!in_array($eq,$equipment_names,true)): ?><option selected value="<?php echo e_admin($eq); ?>"><?php echo e_admin($eq); ?></option><?php endif; ?></select></label><label class="text-xs font-bold">Godzina<input type="time" name="time" value="<?php echo e_admin($tm); ?>" class="block w-full border rounded-lg p-2 mt-1"></label><label class="text-xs font-bold">Telefon / nick OLX<input name="phone" value="<?php echo e_admin($ph); ?>" maxlength="100" class="block w-full border rounded-lg p-2 mt-1"></label><label class="text-xs font-bold">Nazwa klienta<input name="customer_name" value="<?php echo e_admin($r['customer_name']??''); ?>" maxlength="200" class="block w-full border rounded-lg p-2 mt-1"></label><label class="flex items-center gap-2 text-sm"><input type="checkbox" name="reservation_done" value="1" <?php echo !empty($r['done'])?'checked':''; ?>> Wydane</label><button class="bg-gray-900 text-white rounded-xl p-3 font-bold sm:col-span-2 lg:col-span-3">ZAPISZ ZMIANY</button></form></details>
                            <div class="grid sm:grid-cols-2 gap-2"><form method="POST" class="grid sm:grid-cols-3 gap-2 items-end"><input type="hidden" name="admin_csrf" value="<?php echo e_admin($_SESSION['admin_csrf'] ?? ''); ?>"><input type="hidden" name="action" value="reservation_settle"><input type="hidden" name="reservation_id" value="<?php echo e_admin($rid); ?>"><input type="hidden" name="reservation_csrf" value="<?php echo e_admin($_SESSION['reservation_csrf']); ?>"><label class="text-xs font-bold">Zarobek (zł)<input type="number" name="income" min="0" step="0.01" value="0" required class="block w-full border rounded-lg p-2 mt-1"></label><label class="text-xs font-bold">Strata / koszt (zł)<input type="number" name="loss" min="0" step="0.01" value="0" required class="block w-full border rounded-lg p-2 mt-1"></label><label class="text-xs font-bold">Opis straty<input name="loss_note" maxlength="300" class="block w-full border rounded-lg p-2 mt-1"></label><button class="bg-yellow-400 rounded-xl p-3 font-black sm:col-span-3">ROZLICZ NAJEM</button></form><form method="POST" onsubmit="return confirm('Usunąć tę rezerwację?');" class="self-end"><input type="hidden" name="admin_csrf" value="<?php echo e_admin($_SESSION['admin_csrf'] ?? ''); ?>"><input type="hidden" name="action" value="reservation_delete"><input type="hidden" name="reservation_id" value="<?php echo e_admin($rid); ?>"><input type="hidden" name="reservation_csrf" value="<?php echo e_admin($_SESSION['reservation_csrf']); ?>"><input type="hidden" name="return_month" value="<?php echo e_admin($reservation_month); ?>"><button class="bg-red-600 text-white rounded-xl p-3 font-black w-full">USUŃ REZERWACJĘ</button></form></div>
                        </div>
                    <?php endforeach; ?></div>
                </section>
                <section class="bg-white rounded-3xl p-5 sm:p-7 shadow-sm"><h3 class="text-xl font-black mb-4">Historia rozliczeń <span class="text-sm text-gray-400">(<?php echo count($history_reservations); ?>)</span></h3>
                    <?php if (!$history_reservations): ?><p class="text-sm text-gray-500">Nie ma jeszcze rozliczeń w tym miesiącu.</p><?php else: ?>
                    <div class="overflow-x-auto"><table class="w-full text-sm"><thead><tr class="text-left border-b"><th class="p-2">Wpis</th><th class="p-2">Przychód</th><th class="p-2">Strata</th><th class="p-2">Wynik</th><th class="p-2">Opis straty</th><th class="p-2">Akcja</th></tr></thead><tbody>
                    <?php foreach ($history_reservations as $r): $inc=(float)($r['income']??0); $loss=(float)($r['loss']??0); $net=$inc-$loss; ?><tr class="border-b"><td class="p-2 font-semibold"><?php echo e_admin($r['equipment']??$r['text']??''); ?><div class="text-xs text-gray-500"><?php echo e_admin($r['start_date']??''); ?><?php echo !empty($r['end_date'])?' → '.e_admin($r['end_date']):''; ?><?php echo !empty($r['phone'])?' · '.e_admin($r['phone']):''; ?></div></td><td class="p-2"><?php echo number_format($inc,2,',',' '); ?> zł</td><td class="p-2 text-red-600"><?php echo number_format($loss,2,',',' '); ?> zł</td><td class="p-2 font-black <?php echo $net<0?'text-red-600':'text-green-700'; ?>"><?php echo number_format($net,2,',',' '); ?> zł</td><td class="p-2"><?php echo e_admin($r['loss_note']??''); ?></td><td class="p-2"><form method="POST" onsubmit="return confirm('Usunąć ten zapis z historii? Tej operacji nie można cofnąć.');"><input type="hidden" name="admin_csrf" value="<?php echo e_admin($_SESSION['admin_csrf'] ?? ''); ?>"><input type="hidden" name="action" value="reservation_history_delete"><input type="hidden" name="reservation_id" value="<?php echo e_admin($r['id']??''); ?>"><input type="hidden" name="reservation_csrf" value="<?php echo e_admin($_SESSION['reservation_csrf']); ?>"><input type="hidden" name="return_month" value="<?php echo e_admin($reservation_month); ?>"><button class="bg-red-600 text-white rounded-lg px-3 py-2 text-xs font-bold">USUŃ</button></form></td></tr><?php endforeach; ?>
                    </tbody></table></div><?php endif; ?>
                </section>
            </div>
        <?php endif; ?>

        <?php if ($active_tab == 'customers'): ?>
            <?php
                $profilesFile = rm_rental_file('customers.json'); $profiles = is_file($profilesFile) ? json_decode((string)file_get_contents($profilesFile), true) : []; if (!is_array($profiles)) $profiles = [];
                $customers = []; foreach ($profiles as $p) { $k = rm_admin_phone_key($p['phone'] ?? ''); if ($k !== '') $customers[$k] = $p + ['phone_key'=>$k,'reservations'=>[],'protocols'=>[]]; }
                $reservations = is_file(rm_rental_file('reservations.json')) ? json_decode((string)file_get_contents(rm_rental_file('reservations.json')), true) : []; if (!is_array($reservations)) $reservations=[];
                foreach ($reservations as $r) { $k=rm_admin_phone_key($r['phone']??''); if($k==='') continue; if(!isset($customers[$k])) $customers[$k]=['phone'=>$r['phone'],'phone_key'=>$k,'name'=>$r['customer_name']??'','notes'=>'','reservations'=>[],'protocols'=>[]]; if(empty($customers[$k]['name'])&&!empty($r['customer_name']))$customers[$k]['name']=$r['customer_name']; $customers[$k]['reservations'][]=$r; }
                $protocols = is_file(rm_rental_file('protocols.json')) ? json_decode((string)file_get_contents(rm_rental_file('protocols.json')), true) : []; if (!is_array($protocols)) $protocols=[];
                foreach ($protocols as $p) { $k=rm_admin_phone_key($p['phone']??''); if($k==='') continue; if(!isset($customers[$k])) $customers[$k]=['phone'=>$p['phone'],'phone_key'=>$k,'name'=>$p['client']??'','notes'=>'','reservations'=>[],'protocols'=>[]]; if(empty($customers[$k]['name'])&&!empty($p['client']))$customers[$k]['name']=$p['client']; $customers[$k]['protocols'][]=$p; }
                uasort($customers, function($a,$b){ return strcasecmp((string)($a['name']??''),(string)($b['name']??'')); });
            ?>
            <div class="space-y-5"><div><h2 class="text-2xl font-black">Klienci</h2><p class="text-sm text-gray-500">Karty tworzą się automatycznie na podstawie rezerwacji i protokołów. Numer telefonu łączy historię klienta.</p></div>
            <?php if (!empty($_SESSION['customer_flash'])): ?><div class="rounded-xl p-4 bg-blue-50 text-blue-800 font-bold"><?php echo e_admin($_SESSION['customer_flash']); unset($_SESSION['customer_flash']); ?></div><?php endif; ?>
            <div class="grid gap-4"><?php foreach($customers as $c): ?><section class="bg-white rounded-2xl p-5 shadow-sm"><div class="flex flex-wrap justify-between gap-2"><div><h3 class="font-black text-lg"><?php echo e_admin($c['name']?:'Klient bez nazwy'); ?></h3><p class="text-sm text-gray-600"><?php echo e_admin($c['phone']); ?></p></div><div class="text-xs text-gray-500">Rezerwacje: <?php echo count($c['reservations']); ?> · Protokoły: <?php echo count($c['protocols']); ?></div></div>
            <form method="POST" class="grid sm:grid-cols-2 gap-3 mt-4"><input type="hidden" name="admin_csrf" value="<?php echo e_admin($_SESSION['admin_csrf'] ?? ''); ?>"><input type="hidden" name="action" value="customer_save"><input type="hidden" name="customer_csrf" value="<?php echo e_admin($_SESSION['customer_csrf']); ?>"><input type="hidden" name="phone" value="<?php echo e_admin($c['phone']); ?>"><label class="text-xs font-bold">Imię i nazwisko<input name="name" maxlength="200" value="<?php echo e_admin($c['name']??''); ?>" class="block w-full border rounded-lg p-3 mt-1"></label><label class="text-xs font-bold sm:row-span-2">Notatki<textarea name="notes" maxlength="4000" rows="3" class="block w-full border rounded-lg p-3 mt-1"><?php echo e_admin($c['notes']??''); ?></textarea></label><button class="bg-yellow-400 rounded-xl p-3 font-black">ZAPISZ KARTĘ</button></form>
            <?php if(!empty($c['reservations'])||!empty($c['protocols'])): ?><details class="mt-4"><summary class="cursor-pointer text-sm font-bold">Historia klienta</summary><div class="mt-2 space-y-2 text-sm"><?php foreach($c['reservations'] as $r): ?><div class="border-b py-2"><b>Rezerwacja:</b> <?php echo e_admin($r['equipment']??$r['text']??''); ?> · <?php echo e_admin($r['start_date']??''); ?><?php if(!empty($r['end_date'])) echo ' → '.e_admin($r['end_date']); ?></div><?php endforeach; ?><?php foreach($c['protocols'] as $p): ?><div class="border-b py-2"><b>Protokół:</b> <?php echo e_admin($p['number']??''); ?> · <?php echo e_admin($p['equipment']??''); ?> · <?php echo e_admin($p['rental_date']??$p['created_at']??''); ?></div><?php endforeach; ?></div></details><?php endif; ?></section><?php endforeach; ?><?php if(!$customers): ?><p class="bg-white rounded-xl p-5">Nie ma jeszcze klientów z prawidłowym numerem telefonu.</p><?php endif; ?></div></div>
        <?php endif; ?>

        <?php if ($active_tab == 'costs'): ?>
            <?php
                $cost_file = rm_rental_file('costs.json');
                $cost_items = is_file($cost_file) ? json_decode((string)file_get_contents($cost_file), true) : [];
                if (!is_array($cost_items)) $cost_items = [];
                $cost_month = preg_match('/^\d{4}-\d{2}$/', (string)($_GET['month'] ?? '')) ? (string)$_GET['month'] : date('Y-m');
                $month_costs = array_values(array_filter($cost_items, function($x) use ($cost_month) { return (string)($x['month'] ?? substr((string)($x['date'] ?? ''),0,7)) === $cost_month; }));
                usort($month_costs, function($a,$b) { return strcmp((string)($b['date'] ?? ''), (string)($a['date'] ?? '')); });
                $sum_expense = 0; $sum_investment = 0;
                foreach ($month_costs as $x) { if (($x['type'] ?? 'expense') === 'investment') $sum_investment += (float)($x['amount'] ?? 0); else $sum_expense += (float)($x['amount'] ?? 0); }
                $reservation_file_for_costs = rm_rental_file('reservations.json');
                $reservation_data_for_costs = is_file($reservation_file_for_costs) ? json_decode((string)file_get_contents($reservation_file_for_costs), true) : [];
                if (!is_array($reservation_data_for_costs)) $reservation_data_for_costs = [];
                $rental_income = 0; $rental_losses = 0;
                foreach ($reservation_data_for_costs as $r) if (rm_admin_settlement_month($r) === $cost_month) { $rental_income += (float)($r['income'] ?? 0); $rental_losses += (float)($r['loss'] ?? 0); }
                $final_result = $rental_income - $rental_losses - $sum_expense - $sum_investment;
            ?>
            <div class="max-w-6xl mx-auto space-y-6">
                <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                    <div><h2 class="text-2xl font-black">Pozostałe koszty i inwestycje</h2><p class="text-sm text-gray-500 mt-1">Wspólna ewidencja z aplikacją Android. Zmiany pojawią się w aplikacji po synchronizacji.</p></div>
                    <form method="GET" class="flex items-center gap-2"><input type="hidden" name="tab" value="costs"><input type="month" name="month" value="<?php echo e_admin($cost_month); ?>" class="border rounded-xl p-3 bg-white"><button class="bg-gray-900 text-white rounded-xl px-4 py-3 font-bold">Pokaż</button></form>
                </div>
                <?php if (!empty($_SESSION['expense_flash'])): ?><div class="rounded-xl p-4 bg-blue-50 text-blue-800 font-bold"><?php echo e_admin($_SESSION['expense_flash']); unset($_SESSION['expense_flash']); ?></div><?php endif; ?>
                <div class="grid sm:grid-cols-2 lg:grid-cols-5 gap-3">
                    <div class="bg-white rounded-2xl p-4 shadow-sm"><p class="text-xs font-bold text-gray-500">PRZYCHÓD Z NAJMU</p><p class="text-xl font-black mt-1"><?php echo number_format($rental_income,2,',',' '); ?> zł</p></div>
                    <div class="bg-white rounded-2xl p-4 shadow-sm"><p class="text-xs font-bold text-gray-500">STRATY Z NAJMU</p><p class="text-xl font-black mt-1 text-red-600"><?php echo number_format($rental_losses,2,',',' '); ?> zł</p></div>
                    <div class="bg-white rounded-2xl p-4 shadow-sm"><p class="text-xs font-bold text-gray-500">KOSZTY BIEŻĄCE</p><p class="text-xl font-black mt-1"><?php echo number_format($sum_expense,2,',',' '); ?> zł</p></div>
                    <div class="bg-white rounded-2xl p-4 shadow-sm"><p class="text-xs font-bold text-gray-500">INWESTYCJE</p><p class="text-xl font-black mt-1"><?php echo number_format($sum_investment,2,',',' '); ?> zł</p></div>
                    <div class="bg-white rounded-2xl p-4 shadow-sm"><p class="text-xs font-bold text-gray-500">WYNIK PO KOSZTACH</p><p class="text-xl font-black mt-1 <?php echo $final_result < 0 ? 'text-red-600' : 'text-green-700'; ?>"><?php echo number_format($final_result,2,',',' '); ?> zł</p></div>
                </div>
                <section class="bg-white rounded-3xl p-5 sm:p-7 shadow-sm"><h3 class="text-xl font-black mb-4">Dodaj koszt lub zakup sprzętu</h3>
                    <form method="POST" class="grid sm:grid-cols-2 lg:grid-cols-3 gap-3 items-end"><input type="hidden" name="admin_csrf" value="<?php echo e_admin($_SESSION['admin_csrf'] ?? ''); ?>">
                        <input type="hidden" name="action" value="expense_save"><input type="hidden" name="expense_csrf" value="<?php echo e_admin($_SESSION['expense_csrf']); ?>"><input type="hidden" name="return_month" value="<?php echo e_admin($cost_month); ?>">
                        <label class="text-xs font-bold">Nazwa<input name="expense_name" maxlength="160" required placeholder="Np. paliwo, naprawa, reklama" class="block w-full border rounded-xl p-3 mt-1"></label>
                        <label class="text-xs font-bold">Kwota (zł)<input type="number" name="expense_amount" min="0.01" step="0.01" required class="block w-full border rounded-xl p-3 mt-1"></label>
                        <label class="text-xs font-bold">Data<input type="date" name="expense_date" value="<?php echo e_admin($cost_month === date('Y-m') ? date('Y-m-d') : $cost_month.'-01'); ?>" required class="block w-full border rounded-xl p-3 mt-1"></label>
                        <label class="text-xs font-bold">Rodzaj<select name="expense_type" class="block w-full border rounded-xl p-3 mt-1"><option value="expense">Koszt bieżący</option><option value="investment">Zakup sprzętu / inwestycja</option></select></label>
                        <label class="text-xs font-bold lg:col-span-2">Opis (opcjonalnie)<input name="expense_note" maxlength="1000" placeholder="Dodatkowe informacje" class="block w-full border rounded-xl p-3 mt-1"></label>
                        <button class="bg-yellow-400 rounded-xl p-3 font-black">ZAPISZ WPIS</button>
                    </form>
                </section>
                <section class="bg-white rounded-3xl p-5 sm:p-7 shadow-sm"><h3 class="text-xl font-black mb-4">Wpisy za wybrany miesiąc (<?php echo count($month_costs); ?>)</h3>
                    <?php if (!$month_costs): ?><p class="text-sm text-gray-500">Brak kosztów i inwestycji w tym miesiącu.</p><?php else: ?>
                    <div class="overflow-x-auto"><table class="w-full text-sm"><thead><tr class="text-left border-b"><th class="p-2">Data</th><th class="p-2">Nazwa</th><th class="p-2">Rodzaj</th><th class="p-2">Kwota</th><th class="p-2">Opis</th><th class="p-2">Akcje</th></tr></thead><tbody>
                    <?php foreach ($month_costs as $x): ?><tr class="border-b"><td class="p-2 whitespace-nowrap"><?php echo e_admin($x['date'] ?? ''); ?></td><td class="p-2 font-semibold"><?php echo e_admin($x['name'] ?? ''); ?></td><td class="p-2"><?php echo (($x['type'] ?? 'expense') === 'investment') ? 'Inwestycja' : 'Koszt bieżący'; ?></td><td class="p-2 font-black whitespace-nowrap"><?php echo number_format((float)($x['amount'] ?? 0),2,',',' '); ?> zł</td><td class="p-2"><?php echo e_admin($x['note'] ?? ''); ?></td><td class="p-2"><div class="flex gap-2"><button type="button" class="text-blue-700 font-bold" onclick="const f=this.closest('tr').querySelector('.edit-expense');f.classList.toggle('hidden')">Edytuj</button><form method="POST" onsubmit="return confirm('Usunąć ten wpis?')"><input type="hidden" name="admin_csrf" value="<?php echo e_admin($_SESSION['admin_csrf'] ?? ''); ?>"><input type="hidden" name="action" value="expense_delete"><input type="hidden" name="expense_csrf" value="<?php echo e_admin($_SESSION['expense_csrf']); ?>"><input type="hidden" name="expense_id" value="<?php echo e_admin($x['id'] ?? ''); ?>"><input type="hidden" name="return_month" value="<?php echo e_admin($cost_month); ?>"><button class="text-red-700 font-bold">Usuń</button></form></div></td></tr>
                    <tr class="hidden edit-expense bg-gray-50"><td colspan="6" class="p-3"><form method="POST" class="grid sm:grid-cols-2 lg:grid-cols-3 gap-2"><input type="hidden" name="admin_csrf" value="<?php echo e_admin($_SESSION['admin_csrf'] ?? ''); ?>"><input type="hidden" name="action" value="expense_save"><input type="hidden" name="expense_csrf" value="<?php echo e_admin($_SESSION['expense_csrf']); ?>"><input type="hidden" name="expense_id" value="<?php echo e_admin($x['id'] ?? ''); ?>"><input type="hidden" name="return_month" value="<?php echo e_admin($cost_month); ?>"><label class="text-xs font-bold">Nazwa<input name="expense_name" maxlength="160" required value="<?php echo e_admin($x['name'] ?? ''); ?>" class="block w-full border rounded-lg p-2 mt-1"></label><label class="text-xs font-bold">Kwota<input type="number" name="expense_amount" min="0.01" step="0.01" required value="<?php echo e_admin($x['amount'] ?? 0); ?>" class="block w-full border rounded-lg p-2 mt-1"></label><label class="text-xs font-bold">Data<input type="date" name="expense_date" required value="<?php echo e_admin($x['date'] ?? ''); ?>" class="block w-full border rounded-lg p-2 mt-1"></label><label class="text-xs font-bold">Rodzaj<select name="expense_type" class="block w-full border rounded-lg p-2 mt-1"><option value="expense" <?php echo (($x['type'] ?? 'expense') !== 'investment') ? 'selected' : ''; ?>>Koszt bieżący</option><option value="investment" <?php echo (($x['type'] ?? '') === 'investment') ? 'selected' : ''; ?>>Inwestycja</option></select></label><label class="text-xs font-bold lg:col-span-2">Opis<input name="expense_note" maxlength="1000" value="<?php echo e_admin($x['note'] ?? ''); ?>" class="block w-full border rounded-lg p-2 mt-1"></label><button class="bg-gray-900 text-white rounded-lg p-2 font-bold">ZAPISZ ZMIANY</button></form></td></tr><?php endforeach; ?>
                    </tbody></table></div><?php endif; ?>
                </section>
            </div>
        <?php endif; ?>

        <?php if ($active_tab == 'profitability'): ?>
            <?php
                $profit_year = (string)($_GET['year'] ?? 'all');
                if ($profit_year !== 'all' && !preg_match('/^20\d{2}$/', $profit_year)) $profit_year = date('Y');
                $profit_res_file = rm_rental_file('reservations.json');
                $profit_res = is_file($profit_res_file) ? json_decode((string)file_get_contents($profit_res_file), true) : [];
                if (!is_array($profit_res)) $profit_res=[];
                $profit_cost_file = rm_rental_file('costs.json');
                $profit_costs = is_file($profit_cost_file) ? json_decode((string)file_get_contents($profit_cost_file), true) : [];
                if (!is_array($profit_costs)) $profit_costs=[];
                $profit = []; $known=[];
                foreach ($profit_res as $r) {
                    if (empty($r['settled'])) continue;
                    $settledMonth=rm_admin_settlement_month($r);
                    if ($settledMonth==='' || ($profit_year!=='all' && substr($settledMonth,0,4)!==$profit_year)) continue;
                    $name=rm_admin_equipment_name($r);
                    $normalizedName=preg_replace('/\s+/u',' ',$name); $key=function_exists('mb_strtolower')?mb_strtolower($normalizedName,'UTF-8'):strtolower($normalizedName); $known[$key]=$name;
                    if (!isset($profit[$key])) $profit[$key]=['name'=>$name,'rentals'=>0,'days'=>0,'income'=>0.0,'loss'=>0.0,'purchase'=>0.0,'repairs'=>0.0];
                    $profit[$key]['rentals']++; $profit[$key]['income']+=(float)($r['income']??0); $profit[$key]['loss']+=(float)($r['loss']??0);
                    $a=DateTime::createFromFormat('!Y-m-d',(string)($r['start_date']??'')); $b=DateTime::createFromFormat('!Y-m-d',(string)($r['end_date']??''));
                    $settlementDays=(int)($r['settlement']['days']??0);
                    $profit[$key]['days'] += $settlementDays>0 ? $settlementDays : (($a && $b && $b >= $a) ? max(1,(int)$a->diff($b)->days) : 0);
                }
                foreach ($profit_costs as $cost) {
                    $name=trim((string)($cost['equipment_name']??'')); if($name==='') continue;
                    $date=(string)($cost['date']??''); if($profit_year!=='all' && substr($date,0,4)!==$profit_year) continue;
                    $normalizedName=preg_replace('/\s+/u',' ',$name); $key=function_exists('mb_strtolower')?mb_strtolower($normalizedName,'UTF-8'):strtolower($normalizedName); $known[$key]=$name;
                    if(!isset($profit[$key])) $profit[$key]=['name'=>$name,'rentals'=>0,'days'=>0,'income'=>0.0,'loss'=>0.0,'purchase'=>0.0,'repairs'=>0.0];
                    if(($cost['type']??'')==='investment') $profit[$key]['purchase']+=(float)($cost['amount']??0); else $profit[$key]['repairs']+=(float)($cost['amount']??0);
                }
                uasort($profit,function($a,$b){return ($b['income']-$b['loss']-$b['purchase']-$b['repairs']) <=> ($a['income']-$a['loss']-$a['purchase']-$a['repairs']);});
                $totalIncome=0;$totalNet=0;foreach($profit as $v){$totalIncome+=$v['income'];$totalNet+=$v['income']-$v['loss']-$v['purchase']-$v['repairs'];}
                $inventory = is_file(__DIR__.'/data.json') ? json_decode((string)file_get_contents(__DIR__.'/data.json'),true) : []; if(!is_array($inventory))$inventory=[];
                $equipment_options=[]; foreach($inventory as $product){$nm=trim((string)($product['name']??''));if($nm!=='')$equipment_options[$nm]=true;} foreach($known as $nm)$equipment_options[$nm]=true; $equipment_options=array_keys($equipment_options); sort($equipment_options,SORT_NATURAL|SORT_FLAG_CASE);
            ?>
            <div class="max-w-6xl mx-auto space-y-5">
                <div class="flex flex-wrap items-end justify-between gap-3"><div><h2 class="text-2xl font-black">Rentowność sprzętu</h2><p class="text-sm text-gray-500 mt-1">Wyniki łączone według modelu — wszystkie sztuki danego modelu razem.</p></div>
                    <form method="GET" class="flex items-center gap-2"><input type="hidden" name="tab" value="profitability"><select name="year" class="border rounded-xl p-3 bg-white"><option value="all" <?php echo $profit_year==='all'?'selected':''; ?>>Cała historia</option><?php for($yy=(int)date('Y');$yy>=2020;$yy--): ?><option value="<?php echo $yy; ?>" <?php echo $profit_year===(string)$yy?'selected':''; ?>><?php echo $yy; ?></option><?php endfor; ?></select><button class="bg-gray-900 text-white rounded-xl px-4 py-3 font-bold">Pokaż</button></form></div>
                <div class="grid sm:grid-cols-2 gap-3"><div class="bg-white rounded-2xl p-4 shadow-sm"><p class="text-xs font-bold text-gray-500">PRZYCHÓD Z NAJMU</p><p class="text-2xl font-black mt-1"><?php echo number_format($totalIncome,2,',',' '); ?> zł</p></div><div class="bg-white rounded-2xl p-4 shadow-sm"><p class="text-xs font-bold text-gray-500">WYNIK PO KOSZTACH SPRZĘTU</p><p class="text-2xl font-black mt-1 <?php echo $totalNet<0?'text-red-600':'text-green-700'; ?>"><?php echo number_format($totalNet,2,',',' '); ?> zł</p></div></div>
                <?php if(!empty($_SESSION['expense_flash'])): ?><div class="rounded-xl p-4 bg-blue-50 text-blue-800 font-bold"><?php echo e_admin($_SESSION['expense_flash']); unset($_SESSION['expense_flash']); ?></div><?php endif; ?>
                <section class="bg-white rounded-3xl p-5 shadow-sm"><h3 class="text-lg font-black mb-3">Dodaj koszt do modelu</h3><form method="POST" class="grid sm:grid-cols-2 lg:grid-cols-5 gap-3 items-end"><input type="hidden" name="admin_csrf" value="<?php echo e_admin($_SESSION['admin_csrf'] ?? ''); ?>"><input type="hidden" name="action" value="equipment_cost_save"><input type="hidden" name="expense_csrf" value="<?php echo e_admin($_SESSION['expense_csrf']); ?>"><input type="hidden" name="return_year" value="<?php echo e_admin($profit_year); ?>">
                    <label class="text-xs font-bold lg:col-span-2">Model sprzętu<select name="equipment_name" required class="block w-full border rounded-xl p-3 mt-1"><option value="">Wybierz model</option><?php foreach($equipment_options as $nm): ?><option value="<?php echo e_admin($nm); ?>"><?php echo e_admin($nm); ?></option><?php endforeach; ?></select></label>
                    <label class="text-xs font-bold">Rodzaj<select name="expense_type" class="block w-full border rounded-xl p-3 mt-1"><option value="investment">Zakup (łączny koszt)</option><option value="expense">Naprawa / serwis</option></select></label>
                    <label class="text-xs font-bold">Kwota (zł)<input type="number" name="expense_amount" min="0.01" step="0.01" required class="block w-full border rounded-xl p-3 mt-1"></label>
                    <label class="text-xs font-bold">Data<input type="date" name="expense_date" value="<?php echo date('Y-m-d'); ?>" required class="block w-full border rounded-xl p-3 mt-1"></label>
                    <label class="text-xs font-bold sm:col-span-2 lg:col-span-4">Opis (opcjonalnie)<input name="expense_note" maxlength="1000" placeholder="Np. zakup 3 sztuk / wymiana łożysk" class="block w-full border rounded-xl p-3 mt-1"></label><button class="bg-yellow-400 rounded-xl p-3 font-black">ZAPISZ KOSZT</button>
                </form></section>
                <div class="space-y-3">
                    <?php if(!$profit): ?><div class="bg-white rounded-2xl p-5 text-sm text-gray-500">Brak rozliczonych wynajmów i kosztów dla wybranego okresu.</div><?php endif; ?>
                    <?php foreach($profit as $v): $net=$v['income']-$v['loss']-$v['purchase']-$v['repairs']; ?><section class="bg-white rounded-2xl p-4 sm:p-5 shadow-sm"><div class="flex flex-wrap items-start justify-between gap-2"><h3 class="font-black text-lg"><?php echo e_admin($v['name']); ?></h3><span class="font-black text-lg <?php echo $net<0?'text-red-600':'text-green-700'; ?>"><?php echo number_format($net,2,',',' '); ?> zł</span></div><div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 mt-4 text-sm"><div><p class="text-xs text-gray-500">Wynajmy</p><b><?php echo (int)$v['rentals']; ?></b></div><div><p class="text-xs text-gray-500">Dni najmu</p><b><?php echo (int)$v['days']; ?></b></div><div><p class="text-xs text-gray-500">Przychód</p><b><?php echo number_format($v['income'],2,',',' '); ?> zł</b></div><div><p class="text-xs text-gray-500">Straty z najmu</p><b class="text-red-600"><?php echo number_format($v['loss'],2,',',' '); ?> zł</b></div><div><p class="text-xs text-gray-500">Zakup</p><b><?php echo number_format($v['purchase'],2,',',' '); ?> zł</b></div><div><p class="text-xs text-gray-500">Naprawy / serwis</p><b><?php echo number_format($v['repairs'],2,',',' '); ?> zł</b></div></div><p class="text-xs text-gray-400 mt-3">Wynik po odjęciu strat, kosztu zakupu i napraw.</p></section><?php endforeach; ?>
                </div>
            </div>
        <?php endif; ?>

        <?php if ($active_tab == 'devices'): ?>
            <?php $protocol_device_data = admin_load_protocol_devices($protocol_devices_file); ?>
            <div class="max-w-4xl mx-auto space-y-5">
                <div class="bg-white p-6 sm:p-8 rounded-3xl shadow-sm border-t-8 border-yellow-400">
                    <h2 class="text-2xl font-black">Dostęp do aplikacji protokołów</h2>
                    <p class="text-sm text-gray-500 mt-2">Wygeneruj jednorazowy link aktywacyjny dla telefonu administratora lub tabletu pomocnika. Link działa przez 24 godziny i może być użyty tylko raz.</p>
                    <?php if (!empty($_SESSION['protocol_devices_error'])): ?><div class="bg-red-50 text-red-700 p-3 rounded-xl mt-4 font-bold"><?php echo e_admin($_SESSION['protocol_devices_error']); unset($_SESSION['protocol_devices_error']); ?></div><?php endif; ?>
                    <?php if (!empty($_SESSION['protocol_devices_notice'])): ?><div class="bg-green-50 text-green-700 p-3 rounded-xl mt-4 font-bold"><?php echo e_admin($_SESSION['protocol_devices_notice']); unset($_SESSION['protocol_devices_notice']); ?></div><?php endif; ?>
                    <?php if (!empty($_SESSION['protocol_activation_flash'])): $activation = $_SESSION['protocol_activation_flash']; unset($_SESSION['protocol_activation_flash']); ?>
                        <div class="mt-5 rounded-2xl border-2 border-green-200 bg-green-50 p-4">
                            <p class="font-black text-green-800">Link aktywacyjny – <?php echo e_admin($activation['name']); ?></p>
                            <p class="text-sm text-green-800 mt-1">Ważny do: <?php echo e_admin($activation['expires']); ?>. Otwórz go na urządzeniu, które ma otrzymać dostęp.</p>
                            <input id="activationLink" readonly value="<?php echo e_admin($activation['url']); ?>" class="w-full mt-3 p-3 rounded-xl border bg-white text-sm" onclick="this.select()">
                            <button type="button" onclick="navigator.clipboard.writeText(document.getElementById('activationLink').value).then(()=>alert('Skopiowano link.')).catch(()=>{document.getElementById('activationLink').select();alert('Skopiuj zaznaczony link.');})" class="mt-2 bg-gray-900 text-white px-4 py-3 rounded-xl text-sm font-black">KOPIUJ LINK</button>
                        </div>
                    <?php endif; ?>
                    <form method="POST" class="grid sm:grid-cols-2 gap-3 mt-6"><input type="hidden" name="admin_csrf" value="<?php echo e_admin($_SESSION['admin_csrf'] ?? ''); ?>">
                        <input type="hidden" name="action" value="protocol_create_code">
                        <input type="hidden" name="protocol_devices_csrf" value="<?php echo e_admin($_SESSION['protocol_devices_csrf']); ?>">
                        <div><label class="block text-xs font-black mb-2">ROLA URZĄDZENIA</label><select name="device_role" class="w-full border rounded-xl p-3"><option value="admin">Administrator – pełne uprawnienia</option><option value="assistant">Pomocnik – bez usuwania</option></select></div>
                        <div><label class="block text-xs font-black mb-2">NAZWA URZĄDZENIA</label><input name="device_name" class="w-full border rounded-xl p-3" placeholder="Np. Telefon Dominika / Tablet pomocnika"></div>
                        <div class="sm:col-span-2"><button class="bg-yellow-400 text-black font-black px-6 py-4 rounded-xl w-full">GENERUJ LINK AKTYWACYJNY</button></div>
                    </form>
                </div>
                <div class="bg-white p-6 sm:p-8 rounded-3xl shadow-sm">
                    <h3 class="text-xl font-black mb-4">Aktywne urządzenia</h3>
                    <?php $active_devices = array_values(array_filter($protocol_device_data['devices'], function($d){ return !empty($d['active']); })); ?>
                    <?php if (!$active_devices): ?><p class="text-sm text-gray-500">Nie aktywowano jeszcze żadnego urządzenia.</p><?php else: ?>
                        <div class="space-y-3">
                        <?php foreach ($active_devices as $device): ?>
                            <div class="border rounded-2xl p-4 flex flex-wrap items-center justify-between gap-3">
                                <div><p class="font-black"><?php echo e_admin($device['name'] ?? 'Urządzenie'); ?></p><p class="text-xs text-gray-500 mt-1">Rola: <?php echo ($device['role'] ?? '') === 'assistant' ? 'Pomocnik' : 'Administrator'; ?> · Aktywowano: <?php echo e_admin($device['created_at'] ?? ''); ?></p></div>
                                <form method="POST" onsubmit="return confirm('Odebrać dostęp temu urządzeniu?');"><input type="hidden" name="admin_csrf" value="<?php echo e_admin($_SESSION['admin_csrf'] ?? ''); ?>"><input type="hidden" name="action" value="protocol_revoke_device"><input type="hidden" name="device_id" value="<?php echo e_admin($device['id'] ?? ''); ?>"><input type="hidden" name="protocol_devices_csrf" value="<?php echo e_admin($_SESSION['protocol_devices_csrf']); ?>"><button class="bg-red-600 text-white rounded-xl px-4 py-3 text-xs font-black">ODBIERZ DOSTĘP</button></form>
                            </div>
                        <?php endforeach; ?>
                        </div>
                    <?php endif; ?>
                    <p class="text-xs text-gray-500 mt-4">Po odebraniu dostępu urządzenie nie otworzy już aplikacji. W razie potrzeby wygeneruj nowy link aktywacyjny.</p>
                </div>
                <div class="bg-white p-6 sm:p-8 rounded-3xl shadow-sm">
                    <h3 class="font-black text-xl mb-3">Zmień hasło administratora</h3>
                    <p class="text-sm text-gray-600 mb-4">Po zmianie nastąpi wylogowanie. Wybierz unikalne hasło (co najmniej 14 znaków; maksymalnie 72 bajty). Kod z aplikacji Authenticator jest obowiązkowy.</p>
                    <?php if(!empty($_SESSION['password_flash'])): ?>
                        <p class="text-red-700 font-bold mb-4"><?php echo e_admin($_SESSION['password_flash']); unset($_SESSION['password_flash']); ?></p>
                    <?php endif; ?>
                    <form method="POST" class="space-y-3" autocomplete="off">
                        <input type="hidden" name="action" value="change_admin_password">
                        <input type="hidden" name="admin_csrf" value="<?php echo e_admin($_SESSION['admin_csrf']); ?>">
                        <label class="block text-sm font-bold">Obecne hasło<input type="password" name="password_old" autocomplete="current-password" class="block w-full p-3 border rounded-xl mt-1" required></label>
                        <label class="block text-sm font-bold">Nowe hasło<input type="password" name="password_new" autocomplete="new-password" minlength="14" maxlength="72" class="block w-full p-3 border rounded-xl mt-1" required></label>
                        <label class="block text-sm font-bold">Powtórz nowe hasło<input type="password" name="password_repeat" autocomplete="new-password" minlength="14" maxlength="72" class="block w-full p-3 border rounded-xl mt-1" required></label>
                        <label class="block text-sm font-bold">Kod 2FA<input type="text" name="password_otp" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" class="block w-full p-3 border rounded-xl mt-1" required></label>
                        <button class="bg-gray-900 text-white rounded-xl p-4 font-black w-full">ZAPISZ NOWE HASŁO</button>
                    </form>

                </div>
            </div>
        <?php endif; ?>

        <?php if ($active_tab == 'messages'): ?>

            <div class="space-y-4">
                <?php foreach (array_reverse($messages) as $m): ?>

                    <div class="bg-white p-6 rounded-3xl shadow-sm border-l-8 border-yellow-400 flex justify-between items-start">
                        <div class="flex-1 min-w-0">
                            <span class="text-[10px] font-black text-gray-400 uppercase">
                                <?php echo e_admin($m['date'] ?? ''); ?>
                                | Produkt: <?php echo e_admin($m['product'] ?? ''); ?>
                            </span>

                            <p class="font-black text-xl mt-1">
                                <?php echo e_admin($m['client'] ?? ''); ?>
                                (<?php echo e_admin($m['contact'] ?? ''); ?>)
                            </p>

                            <p class="mt-4 text-gray-700 bg-slate-50 p-4 rounded-2xl italic border border-slate-100 break-words">
                                "<?php echo nl2br(e_admin($m['message'] ?? '')); ?>"
                            </p>

                            <div class="mt-4">
                                <a href="tel:<?php echo e_admin($m['contact'] ?? ''); ?>"
                                   class="bg-green-500 text-white px-6 py-2 rounded-xl text-xs font-black shadow-lg">
                                    ODZWOŃ TERAZ
                                </a>
                            </div>
                        </div>

                        <form method="POST"><input type="hidden" name="admin_csrf" value="<?php echo e_admin($_SESSION['admin_csrf'] ?? ''); ?>">
                            <input type="hidden" name="action" value="delete_msg">
                            <input type="hidden" name="id" value="<?php echo e_admin($m['id'] ?? ''); ?>">
                            <button type="submit" class="text-red-200 hover:text-red-500 p-2">
                                <i class="fa-solid fa-trash"></i>
                            </button>
                        </form>
                    </div>

                <?php endforeach; ?>
            </div>

        <?php endif; ?>


        <?php if ($active_tab == 'products'): ?>

            <div class="bg-white p-8 rounded-3xl shadow-xl mb-12 border-t-8 product-form <?php echo $edit_mode ? 'border-orange-500' : 'border-green-500'; ?>">

                <h3 class="font-black mb-6 uppercase">
                    <?php echo $edit_mode ? 'Edytuj' : 'Dodaj'; ?> Sprzęt
                </h3>

                <form method="POST" enctype="multipart/form-data" class="grid gap-4"><input type="hidden" name="admin_csrf" value="<?php echo e_admin($_SESSION['admin_csrf'] ?? ''); ?>">

                    <input type="hidden" name="action" value="<?php echo $edit_mode ? 'update' : 'add'; ?>">

                    <?php if ($edit_mode): ?>
                        <input type="hidden" name="id" value="<?php echo e_admin($edit_item['id'] ?? ''); ?>">
                    <?php endif; ?>

                    <input
                        type="text"
                        name="name"
                        placeholder="Nazwa sprzętu"
                        value="<?php echo e_admin($edit_item['name'] ?? ''); ?>"
                        class="p-4 border rounded-xl outline-none focus:border-yellow-400 bg-slate-50"
                        required
                    >

                    <div class="grid grid-cols-2 gap-4">
                        <input
                            type="number"
                            name="price"
                            placeholder="Cena za dobę"
                            value="<?php echo e_admin($edit_item['price'] ?? ''); ?>"
                            class="p-4 border rounded-xl bg-slate-50"
                            min="0.01" step="0.01" max="999999.99"
                            required
                        >

                        <select name="category" class="p-4 border rounded-xl bg-white border-slate-200">
                            <option value="budowlane" <?php echo ($edit_item['category'] ?? '') == 'budowlane' ? 'selected' : ''; ?>>Budowlane</option>
                            <option value="ogrodnicze" <?php echo ($edit_item['category'] ?? '') == 'ogrodnicze' ? 'selected' : ''; ?>>Ogrodnicze</option>
                            <option value="czyszczace" <?php echo ($edit_item['category'] ?? '') == 'czyszczace' ? 'selected' : ''; ?>>Czyszczące</option>
                        </select>
                    </div>

                    <textarea
                        name="desc"
                        placeholder="Pełny opis sprzętu..."
                        class="p-4 border rounded-xl h-40 outline-none focus:border-yellow-400 bg-slate-50"
                    ><?php echo e_admin($edit_item['desc'] ?? ''); ?></textarea>

                    <!-- USTAWIENIA SEO -->
                    <div class="bg-blue-50 p-4 sm:p-5 rounded-2xl border border-blue-100">
                        <h4 class="font-black text-sm uppercase text-blue-900 mb-2">
                            <i class="fa-solid fa-magnifying-glass mr-2"></i>
                            Ustawienia SEO dla Google
                        </h4>

                        <p class="text-xs text-gray-600 mb-4">
                            Ustaw własny tytuł i opis strony tego sprzętu. Możesz zostawić pola puste,
                            jeśli chcesz korzystać z ustawień automatycznych.
                        </p>

                        <label class="block text-xs font-black uppercase text-gray-700 mb-2" for="seo_title">
                            Tytuł SEO
                        </label>

                        <input
                            id="seo_title"
                            type="text"
                            name="seo_title"
                            maxlength="70"
                            placeholder="Wynajem szlifierki do betonu Kraków | RentMor"
                            value="<?php echo e_admin($edit_item['seo_title'] ?? ''); ?>"
                            class="p-4 border rounded-xl mb-4 bg-white focus:border-blue-400 outline-none"
                        >

                        <label class="block text-xs font-black uppercase text-gray-700 mb-2" for="seo_description">
                            Opis SEO (meta description)
                        </label>

                        <textarea
                            id="seo_description"
                            name="seo_description"
                            maxlength="300"
                            rows="3"
                            placeholder="Wynajem szlifierki do betonu w Krakowie. Sprawdź cenę za dobę i skontaktuj się z RentMor."
                            class="p-4 border rounded-xl bg-white focus:border-blue-400 outline-none"
                        ><?php echo e_admin($edit_item['seo_description'] ?? ''); ?></textarea>

                        <p class="text-[11px] text-gray-500 mt-2">
                            Podaj rodzaj sprzętu, frazę „wynajem” i lokalizację Kraków.
                            Pisz naturalnie, bez nadmiernego powtarzania słów.
                        </p>
                    </div>

                    <div class="grid grid-cols-2 gap-4">
                        <input
                            type="number"
                            name="deposit"
                            placeholder="Kaucja"
                            value="<?php echo e_admin($edit_item['deposit'] ?? ''); ?>"
                            min="0" step="0.01" max="999999.99"
                            class="p-4 border rounded-xl bg-slate-50"
                        >

                        <input
                            type="text"
                            name="olx"
                            placeholder="Link OLX"
                            value="<?php echo e_admin($edit_item['olx'] ?? ''); ?>"
                            class="p-4 border rounded-xl bg-slate-50"
                        >
                    </div>

                    <div class="bg-yellow-50 p-4 rounded-xl border-2 border-dashed border-yellow-200 text-center">
                        <input type="file" name="photos[]" multiple accept="image/jpeg,image/png,image/webp,image/gif">
                        <p class="text-xs text-gray-600 mt-2">JPG, PNG, WebP lub GIF, do 8 MB i 24 MP na zdjęcie (maks. 12 plików).</p>

                        <p class="text-[10px] font-black uppercase text-yellow-600 mt-2">
                            Wybierz zdjęcia sprzętu
                        </p>

                        <?php if ($edit_mode && !empty($edit_item['images'])): ?>
                            <p class="text-sm font-bold mt-4 mb-2">Kolejność zdjęć — pierwsze jest okładką oferty</p>
                            <div id="rm-existing-photos" class="grid grid-cols-2 sm:grid-cols-4 gap-3 text-left">
                                <?php foreach ($edit_item['images'] as $existingPhoto): ?>
                                    <div class="rm-existing-photo rounded-xl border bg-white p-2">
                                        <img src="uploads/<?php echo e_admin(basename((string)$existingPhoto)); ?>" alt="Zdjęcie oferty" class="w-full h-24 object-cover rounded-lg" loading="lazy">
                                        <input type="hidden" name="photo_order[]" value="<?php echo e_admin($existingPhoto); ?>">
                                        <div class="flex items-center justify-between mt-2 gap-1">
                                            <button type="button" onclick="rmMovePhoto(this,-1)" class="border rounded-lg px-2 py-1 text-xs" title="Przesuń w lewo" aria-label="Przenieś zdjęcie wcześniej">←</button>
                                            <button type="button" onclick="rmMovePhoto(this,1)" class="border rounded-lg px-2 py-1 text-xs" title="Przesuń w prawo" aria-label="Przenieś zdjęcie później">→</button>
                                        </div>
                                        <label class="text-xs block mt-2"><input type="checkbox" name="photo_remove[]" value="<?php echo e_admin($existingPhoto); ?>" onchange="this.closest('.rm-existing-photo').style.opacity=this.checked?'0.45':'1'"> Usuń zdjęcie</label>
                                    </div>
                                <?php endforeach; ?>
                            </div>
                            <p class="text-xs text-gray-500 mt-2">Zmień kolejność strzałkami. Zaznaczone zdjęcia znikną dopiero po zapisaniu zmian.</p>
                            <script>
                            function rmMovePhoto(button, direction) {
                                const card = button.closest('.rm-existing-photo');
                                const container = card && card.parentNode;
                                if (!container) return;
                                if (direction < 0 && card.previousElementSibling) {
                                    container.insertBefore(card, card.previousElementSibling);
                                } else if (direction > 0 && card.nextElementSibling) {
                                    container.insertBefore(card.nextElementSibling, card);
                                }
                            }
                            </script>
                        <?php endif; ?>
                    </div>

                    <button
                        type="submit"
                        class="bg-gray-900 text-white p-5 rounded-2xl font-black uppercase tracking-widest shadow-lg hover:bg-black transition"
                    >
                        <?php echo $edit_mode ? 'Zapisz zmiany' : 'Opublikuj ogłoszenie'; ?>
                    </button>

                    <?php if ($edit_mode): ?>
                        <a href="?tab=products" class="text-center font-bold text-gray-400 text-xs mt-2 uppercase">
                            Anuluj edycję
                        </a>
                    <?php endif; ?>

                </form>
            </div>


            <!-- LISTA SPRZĘTU -->
            <div class="flex justify-between items-center mb-6 gap-3 flex-wrap">
                <h3 class="font-black text-xs uppercase text-gray-400 tracking-widest">
                    Twoja baza ogłoszeń
                </h3>

                <form method="POST" onsubmit="return confirm('Czy na pewno wyzerować liczniki telefonów?');"><input type="hidden" name="admin_csrf" value="<?php echo e_admin($_SESSION['admin_csrf'] ?? ''); ?>">
                    <input type="hidden" name="action" value="reset_phones">
                    <button type="submit" class="bg-white px-3 py-2 rounded-xl text-[10px] font-black uppercase text-red-500 shadow-sm border">Resetuj liczniki tel. 📞</button>
                </form>
            </div>

            <div class="space-y-4">
                <?php foreach ($products as $p): ?>

                    <div class="bg-white p-5 rounded-3xl shadow-sm border border-slate-100 flex items-center justify-between hover:shadow-md transition product-row">

                        <div class="flex items-center gap-5 product-row-info">
                            <img
                                src="uploads/<?php echo e_admin($p['images'][0] ?? ''); ?>"
                                class="w-16 h-16 object-cover rounded-2xl bg-slate-50 p-1 border shadow-inner"
                                alt=""
                            >

                            <div class="min-w-0">
                                <p class="font-black text-gray-900 text-lg leading-tight mb-1 break-words">
                                    <?php echo e_admin($p['name'] ?? ''); ?>
                                </p>

                                <div class="flex flex-wrap gap-4 mt-2 font-black uppercase text-[10px]">
                                    <span class="text-yellow-600"><?php echo e_admin($p['price'] ?? 0); ?> zł/d</span>

                                    <span class="text-blue-500">
                                        <i class="fa-solid fa-eye mr-1"></i>
                                        <?php echo $stats['product_views'][$p['id']] ?? 0; ?>
                                    </span>

                                    <span class="text-green-600">
                                        <i class="fa-solid fa-phone mr-1"></i>
                                        <?php echo $p['phone_clicks'] ?? 0; ?>
                                    </span>
                                </div>

                                <?php if (!empty($p['seo_title']) || !empty($p['seo_description'])): ?>
                                    <p class="text-[10px] text-blue-600 mt-2">
                                        <i class="fa-solid fa-check-circle mr-1"></i>
                                        Ustawienia SEO zapisane
                                    </p>
                                <?php else: ?>
                                    <p class="text-[10px] text-gray-400 mt-2">SEO: ustawienia automatyczne</p>
                                <?php endif; ?>
                            </div>
                        </div>

                        <div class="flex gap-2 product-row-actions">
                            <a
                                href="?tab=products&edit_id=<?php echo e_admin($p['id'] ?? ''); ?>"
                                class="bg-yellow-400 p-3 rounded-xl text-[10px] font-black"
                            >
                                EDYTUJ
                            </a>

                            <form method="POST" onsubmit="return confirm('Czy na pewno usunąć ten sprzęt?');"><input type="hidden" name="admin_csrf" value="<?php echo e_admin($_SESSION['admin_csrf'] ?? ''); ?>">
                                <input type="hidden" name="action" value="delete">
                                <input type="hidden" name="id" value="<?php echo e_admin($p['id'] ?? ''); ?>">

                                <button type="submit" class="bg-red-50 text-red-500 p-3 rounded-xl text-[10px] font-black">
                                    USUŃ
                                </button>
                            </form>
                        </div>

                    </div>

                <?php endforeach; ?>
            </div>

        <?php endif; ?>

    </div>

<?php endif; ?>

</body>
</html>
