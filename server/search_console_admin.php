<?php
declare(strict_types=1);
if (realpath((string)($_SERVER['SCRIPT_FILENAME'] ?? '')) === __FILE__) { http_response_code(404); exit; }
require_once __DIR__ . '/search_console.php';

/** Called only after the existing password, MFA, password-version and session checks. */
function rm_gsc_admin_handle(bool $authorized): void {
    $action=is_string($_POST['gsc_action'] ?? null)?$_POST['gsc_action']:'';
    $callback=isset($_GET['gsc_callback']);
    if (!$authorized) {
        if ($action!=='' || $callback) { http_response_code(403); exit('Zaloguj się jako administrator i rozpocznij ponownie.'); }
        return;
    }
    if (empty($_SESSION['gsc_csrf'])) $_SESSION['gsc_csrf']=bin2hex(random_bytes(32));
    if (($_GET['tab'] ?? '')==='google' || $callback) header('Referrer-Policy: no-referrer');
    if ($action==='' && !$callback) return;
    try {
        if ($callback) {
            $verifier=rm_gsc_consume_state($_SESSION,$_GET['state'] ?? null);
            unset($_SESSION['gsc_authorize_url']);
            if (isset($_GET['error'])) throw new RuntimeException('Nie udzielono dostępu Google. Możesz spróbować ponownie.');
            $code=$_GET['code'] ?? null;
            if (!is_string($code) || $code==='' || strlen($code)>4096) throw new RuntimeException('Nieprawidłowy powrót z Google.');
            rm_gsc_lock(static function() use ($code,$verifier): void {
                $client=rm_gsc_read('client.json');
                if (!$client) throw new RuntimeException('Brak konfiguracji klienta Google.');
                $result=rm_gsc_http('https://oauth2.googleapis.com/token',[
                    'code'=>$code,'client_id'=>$client['client_id'],'client_secret'=>$client['client_secret'],
                    'redirect_uri'=>RM_GSC_CALLBACK,'grant_type'=>'authorization_code','code_verifier'=>$verifier], '', true);
                rm_gsc_write('token.json',rm_gsc_token_result($result));
            });
            $_SESSION['gsc_notice']='Połączono konto Google. Kliknij „Pobierz dane”, aby sprawdzić dostęp do rentmor.pl.';
        } else {
            if (($_SERVER['REQUEST_METHOD'] ?? '')!=='POST' || !is_string($_POST['gsc_csrf'] ?? null)
                || !hash_equals($_SESSION['gsc_csrf'],$_POST['gsc_csrf'])) { http_response_code(403); exit('Odśwież panel i ponów operację.'); }
            rm_gsc_lock(static function() use ($action): void {
                if ($action==='configure') {
                    $id=$_POST['gsc_client_id'] ?? null; $secret=$_POST['gsc_client_secret'] ?? null;
                    if (!is_string($id)||!is_string($secret)) throw new RuntimeException('Nieprawidłowa konfiguracja.');
                    rm_gsc_write('client.json',rm_gsc_validate_client($id,$secret));
                    rm_gsc_clear_connection();
                    $_SESSION['gsc_notice']='Zapisano konfigurację. Teraz połącz konto Google.';
                } elseif ($action==='connect') {
                    $client=rm_gsc_read('client.json');
                    if (!$client) throw new RuntimeException('Najpierw skonfiguruj klienta Google.');
                    $_SESSION['gsc_authorize_url']=rm_gsc_authorize($client,$_SESSION);
                } elseif ($action==='disconnect') {
                    rm_gsc_clear_connection();
                    $_SESSION['gsc_notice']='Usunięto połączenie i raporty z panelu. Uprawnienia aplikacji możesz też usunąć na koncie Google.';
                } elseif ($action==='refresh') {
                    $days=rm_gsc_days($_POST['gsc_days'] ?? '28');
                    $last=(int)($_SESSION['gsc_refresh_at'] ?? 0);
                    if ($last>time()-60) throw new RuntimeException('Poczekaj minutę przed kolejnym pobraniem.');
                    $_SESSION['gsc_refresh_at']=time();
                    $client=rm_gsc_read('client.json');
                    if (!$client) throw new RuntimeException('Najpierw skonfiguruj klienta Google.');
                    $token=rm_gsc_access_token($client);
                    $report=rm_gsc_build_report($days,static fn(array $body):array=>rm_gsc_http(
                        'https://www.googleapis.com/webmasters/v3/sites/'.rawurlencode(RM_GSC_PROPERTY).'/searchAnalytics/query',$body,$token));
                    rm_gsc_write('cache-'.$days.'.json',$report);
                    $_SESSION['gsc_notice']='Pobrano aktualny raport Google.';
                } else throw new RuntimeException('Nieznana operacja Google.');
            });
        }
    } catch (Throwable $e) {
        // Only explicitly authored validation messages reach the owner; no transport bodies or secrets.
        $_SESSION['gsc_error']=$e instanceof RuntimeException || $e instanceof InvalidArgumentException
            ? $e->getMessage() : 'Nie udało się odczytać danych Google. Zachowano poprzedni raport.';
    }
    $days=rm_gsc_days($_POST['gsc_days'] ?? $_GET['days'] ?? '28');
    header('Location: admin.php?tab=google&days='.$days, true, 303); exit;
}
function rm_gsc_clear_connection(): void {
    foreach (['token.json','cache-7.json','cache-28.json','cache-90.json'] as $name) {
        $path=rm_gsc_path($name); if (file_exists($path) && !@unlink($path)) throw new RuntimeException('Nie udało się usunąć połączenia Google.');
    }
    unset($_SESSION['gsc_oauth'],$_SESSION['gsc_authorize_url']);
}
function rm_gsc_days($value): int { return is_scalar($value) && in_array((string)$value,['7','28','90'],true)?(int)$value:28; }
function rm_gsc_e($value): string { return htmlspecialchars((string)$value,ENT_QUOTES | ENT_SUBSTITUTE,'UTF-8'); }
function rm_gsc_number($value, int $decimals=0): string { return $value===null?'—':number_format((float)$value,$decimals,',',' '); }
function rm_gsc_delta($current,$previous,int $decimals=0): string {
    if ($current===null || $previous===null) return '—';
    $delta=(float)$current-(float)$previous;
    return ($delta>0?'+':'').rm_gsc_number($delta,$decimals);
}
function rm_gsc_form(string $action,int $days,string $label): string {
    return '<form method="post" action="admin.php?tab=google"><input type="hidden" name="gsc_csrf" value="'.rm_gsc_e($_SESSION['gsc_csrf']).'">'
        .'<input type="hidden" name="gsc_action" value="'.rm_gsc_e($action).'"><input type="hidden" name="gsc_days" value="'.$days.'">'
        .'<button class="bg-gray-900 text-white rounded-xl px-4 py-3 font-bold">'.rm_gsc_e($label).'</button></form>';
}
function rm_gsc_admin_render(bool $authorized,array $products): void {
    if (!$authorized) { http_response_code(403); return; }
    $days=rm_gsc_days($_GET['days'] ?? '28'); $client=null; $connected=false; $report=null; $error='';
    try { $client=rm_gsc_read('client.json'); $connected=rm_gsc_read('token.json')!==null; $report=rm_gsc_read('cache-'.$days.'.json'); }
    catch(Throwable $e) { $error='Magazyn Search Console jest niedostępny. Sprawdź konfigurację hostingu.'; }
    echo '<section class="bg-white p-4 sm:p-6 rounded-3xl shadow-sm mb-8" aria-labelledby="gsc-title"><h2 id="gsc-title" class="text-xl font-black mb-2">Widoczność w Google — Search Console</h2>';
    echo '<p class="text-sm text-gray-600 mb-4">Organiczne wyniki rentmor.pl. Dane Google kończą się trzy dni przed dzisiejszą datą. Odsłony strony i kliknięcia telefonu są liczone osobno.</p>';
    foreach (['gsc_notice'=>'text-green-800','gsc_error'=>'text-red-700'] as $key=>$style) {
        if (isset($_SESSION[$key])) { echo '<p role="status" class="p-3 mb-4 rounded-xl bg-gray-50 '.$style.'">'.rm_gsc_e($_SESSION[$key]).'</p>'; unset($_SESSION[$key]); }
    }
    if ($error!=='') echo '<p role="alert">'.rm_gsc_e($error).'</p>';
    echo '<form method="get" action="admin.php" class="flex flex-wrap items-end gap-3 mb-4"><input type="hidden" name="tab" value="google"><label>Okres<select name="days" class="block border p-3 rounded-xl">';
    foreach ([7,28,90] as $n) echo '<option value="'.$n.'"'.($n===$days?' selected':'').'>'.$n.' dni</option>';
    echo '</select></label><button class="border p-3 rounded-xl">Pokaż zapisany raport</button></form>';
    echo '<div class="flex flex-wrap gap-3 mb-4">';
    if ($client) echo rm_gsc_form('connect',$days,$connected?'Połącz inne konto Google':'Połącz konto Google');
    if ($connected) echo rm_gsc_form('refresh',$days,'Pobierz dane').rm_gsc_form('disconnect',$days,'Rozłącz i usuń raporty');
    echo '</div>';
    if (isset($_SESSION['gsc_authorize_url'],$_SESSION['gsc_oauth']['at']) && time()-(int)$_SESSION['gsc_oauth']['at']<=600) {
        echo '<p class="mb-4"><a class="inline-block bg-yellow-400 text-black p-3 rounded-xl font-bold" rel="noreferrer" href="'.rm_gsc_e($_SESSION['gsc_authorize_url']).'">Przejdź do Google i udziel dostępu tylko do odczytu</a></p>';
    }
    echo '<details class="border rounded-xl p-4 mb-6"><summary class="font-bold cursor-pointer">'.($client?'Zmień konfigurację Google':'Pierwsze połączenie — konfiguracja Google').'</summary>';
    echo '<p class="my-3 text-sm">W Google Cloud włącz Search Console API i utwórz klienta OAuth typu „Aplikacja internetowa”. Dodaj poniższy adres przekierowania. Konto łączone z panelem musi mieć dostęp do rentmor.pl w Search Console. Konfiguracja jest zapisywana poza publiczną stroną.</p>';
    echo '<p class="break-all font-mono text-xs mb-3">'.rm_gsc_e(RM_GSC_CALLBACK).'</p>';
    echo '<form method="post" action="admin.php?tab=google" class="grid gap-3"><input type="hidden" name="gsc_action" value="configure"><input type="hidden" name="gsc_csrf" value="'.rm_gsc_e($_SESSION['gsc_csrf']).'">'
        .'<label>Identyfikator klienta Google<input class="block border rounded-xl p-3 w-full" name="gsc_client_id" maxlength="240" autocomplete="off" required></label>'
        .'<label>Sekret klienta Google<input class="block border rounded-xl p-3 w-full" type="password" name="gsc_client_secret" maxlength="512" autocomplete="new-password" required></label>'
        .'<p class="text-sm">Zapis nowej konfiguracji usuwa dotychczasowe połączenie i raporty.</p><button class="bg-gray-900 text-white rounded-xl p-3 font-bold">Zapisz konfigurację</button></form></details>';
    if (!$report) { echo '<p class="text-gray-600">Brak pobranego raportu dla tego okresu. '.($connected?'Kliknij „Pobierz dane”.':'Połącz konto Google, aby pobrać wyniki.').'</p></section>'; return; }
    if (($report['property'] ?? '')!==RM_GSC_PROPERTY || ($report['days'] ?? 0)!==$days || !isset($report['current']['pages']['rows'],$report['previous']['pages']['rows'],$report['current']['queries']['rows'])) {
        echo '<p>Raport jest nieprawidłowy. Pobierz dane ponownie.</p></section>'; return;
    }
    echo '<p class="text-sm mb-3">Raport: '.rm_gsc_e(implode(' – ',$report['ranges']['current'])).' · porównanie: '.rm_gsc_e(implode(' – ',$report['ranges']['previous'])).'<br>Pobrano: '.rm_gsc_e($report['fetched_at']).'. Zmiana oznacza różnicę względem poprzedniego okresu; niższa pozycja liczbowa jest lepsza.</p>';
    $expected=rm_gsc_periods($days);
    if ($report['ranges']!==$expected) echo '<p role="status" class="bg-yellow-50 p-3 rounded-xl mb-3">To zapisany, starszy raport. Kliknij „Pobierz dane”, aby go zaktualizować.</p>';
    $truncated=false; foreach (['current','previous'] as $period) foreach(['pages','queries'] as $kind) $truncated=$truncated || !empty($report[$period][$kind]['truncated']);
    if ($truncated) echo '<p role="status">Raport osiągnął limit pobierania. Wyniki są częściowe.</p>';
    $current=rm_gsc_group($report['current']['pages']['rows'],$products);
    $previous=rm_gsc_group($report['previous']['pages']['rows'],$products);
    $names=[]; foreach($products as $p) if(is_array($p)&&isset($p['id'],$p['name'])) $names[(string)$p['id']]=(string)$p['name'];
    $names['_other']='Pozostałe strony / nierozpoznane adresy';
    $empty=rm_gsc_summary([]);
    echo '<div class="overflow-x-auto"><table class="w-full text-sm"><caption class="text-left font-bold mb-2">Sprzęt i pozostałe strony — stare i nowe adresy łącznie</caption><thead><tr class="text-left border-b"><th class="p-2">Sprzęt</th><th class="p-2">Kliknięcia (zmiana)</th><th class="p-2">Wyświetlenia (zmiana)</th><th class="p-2">CTR (zmiana p.p.)</th><th class="p-2">Śr. pozycja (zmiana)</th></tr></thead><tbody>';
    uasort($current,static fn($a,$b)=>$b['impressions']<=>$a['impressions']);
    $order=array_unique(array_merge(array_keys($current),array_keys($previous),array_keys($names)));
    foreach($order as $id) {
        $a=$current[$id]??$empty; $b=$previous[$id]??$empty;
        echo '<tr class="border-b"><th scope="row" class="p-2 text-left"><a class="underline" href="?tab=google&amp;days='.$days.'&amp;equipment='.rawurlencode((string)$id).'">'.rm_gsc_e($names[$id]??'Nierozpoznany sprzęt').'</a></th>';
        foreach(['clicks'=>0,'impressions'=>0,'ctr'=>2,'position'=>2] as $metric=>$decimals) echo '<td class="p-2 whitespace-nowrap">'.rm_gsc_number($a[$metric],$decimals).($metric==='ctr'&&$a[$metric]!==null?'%':'').' <span class="text-gray-500">('.rm_gsc_delta($a[$metric],$b[$metric],$decimals).')</span></td>';
        echo '</tr>';
    }
    echo '</tbody></table></div><p class="text-xs text-gray-600 my-4">Zera oznaczają brak wierszy w pobranym raporcie stron. Nie oznaczają braku indeksacji. Średnia pozycja jest ważona wyświetleniami. Google może pomijać część danych.</p>';
    $selected=is_string($_GET['equipment']??null)?$_GET['equipment']:'';
    if ($selected!=='' && array_key_exists($selected,$names)) {
        $queries=rm_gsc_group($report['current']['queries']['rows'],$products,true)[$selected]??[];
        $oldQueries=rm_gsc_group($report['previous']['queries']['rows'],$products,true)[$selected]??[];
        uasort($queries,static fn($a,$b)=>($b['clicks']<=>$a['clicks'])?:($b['impressions']<=>$a['impressions']));
        echo '<h3 class="font-black mt-6 mb-3">Frazy: '.rm_gsc_e($names[$selected]).'</h3><div class="overflow-x-auto"><table class="w-full text-sm"><thead><tr class="text-left border-b"><th class="p-2">Fraza</th><th class="p-2">Kliknięcia (zmiana)</th><th class="p-2">Wyświetlenia</th><th class="p-2">CTR</th><th class="p-2">Śr. pozycja</th></tr></thead><tbody>';
        foreach(array_slice($queries,0,100,true) as $query=>$stats) echo '<tr class="border-b"><th class="p-2 text-left" scope="row">'.rm_gsc_e($query).'</th><td class="p-2">'.rm_gsc_number($stats['clicks']).' ('.rm_gsc_delta($stats['clicks'],$oldQueries[$query]['clicks']??0).')</td><td class="p-2">'.rm_gsc_number($stats['impressions']).'</td><td class="p-2">'.rm_gsc_number($stats['ctr'],2).'%</td><td class="p-2">'.rm_gsc_number($stats['position'],2).'</td></tr>';
        if (!$queries) echo '<tr><td colspan="5" class="p-3">Brak ujawnionych fraz dla tego sprzętu w tym okresie.</td></tr>';
        echo '</tbody></table></div><p class="text-xs mt-3">Do 100 fraz. Google ukrywa część zapytań ze względu na prywatność, dlatego suma fraz może być mniejsza niż wynik sprzętu.</p>';
    }
    echo '</section>';
}
