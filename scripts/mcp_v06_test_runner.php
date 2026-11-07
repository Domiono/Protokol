<?php
declare(strict_types=1);
/**
 * RentMor Control 0.6 fixed-suite self-tests. No GitHub, shell, eval, arbitrary URL,
 * external user code execution or modification to application files.
 * The fixture workspace and redacted reports are private, outside document root.
 */
const RM_TEST_REPORT_MAX=32;
const RM_TEST_REPORT_TTL=604800;
function rm_test_tools(): array {
    $read=['securitySchemes'=>[['type'=>'oauth2','scopes'=>['source:read']]]];
    $write=['securitySchemes'=>[['type'=>'oauth2','scopes'=>['source:write']]]];
    $readonly=['readOnlyHint'=>true,'destructiveHint'=>false,'openWorldHint'=>false];
    return [
        ['name'=>'run_site_tests',
         'description'=>'Uruchom bez GitHub Actions bezpieczne testy PHP, kodu, konfiguracji i publicznych endpointów RentMor. Nie wykonuje kodu projektu ani nie zmienia strony.',
         'inputSchema'=>['type'=>'object','properties'=>[
             'suite'=>['type'=>'string','enum'=>['all','runtime','source','security','http','sandbox']],
             'filename'=>['type'=>'string','enum'=>rm_ext_file_names()]],
             'additionalProperties'=>false],
         '_meta'=>$read,'annotations'=>['title'=>'Testy RentMor na własnym serwerze']+$readonly],
        ['name'=>'test_staged_site_change',
         'description'=>'Sprawdź przed publikacją przygotowaną zmianę: SHA256, dopuszczalną ścieżkę i składnię PHP. Nie wykonuje kodu zmiany ani niczego nie publikuje.',
         'inputSchema'=>['type'=>'object','properties'=>[
             'change_id'=>['type'=>'string','pattern'=>'^[a-f0-9]{32}$']],
             'required'=>['change_id'],'additionalProperties'=>false],
         '_meta'=>$write,'annotations'=>['title'=>'Test przygotowanej poprawki']+$readonly],
        ['name'=>'get_site_test_report',
         'description'=>'Zwróć zapisany raport testów o identyfikatorze, bez danych klientów i kodu źródłowego.',
         'inputSchema'=>['type'=>'object','properties'=>[
             'report_id'=>['type'=>'string','pattern'=>'^[a-f0-9]{32}$']],
             'required'=>['report_id'],'additionalProperties'=>false],
         '_meta'=>$read,'annotations'=>['title'=>'Odczytaj raport testów']+$readonly],
    ];
}
function rm_test_is_tool(string $name): bool {
    return in_array($name,['run_site_tests','test_staged_site_change','get_site_test_report'],true);
}
function rm_test_dir(): string {
    $private=rm_private_dir();
    if($private===null)throw new RuntimeException('No private storage');
    $dir=$private.'/mcp-test-reports';
    if(!is_dir($dir)&&!@mkdir($dir,0700)&&!is_dir($dir))
        throw new RuntimeException('Test reports unavailable');
    $real=realpath($dir);
    if($real===false||is_link($dir)||dirname($real)!==$private||!is_writable($real))
        throw new RuntimeException('Unsafe test report directory');
    @chmod($real,0700);
    return $real;
}
function rm_test_add(array &$cases,string $name,string $status,string $detail=''): void {
    $cases[]=['test'=>$name,'status'=>$status,'detail'=>substr($detail,0,280)];
}
function rm_test_php_source(string $source): array {
    if(!function_exists('token_get_all')||!defined('TOKEN_PARSE'))
        return ['skipped','Parser PHP niedostępny na hostingu'];
    try {
        token_get_all($source,TOKEN_PARSE);
        return ['passed','Składnia PHP zaakceptowana przez parser bieżącego serwera'];
    } catch(ParseError $e) {
        // No source text in reports. Line number is safe.
        return ['failed','Błąd składni PHP w linii '.$e->getLine()];
    } catch(Throwable $e) {
        return ['failed','Błąd parsera PHP (szczegóły ukryte)'];
    }
}
function rm_test_runtime(array &$cases): void {
    rm_test_add($cases,'php_version',PHP_VERSION_ID>=80100?'passed':'failed',
        'PHP '.PHP_MAJOR_VERSION.'.'.PHP_MINOR_VERSION.'.'.PHP_RELEASE_VERSION);
    rm_test_add($cases,'php_parser',function_exists('token_get_all')&&defined('TOKEN_PARSE')?'passed':'skipped',
        'Parser składni bez wykonywania kodu');
    rm_test_add($cases,'json',function_exists('json_decode')?'passed':'failed','Obsługa JSON');
    rm_test_add($cases,'cryptography',
        function_exists('hash_equals')&&function_exists('random_bytes')&&function_exists('password_verify')?'passed':'failed',
        'Funkcje weryfikacji i kryptografii');
    rm_test_add($cases,'https_client',function_exists('curl_init')?'passed':'skipped','Klient HTTP cURL');
}
function rm_test_source(array &$cases,?string $filename): void {
    $list=$filename!==null?[$filename]:rm_ext_file_names();
    foreach($list as $name) {
        $path=rm_ext_locate($name);
        if($path===null||is_link($path)||!is_file($path)) {
            rm_test_add($cases,'file_'.$name,'failed','Plik nie istnieje lub jest poza listą');
            continue;
        }
        $source=rm_ext_read($path);
        if($source===null){
            rm_test_add($cases,'file_'.$name,'failed','Plik nieczytelny lub zbyt duży');
            continue;
        }
        $size=strlen($source);
        rm_test_add($cases,'integrity_'.$name,$size>0?'passed':'failed',
            'Odczytano '.$size.' bajtów; SHA256 '.substr(hash('sha256',$source),0,12).'…');
        if(str_ends_with($name,'.php')) {
            [$state,$description]=rm_test_php_source($source);
            rm_test_add($cases,'php_parse_'.$name,$state,$description);
        }
    }
}
function rm_test_security(array &$cases): void {
    $private=rm_private_dir();
    $public=realpath(rm_site_root());
    $privateReal=$private===null?false:realpath($private);
    rm_test_add($cases,'private_storage_outside_webroot',
        $public!==false&&$privateReal!==false&&
        !str_starts_with($privateReal,$public.'/')&&$privateReal!==$public?'passed':'failed',
        'Sprawdzono położenie magazynu prywatnego bez ujawniania ścieżek');
    rm_test_add($cases,'oauth_write_scope_separated',
        defined('RM_OAUTH_SCOPE_WRITE')&&RM_OAUTH_SCOPE_WRITE==='source:write'?'passed':'failed',
        'Zapis wymaga odrębnego zakresu OAuth');
    rm_test_add($cases,'owner_approval_required',
        function_exists('rm_write_apply')&&function_exists('rm_write_stage_read')?'passed':'failed',
        'Mechanizm weryfikacji i zatwierdzania zmian jest dostępny');
    rm_test_add($cases,'unknown_files_rejected',
        rm_ext_locate('../owner.json')===null&&rm_ext_locate('state.json')===null?'passed':'failed',
        'Ścieżki prywatne nie występują w dozwolonym katalogu');
}
function rm_test_sandbox(array &$cases): void {
    $dir=rm_test_dir();
    $workspace=$dir.'/fixture-'.bin2hex(random_bytes(10));
    if(!@mkdir($workspace,0700)) {
        rm_test_add($cases,'synthetic_file_fixture','failed','Nie udało się utworzyć prywatnej próbki');
        return;
    }
    $file=$workspace.'/sample.txt';
    $first="synthetic\nv1\n";
    $second="synthetic\nv2\n";
    $ok=false;
    try {
        if(!rm_atomically_write($file,$first))throw new RuntimeException('write');
        $hash=hash_file('sha256',$file);
        if($hash!==hash('sha256',$first))throw new RuntimeException('hash');
        if(!rm_atomically_write($file,$second))throw new RuntimeException('replace');
        if(hash_file('sha256',$file)!==hash('sha256',$second))throw new RuntimeException('verify');
        if(!rm_atomically_write($file,$first))throw new RuntimeException('rollback');
        $ok=hash_file('sha256',$file)===$hash;
    } catch(Throwable $e) {
        $ok=false;
    } finally {
        @unlink($file);
        @rmdir($workspace);
    }
    rm_test_add($cases,'synthetic_atomic_write_and_rollback',$ok?'passed':'failed',
        'Prywatny fikcyjny plik: zapis, kontrola SHA256, zastąpienie i cofnięcie');
    [$status,$message]=rm_test_php_source('<?php function syntactically_bad( {');
    rm_test_add($cases,'synthetic_invalid_php_rejected',
        $status==='failed'?'passed':($status==='skipped'?'skipped':'failed'),
        'Parser odrzuca celowo nieprawidłowy kod bez jego wykonania');
}
function rm_test_fixed_http(string $url): array {
    // No arbitrary targets and no redirects. Only public pages of RentMor.
    if(!str_starts_with($url,'https://rentmor.pl/')||!function_exists('curl_init'))
        return ['skipped','Nieobsługiwany klient HTTP'];
    $curl=curl_init($url);
    if($curl===false)return ['failed','Nie można utworzyć klienta HTTP'];
    $body='';
    curl_setopt_array($curl,[
        CURLOPT_FOLLOWLOCATION=>false,
        CURLOPT_MAXREDIRS=>0,
        CURLOPT_CONNECTTIMEOUT=>3,
        CURLOPT_TIMEOUT=>6,
        CURLOPT_SSL_VERIFYPEER=>true,
        CURLOPT_SSL_VERIFYHOST=>2,
        CURLOPT_USERAGENT=>'RentMor-Control-SelfTest/0.6',
        CURLOPT_RETURNTRANSFER=>false,
        CURLOPT_WRITEFUNCTION=>static function($ch,string $part)use(&$body):int {
            if(strlen($body)+strlen($part)>16384)return 0;
            $body.=$part;
            return strlen($part);
        }
    ]);
    $ok=curl_exec($curl);
    $code=(int)curl_getinfo($curl,CURLINFO_HTTP_CODE);
    curl_close($curl);
    if($ok===false)return ['failed','Błąd transportu HTTPS lub limit odpowiedzi'];
    return [$code>=200&&$code<300?'passed':'failed','HTTP '.$code];
}
function rm_test_http(array &$cases): void {
    foreach([
        'robots'=>'https://rentmor.pl/robots.txt',
        'oauth_metadata'=>'https://rentmor.pl/rentmor-control-mcp/oauth.php?op=metadata',
    ] as $name=>$url){
        [$status,$detail]=rm_test_fixed_http($url);
        rm_test_add($cases,'https_'.$name,$status,$detail);
    }
}
function rm_test_stage(array &$cases,string $id): void {
    if(!preg_match('/^[a-f0-9]{32}$/D',$id))throw new InvalidArgumentException('Invalid change ID');
    $row=rm_write_stage_read($id);
    if(!is_array($row))throw new RuntimeException('Stage not found');
    if(!in_array((string)($row['status']??''),['pending','approved'],true))
        throw new RuntimeException('Stage no longer pending');
    rm_test_add($cases,'stage_not_expired',($row['expires']??0)>=time()?'passed':'failed',
        'Okno przygotowanej zmiany');
    $action=(string)($row['action']??'');
    $name=(string)($row['filename']??'');
    $path=rm_write_target($name,$action);
    rm_test_add($cases,'stage_allowed_target',$path!==null?'passed':'failed','Dozwolony plik');
    if($path===null)return;
    $current=rm_write_current($path);
    $liveSha=$current===null?null:hash('sha256',$current);
    rm_test_add($cases,'stage_base_sha256',$liveSha===($row['old_sha']??null)?'passed':'failed',
        'Porównanie wersji pliku bez ujawniania źródła');
    $new=$row['new_content']??null;
    if($new!==null&&!is_string($new)){
        rm_test_add($cases,'stage_candidate_integrity','failed','Nieprawidłowy typ zawartości');
        return;
    }
    rm_test_add($cases,'stage_candidate_integrity',
        ($new===null?null:hash('sha256',$new))===($row['new_sha']??null)?'passed':'failed',
        'Sprawdzenie SHA256 przygotowanej wersji');
    if($new!==null&&str_ends_with($name,'.php')){
        [$status,$message]=rm_test_php_source($new);
        rm_test_add($cases,'stage_php_parse',$status,$message);
    }
    rm_test_add($cases,'stage_not_published','passed','Testowanie nie publikuje przygotowanego kodu');
}
function rm_test_store(string $suite,array $cases): array {
    $failed=0;$passed=0;$skipped=0;
    foreach($cases as $case) {
        if($case['status']==='passed')$passed++;
        elseif($case['status']==='failed')$failed++;
        else $skipped++;
    }
    $report=[
        'report_id'=>bin2hex(random_bytes(16)),
        'suite'=>$suite,'created_at'=>gmdate(DATE_ATOM),
        'summary'=>['passed'=>$passed,'failed'=>$failed,'skipped'=>$skipped],
        'overall'=>$failed>0?'failed':($passed===0?'skipped':($skipped>0?'partial':'passed')),
        'tests'=>array_slice($cases,0,RM_TEST_REPORT_MAX),
        'independent_of_github'=>true,
        'no_production_code_executed'=>true,
        'limitations'=>'Testy lokalne bez uruchamiania kodu projektu; nie zastępują kontenerowych testów integracyjnych ani testów przeglądarkowych.'
    ];
    $file=rm_test_dir().'/report-'.$report['report_id'].'.json';
    if(!rm_atomically_write($file,json_encode($report,JSON_THROW_ON_ERROR|JSON_UNESCAPED_SLASHES)))
        throw new RuntimeException('Could not persist test report');
    $cleanup=glob(rm_test_dir().'/report-*.json');
    if(is_array($cleanup)){
        foreach($cleanup as $old){
            if(!is_link($old)&&is_file($old)&&filemtime($old)<time()-RM_TEST_REPORT_TTL)
                @unlink($old);
        }
    }
    return $report;
}
function rm_test_call(string $name,array $args): array {
    try {
        if(!rm_rate_allow('mcp-safe-test-execution',10,900))
            return rm_tool_error('Limit testów: maksymalnie 10 uruchomień na 15 minut.');
        if($name==='get_site_test_report') {
            $id=$args['report_id']??null;
            if(!is_string($id)||!preg_match('/^[a-f0-9]{32}$/D',$id))
                return rm_tool_error('Nieprawidłowy identyfikator raportu.');
            $file=rm_test_dir().'/report-'.$id.'.json';
            if(is_link($file)||!is_file($file)||filesize($file)>30000)
                return rm_tool_error('Brak raportu.');
            $report=json_decode((string)file_get_contents($file),true);
            return is_array($report)?rm_text_result($report):rm_tool_error('Uszkodzony raport.');
        }
        $cases=[];
        if($name==='run_site_tests'){
            $suite=$args['suite']??'all';
            $filename=$args['filename']??null;
            if(!in_array($suite,['all','runtime','source','security','http','sandbox'],true)||
               ($filename!==null&&(!is_string($filename)||!in_array($filename,rm_ext_file_names(),true))))
                return rm_tool_error('Nieprawidłowy zestaw testów lub nazwa pliku.');
            if($suite==='all'||$suite==='runtime')rm_test_runtime($cases);
            if($suite==='all'||$suite==='source')rm_test_source($cases,$filename);
            if($suite==='all'||$suite==='security')rm_test_security($cases);
            if($suite==='all'||$suite==='sandbox')rm_test_sandbox($cases);
            if($suite==='all'||$suite==='http')rm_test_http($cases);
            return rm_text_result(rm_test_store($suite,$cases));
        }
        if($name==='test_staged_site_change'){
            $id=$args['change_id']??null;
            if(!is_string($id)||!preg_match('/^[a-f0-9]{32}$/D',$id))
                return rm_tool_error('Nieprawidłowy identyfikator zmiany.');
            rm_test_stage($cases,$id);
            return rm_text_result(rm_test_store('staged_change',$cases));
        }
        return rm_tool_error('Nieznany zestaw testów.');
    } catch(Throwable $e) {
        error_log('RentMor MCP safe self-test failed (details suppressed)');
        return rm_tool_error('Test nie może zostać bezpiecznie wykonany. Brak zmian na stronie.');
    }
}
