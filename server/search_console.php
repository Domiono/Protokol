<?php
declare(strict_types=1);
// Private helper, never a standalone public endpoint.
if (realpath((string)($_SERVER['SCRIPT_FILENAME'] ?? '')) === __FILE__) { http_response_code(404); exit; }

const RM_GSC_PROPERTY = 'sc-domain:rentmor.pl';
const RM_GSC_CALLBACK = 'https://rentmor.pl/admin.php?tab=google&gsc_callback=1';
const RM_GSC_SCOPE = 'https://www.googleapis.com/auth/webmasters.readonly';

function rm_gsc_dir(): string {
    $parent = dirname(__DIR__) . '/rentmor-private';
    $dir = $parent . '/search-console';
    foreach ([$parent, $dir] as $p) {
        if (is_link($p)) throw new RuntimeException('Niebezpieczna ścieżka magazynu.');
        if (!is_dir($p) && !@mkdir($p, 0700)) throw new RuntimeException('Magazyn Google jest niedostępny.');
    }
    if (!@chmod($dir, 0700) || ((fileperms($dir) & 0777) !== 0700)) throw new RuntimeException('Uprawnienia magazynu Google.');
    return $dir;
}
function rm_gsc_path(string $name): string {
    if (!preg_match('/^(?:client|token|cache-(?:7|28|90))\.json$/D', $name)) throw new InvalidArgumentException('Niedozwolony plik.');
    $path = rm_gsc_dir() . '/' . $name;
    if (is_link($path)) throw new RuntimeException('Niebezpieczny plik.');
    return $path;
}
function rm_gsc_read(string $name): ?array {
    $path = rm_gsc_path($name);
    if (!file_exists($path)) return null;
    if (!is_file($path) || filesize($path) > 32000000 || (fileperms($path) & 0077)) throw new RuntimeException('Nieprawidłowy magazyn Google.');
    $raw = @file_get_contents($path);
    $result = json_decode((string)$raw, true, 512, JSON_THROW_ON_ERROR);
    if (!is_array($result)) throw new RuntimeException('Uszkodzony zapis Google.');
    return $result;
}
function rm_gsc_write(string $name, array $data): void {
    $path = rm_gsc_path($name);
    $json = json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
    if (strlen($json) > 32000000) throw new RuntimeException('Raport jest za duży.');
    $tmp = $path . '.' . bin2hex(random_bytes(12)) . '.tmp';
    $oldMask = umask(0077);
    try {
        $fp = @fopen($tmp, 'xb');
        if (!$fp) throw new RuntimeException('Nie udało się zapisać raportu.');
        try {
            if (!chmod($tmp, 0600) || fwrite($fp, $json) !== strlen($json) || !fflush($fp)) throw new RuntimeException('Niepełny zapis raportu.');
        } finally { fclose($fp); }
        if (!@rename($tmp, $path)) throw new RuntimeException('Nie udało się podmienić raportu.');
    } finally { @unlink($tmp); umask($oldMask); }
}
function rm_gsc_lock(callable $fn) {
    $path = rm_gsc_dir() . '/operation.lock';
    if (is_link($path)) throw new RuntimeException('Niebezpieczna blokada.');
    $old = umask(0077); $fp = @fopen($path, 'c+'); umask($old);
    if (!$fp) throw new RuntimeException('Brak blokady Google.');
    try {
        if (!chmod($path, 0600) || !flock($fp, LOCK_EX | LOCK_NB)) throw new RuntimeException('Trwa inne odświeżanie Google. Spróbuj ponownie za chwilę.');
        return $fn();
    } finally { flock($fp, LOCK_UN); fclose($fp); }
}
function rm_gsc_http(string $url, array $payload, string $token = '', bool $form = false): array {
    // Fixed Google endpoints only; neither the host header nor form values select a destination.
    if ($url !== 'https://oauth2.googleapis.com/token' && $url !== 'https://www.googleapis.com/webmasters/v3/sites/' . rawurlencode(RM_GSC_PROPERTY) . '/searchAnalytics/query') throw new InvalidArgumentException('Niedozwolony adres Google.');
    if (!function_exists('curl_init')) throw new RuntimeException('Hosting wymaga rozszerzenia PHP cURL.');
    $ch = curl_init($url); $raw = ''; $tooLarge = false;
    curl_setopt_array($ch, [CURLOPT_POST=>true, CURLOPT_CONNECTTIMEOUT=>10, CURLOPT_TIMEOUT=>45,
        CURLOPT_FOLLOWLOCATION=>false, CURLOPT_PROTOCOLS=>CURLPROTO_HTTPS,
        CURLOPT_SSL_VERIFYPEER=>true, CURLOPT_SSL_VERIFYHOST=>2,
        CURLOPT_POSTFIELDS=>$form ? http_build_query($payload) : json_encode($payload, JSON_THROW_ON_ERROR),
        CURLOPT_HTTPHEADER=>array_merge(['Content-Type: ' . ($form ? 'application/x-www-form-urlencoded' : 'application/json')], $token === '' ? [] : ['Authorization: Bearer ' . $token]),
        CURLOPT_WRITEFUNCTION=>static function($ch, string $part) use (&$raw, &$tooLarge): int {
            if (strlen($raw) + strlen($part) > 16000000) { $tooLarge=true; return 0; }
            $raw .= $part; return strlen($part);
        }]);
    try { $ok = curl_exec($ch); $status = curl_getinfo($ch, CURLINFO_HTTP_CODE); }
    finally { curl_close($ch); }
    // Never reflect Google's raw error body, authorization code or token into HTML/logs.
    if ($ok === false || $tooLarge) throw new RuntimeException('Nie udało się połączyć z Google. Zachowano poprzedni raport.');
    if ($status === 401 || $status === 400) throw new RuntimeException('Google odrzucił żądanie. Sprawdź połączenie konta i konfigurację.');
    if ($status === 403) throw new RuntimeException('Brak dostępu do rentmor.pl albo wyłączone Search Console API.');
    if ($status === 429) throw new RuntimeException('Limit Google. Spróbuj ponownie później.');
    if ($status !== 200) throw new RuntimeException('Google jest chwilowo niedostępne. Zachowano poprzedni raport.');
    $data = json_decode($raw, true, 512, JSON_THROW_ON_ERROR);
    if (!is_array($data)) throw new RuntimeException('Nieprawidłowa odpowiedź Google.');
    return $data;
}
function rm_gsc_validate_client(string $id, string $secret): array {
    $id = trim($id); $secret = trim($secret);
    if (!preg_match('/^[A-Za-z0-9._-]{10,200}\.apps\.googleusercontent\.com$/D', $id)
        || strlen($secret)<10 || strlen($secret)>512 || preg_match('/[\x00-\x20\x7f]/', $secret)) throw new InvalidArgumentException('Sprawdź identyfikator i sekret klienta Google.');
    return ['client_id'=>$id, 'client_secret'=>$secret];
}
function rm_gsc_authorize(array $client, array &$session, ?int $now = null): string {
    $now = $now ?? time();
    $verifier = bin2hex(random_bytes(32));
    $session['gsc_oauth'] = ['state'=>bin2hex(random_bytes(32)), 'verifier'=>$verifier, 'at'=>$now];
    return 'https://accounts.google.com/o/oauth2/v2/auth?' . http_build_query([
        'client_id'=>$client['client_id'], 'redirect_uri'=>RM_GSC_CALLBACK, 'response_type'=>'code',
        'scope'=>RM_GSC_SCOPE, 'access_type'=>'offline', 'prompt'=>'consent',
        'state'=>$session['gsc_oauth']['state'], 'code_challenge_method'=>'S256',
        'code_challenge'=>rtrim(strtr(base64_encode(hash('sha256', $verifier, true)), '+/', '-_'), '=')]);
}
function rm_gsc_consume_state(array &$session, $state, ?int $now = null): string {
    $entry = $session['gsc_oauth'] ?? []; unset($session['gsc_oauth']);
    $now = $now ?? time();
    if (!is_string($state) || !is_string($entry['state'] ?? null) || !hash_equals($entry['state'], $state)
        || $now < (int)($entry['at'] ?? 0) || $now-(int)($entry['at'] ?? 0)>600 || !is_string($entry['verifier'] ?? null)) throw new RuntimeException('Wygasło połączenie Google. Rozpocznij ponownie.');
    return $entry['verifier'];
}
function rm_gsc_token_result(array $data, ?array $previous = null): array {
    if (!is_string($data['access_token'] ?? null) || $data['access_token']==='' || strlen($data['access_token'])>8192
        || preg_match('/[\x00-\x20\x7f]/', $data['access_token'])) throw new RuntimeException('Google nie zwrócił poprawnego tokenu.');
    if (isset($data['scope']) && (!is_string($data['scope']) || !in_array(RM_GSC_SCOPE, explode(' ', $data['scope']), true))) throw new RuntimeException('Google nie przyznał odczytu Search Console.');
    $refresh = $data['refresh_token'] ?? ($previous['refresh_token'] ?? '');
    if (!is_string($refresh) || $refresh==='' || strlen($refresh)>8192 || preg_match('/[\x00-\x20\x7f]/', $refresh)) throw new RuntimeException('Brak trwałego połączenia Google. Połącz konto ponownie.');
    return ['access_token'=>$data['access_token'], 'refresh_token'=>$refresh, 'expires_at'=>time()+min(3600,max(60,(int)($data['expires_in'] ?? 3600)))];
}
function rm_gsc_access_token(array $client): string {
    $token = rm_gsc_read('token.json');
    if (!$token) throw new RuntimeException('Najpierw połącz konto Google.');
    if ((int)($token['expires_at'] ?? 0)>time()+90) return (string)$token['access_token'];
    $data = rm_gsc_http('https://oauth2.googleapis.com/token', ['client_id'=>$client['client_id'],
        'client_secret'=>$client['client_secret'], 'refresh_token'=>$token['refresh_token'], 'grant_type'=>'refresh_token'], '', true);
    $token = rm_gsc_token_result($data, $token); rm_gsc_write('token.json', $token);
    return $token['access_token'];
}
function rm_gsc_periods(int $days, ?DateTimeImmutable $today = null): array {
    if (!in_array($days, [7,28,90], true)) throw new InvalidArgumentException('Nieprawidłowy okres.');
    // GSC dates use Pacific Time. Avoid the most recent three incomplete days.
    $today = ($today ?? new DateTimeImmutable('now', new DateTimeZone('America/Los_Angeles')))->setTimezone(new DateTimeZone('America/Los_Angeles'));
    $end = $today->setTime(0,0)->modify('-3 days'); $start = $end->modify('-'.($days-1).' days');
    return ['current'=>[$start->format('Y-m-d'),$end->format('Y-m-d')],
        'previous'=>[$start->modify('-'.$days.' days')->format('Y-m-d'),$start->modify('-1 day')->format('Y-m-d')]];
}
function rm_gsc_query_rows(callable $request, array $range, array $dimensions): array {
    $rows=[]; $limit=25000; $truncated=false;
    for ($page=0; $page<4; ++$page) {
        $response=$request(['startDate'=>$range[0], 'endDate'=>$range[1], 'dimensions'=>$dimensions,
            'type'=>'web', 'dataState'=>'final', 'rowLimit'=>$limit, 'startRow'=>$page*$limit]);
        $batch=$response['rows'] ?? [];
        if (!is_array($batch) || !array_is_list($batch) || count($batch)>$limit) throw new RuntimeException('Nieprawidłowe wiersze Google.');
        foreach ($batch as $row) {
            if (!is_array($row) || !is_array($row['keys'] ?? null) || count($row['keys'])!==count($dimensions)) throw new RuntimeException('Nieprawidłowe wymiary Google.');
            foreach ($row['keys'] as $key) if (!is_string($key) || strlen($key)>4096) throw new RuntimeException('Nieprawidłowy wymiar Google.');
            foreach (['clicks','impressions','position'] as $field) {
                if (!isset($row[$field]) || !is_numeric($row[$field]) || !is_finite((float)$row[$field]) || $row[$field]<0) throw new RuntimeException('Nieprawidłowe metryki Google.');
            }
            if ($row['clicks']>$row['impressions']) throw new RuntimeException('Niespójne metryki Google.');
            $rows[]=['keys'=>$row['keys'], 'clicks'=>(float)$row['clicks'], 'impressions'=>(float)$row['impressions'], 'position'=>(float)$row['position']];
        }
        if (count($batch)<$limit) break;
        if ($page===3) $truncated=true;
    }
    return ['rows'=>$rows, 'truncated'=>$truncated];
}
function rm_gsc_build_report(int $days, callable $request, ?DateTimeImmutable $today = null): array {
    $ranges=rm_gsc_periods($days,$today); $report=['property'=>RM_GSC_PROPERTY, 'days'=>$days, 'fetched_at'=>gmdate('c'), 'ranges'=>$ranges];
    foreach ($ranges as $name=>$range) {
        // Page totals are independent of query data: anonymized queries must not reduce equipment totals.
        $report[$name]=['pages'=>rm_gsc_query_rows($request,$range,['page']),
            'queries'=>rm_gsc_query_rows($request,$range,['page','query'])];
    }
    return $report;
}
function rm_gsc_slug(string $name): string {
    $name = strtr(trim($name), ['Ą'=>'a','Ć'=>'c','Ę'=>'e','Ł'=>'l','Ń'=>'n','Ó'=>'o','Ś'=>'s','Ź'=>'z','Ż'=>'z','ą'=>'a','ć'=>'c','ę'=>'e','ł'=>'l','ń'=>'n','ó'=>'o','ś'=>'s','ź'=>'z','ż'=>'z']);
    return trim((string)preg_replace('/[^a-z0-9]+/', '-', strtolower($name)), '-');
}
function rm_gsc_equipment(string $url, array $products): ?string {
    $parts=parse_url($url);
    if (!$parts || !in_array(strtolower((string)($parts['host'] ?? '')), ['rentmor.pl','www.rentmor.pl'],true)
        || !in_array(strtolower((string)($parts['scheme'] ?? '')), ['http','https'],true) || isset($parts['user']) || isset($parts['port'])) return null;
    $path=$parts['path'] ?? '/'; parse_str($parts['query'] ?? '',$params);
    $matches=[];
    foreach ($products as $product) {
        if (!is_array($product) || !is_string($product['name'] ?? null)) continue;
        $id=(string)($product['id'] ?? ''); if ($id==='') continue;
        $slug=rm_gsc_slug($product['name']);
        $legacy=in_array($path,['/','/index.php'],true) && is_string($params['id'] ?? null) && hash_equals($id,$params['id']);
        if ($legacy || ($slug!=='' && rtrim($path,'/')==='/'.$slug)) $matches[$id]=true;
    }
    return count($matches)===1 ? (string)array_key_first($matches) : null;
}
function rm_gsc_summary(array $rows): array {
    $clicks=0.0; $impressions=0.0; $weighted=0.0;
    foreach ($rows as $row) { $clicks+=$row['clicks']; $impressions+=$row['impressions']; $weighted+=$row['position']*$row['impressions']; }
    return ['clicks'=>$clicks,'impressions'=>$impressions,'ctr'=>$impressions>0?100*$clicks/$impressions:null,'position'=>$impressions>0?$weighted/$impressions:null];
}
function rm_gsc_group(array $rows, array $products, bool $queries=false): array {
    $group=[];
    foreach ($rows as $row) {
        $id=rm_gsc_equipment((string)$row['keys'][0],$products) ?? '_other';
        $key=$queries ? (string)$row['keys'][1] : $id;
        if ($queries) $group[$id][$key][]=$row; else $group[$key][]=$row;
    }
    if ($queries) { foreach($group as &$items) foreach($items as &$rows2) $rows2=rm_gsc_summary($rows2); unset($items,$rows2); }
    else foreach($group as &$rows2) $rows2=rm_gsc_summary($rows2);
    return $group;
}
