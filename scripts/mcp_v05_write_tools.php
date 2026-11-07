<?php
declare(strict_types=1);
/**
 * RentMor Control 0.5: owner-approved, revision-pinned public-source edits.
 * No admin files, data.json, user records, OAuth files, or arbitrary shell.
 */
const RM_WRITE_TTL=900;
const RM_WRITE_MAX_BYTES=131072;
function rm_write_tools(): array {
    $auth=['securitySchemes'=>[['type'=>'oauth2','scopes'=>['source:write']]]];
    $nonread=['readOnlyHint'=>false,'destructiveHint'=>false,'openWorldHint'=>false];
    $files=rm_write_file_names();
    return [
      ['name'=>'stage_site_change','description'=>'Przygotuj zmianę pliku na RentMor.pl (bez publikacji). Wymaga scope source:write; wymagana osobna akceptacja hasłem właściciela.',
       'inputSchema'=>['type'=>'object','properties'=>[
          'action'=>['type'=>'string','enum'=>['replace','create','delete']],
          'filename'=>['type'=>'string','maxLength'=>120],
          'expected_sha256'=>['type'=>'string','pattern'=>'^[a-f0-9]{64}$'],
          'old_text'=>['type'=>'string','maxLength'=>4096],
          'new_text'=>['type'=>'string','maxLength'=>16000]],
         'required'=>['action','filename'],'additionalProperties'=>false],
       '_meta'=>$auth,'annotations'=>['title'=>'Przygotuj zmianę z akceptacją właściciela']+$nonread],
      ['name'=>'apply_approved_site_change','description'=>'Opublikuj wyłącznie wcześniej przygotowaną i osobno zatwierdzoną hasłem zmianę. Kontrola SHA, kopia zapasowa, log.',
       'inputSchema'=>['type'=>'object','properties'=>[
          'change_id'=>['type'=>'string','pattern'=>'^[a-f0-9]{32}$']],
         'required'=>['change_id'],'additionalProperties'=>false],
       '_meta'=>$auth,'annotations'=>['title'=>'Zastosuj zatwierdzoną zmianę','readOnlyHint'=>false,'destructiveHint'=>true,'openWorldHint'=>false]],
      ['name'=>'list_site_changes','description'=>'Pokaż historię zmian oraz stan przygotowanych poprawek (bez treści plików i sekretów).',
       'inputSchema'=>['type'=>'object','properties'=>(object)[],'additionalProperties'=>false],
       '_meta'=>$auth,'annotations'=>['title'=>'Historia i kopie zmian','readOnlyHint'=>true,'destructiveHint'=>false,'openWorldHint'=>false]],
      ['name'=>'stage_site_rollback','description'=>'Przygotuj przywrócenie poprzedniej wersji z kopii zapasowej. Również wymaga akceptacji właściciela.',
       'inputSchema'=>['type'=>'object','properties'=>[
          'change_id'=>['type'=>'string','pattern'=>'^[a-f0-9]{32}$']],
         'required'=>['change_id'],'additionalProperties'=>false],
       '_meta'=>$auth,'annotations'=>['title'=>'Przygotuj przywrócenie kopii','readOnlyHint'=>false,'destructiveHint'=>false,'openWorldHint'=>false]],
    ];
}
function rm_write_is_tool(string $name): bool {
    return in_array($name,['stage_site_change','apply_approved_site_change','list_site_changes','stage_site_rollback'],true);
}
function rm_write_file_names(): array {
    return array_values(array_unique(array_merge(rm_public_files(),rm_source_files())));
}
function rm_write_name_ok(string $name,string $action='replace'): bool {
    if(!preg_match('/^[a-z0-9][a-z0-9_-]{0,100}\.(php|css|js|txt)$/D',$name))return false;
    if(in_array($name,rm_write_file_names(),true))return $action==='replace';
    // New and subsequently managed files use an isolated naming prefix.
    if(preg_match('/^rentmor-ai-[a-z0-9_-]{1,64}\.(css|js|txt)$/D',$name))return true;
    if(preg_match('/^poradnik-rentmor-[a-z0-9_-]{1,64}\.php$/D',$name))return true;
    return false;
}
function rm_write_root(): ?string {
    $root=rm_site_root();
    return $root!==''&&is_dir($root)&&!is_link($root)?$root:null;
}
function rm_write_target(string $name,string $action): ?string {
    if(!rm_write_name_ok($name,$action))return null;
    $root=rm_write_root();if($root===null)return null;
    $path=$root.'/'.$name;
    if(is_link($path)||is_dir($path))return null;
    if(is_file($path)){
        $real=realpath($path);
        if($real===false||dirname($real)!==$root||!is_readable($real))return null;
        $size=filesize($real);
        if($size===false||$size>RM_WRITE_MAX_BYTES)return null;
    }
    return $path;
}
function rm_write_dir(): string {
    $private=rm_private_dir();
    if($private===null)throw new RuntimeException('Private storage unavailable');
    $dir=$private.'/mcp-source-changes';
    if(!file_exists($dir) && !@mkdir($dir,0700))throw new RuntimeException('Change storage unavailable');
    if(is_link($dir)||!is_dir($dir)||!is_writable($dir)||realpath(dirname($dir))!==$private)
        throw new RuntimeException('Invalid change directory');
    @chmod($dir,0700);
    return $dir;
}
function rm_write_stage_path(string $id): ?string {
    return preg_match('/^[a-f0-9]{32}$/D',$id) ? rm_write_dir().'/stage-'.$id.'.json' : null;
}
function rm_write_stage_read(string $id): ?array {
    $path=rm_write_stage_path($id);
    if($path===null||is_link($path)||!is_file($path)||filesize($path)>1000000)return null;
    $data=json_decode((string)@file_get_contents($path),true);
    return is_array($data)&&($data['id']??null)===$id?$data:null;
}
function rm_write_stage_save(array $data): void {
    $path=rm_write_stage_path((string)($data['id']??''));
    if($path===null||!rm_atomically_write($path,json_encode($data,JSON_THROW_ON_ERROR|JSON_UNESCAPED_SLASHES)))
        throw new RuntimeException('Cannot stage the change');
}
function rm_write_lock() {
    $lock=fopen(rm_write_dir().'/changes.lock','c+');
    if(!$lock||!flock($lock,LOCK_EX))throw new RuntimeException('Change lock unavailable');
    return $lock;
}
function rm_write_unlock($lock): void {flock($lock,LOCK_UN);fclose($lock);}
function rm_write_current(string $path): ?string {
    if(!is_file($path))return null;
    $source=@file_get_contents($path,false,null,0,RM_WRITE_MAX_BYTES+1);
    if(!is_string($source)||strlen($source)>RM_WRITE_MAX_BYTES)throw new RuntimeException('File too large or unavailable');
    return $source;
}
function rm_write_php_lint(string $source): bool {
    if(!function_exists('proc_open'))return false;
    $dir=rm_write_dir();
    $tmp=$dir.'/lint-'.bin2hex(random_bytes(12)).'.php';
    if(file_put_contents($tmp,$source,LOCK_EX)!==strlen($source))return false;
    @chmod($tmp,0600);
    try {
        // No shell; known local PHP CLI executable; never execute the submitted code.
        $binary=is_executable('/usr/bin/php')?'/usr/bin/php':null;
        if($binary===null)return false;
        $pipes=[];
        $proc=@proc_open([$binary,'-l',$tmp],[[ 'pipe','r'],['pipe','w'],['pipe','w']],$pipes,$dir);
        if(!is_resource($proc))return false;
        fclose($pipes[0]);
        stream_get_contents($pipes[1]);fclose($pipes[1]);
        stream_get_contents($pipes[2]);fclose($pipes[2]);
        return proc_close($proc)===0;
    } finally {@unlink($tmp);}
}
function rm_write_public_atomic(string $path,string $source): bool {
    $dir=dirname($path);
    $tmp=$dir.'/.rm-change-'.bin2hex(random_bytes(10));
    if(@file_put_contents($tmp,$source,LOCK_EX)!==strlen($source)){@unlink($tmp);return false;}
    @chmod($tmp,0644);
    if(!@rename($tmp,$path)){@unlink($tmp);return false;}
    @chmod($path,0644);
    return hash_file('sha256',$path)===hash('sha256',$source);
}
function rm_write_prepare(string $action,string $filename,?string $expected,?string $old,?string $new): array {
    $path=rm_write_target($filename,$action);
    if($path===null)return rm_tool_error('Plik lub działanie poza dozwoloną listą.');
    $current=rm_write_current($path);
    if($action==='create'){
        if($current!==null||!is_string($new)||strlen($new)>16000||strlen($new)===0)
            return rm_tool_error('Nowy plik musi nie istnieć i mieć od 1 do 16000 bajtów.');
        $after=$new;
    } elseif($action==='delete'){
        if($current===null||!is_string($expected)||!hash_equals(hash('sha256',$current),$expected))
            return rm_tool_error('Plik nie istnieje lub suma SHA256 nie odpowiada.');
        if(!preg_match('/^(rentmor-ai-|poradnik-rentmor-)/D',$filename))
            return rm_tool_error('Usuwanie istniejących kluczowych plików jest możliwe przez GitHub, nie MCP.');
        $after=null;
    } else {
        if($current===null||!is_string($expected)||!hash_equals(hash('sha256',$current),$expected))
            return rm_tool_error('Plik zmieniono od czasu odczytu; pobierz nowy SHA256.');
        if(!is_string($old)||$old===''||strlen($old)>4096||!is_string($new)||strlen($new)>16000||
           substr_count($current,$old)!==1||$old===$new)
            return rm_tool_error('Zmiana musi dotyczyć jednego, jednoznacznego fragmentu.');
        $after=str_replace($old,$new,$current);
    }
    if($after!==null&&strlen($after)>RM_WRITE_MAX_BYTES)return rm_tool_error('Wynik przekracza dozwolony rozmiar.');
    // PHP must pass syntax validation on THIS server before the owner can approve it.
    if($after!==null&&str_ends_with($filename,'.php')&&!rm_write_php_lint($after))
        return rm_tool_error('Nie można bezpiecznie potwierdzić składni PHP. Wdróż przez GitHub Actions.');
    $owner=rm_owner();if(!$owner)return rm_tool_error('Brak konta właściciela.');
    $id=bin2hex(random_bytes(16));
    $entry=[
       'id'=>$id,'action'=>$action,'filename'=>$filename,
       'old_sha'=>$current===null?null:hash('sha256',$current),
       'new_sha'=>$after===null?null:hash('sha256',$after),
       'new_content'=>$after,
       'preview_before'=>$action==='replace'?$old:($action==='delete'?substr((string)$current,0,16000):''),
       'preview_after'=>$action==='replace'?$new:($action==='create'?$new:''),
       'status'=>'pending','created'=>time(),'expires'=>time()+RM_WRITE_TTL,
       'approved_until'=>0,'owner_fp'=>hash('sha256',$owner['password_hash'])
    ];
    rm_write_stage_save($entry);
    return rm_text_result([
       'change_id'=>$id,'filename'=>$filename,'action'=>$action,
       'current_sha256'=>$entry['old_sha'],'new_sha256'=>$entry['new_sha'],
       'approved'=>false,'published'=>false,'expires_in_seconds'=>RM_WRITE_TTL,
       'approval_url'=>rm_base_url().'/approve-change.php?id='.$id,
       'instruction'=>'Właściciel musi otworzyć ten adres i zatwierdzić zmianę swoim hasłem. Dopiero potem wywołaj apply_approved_site_change.'
    ]);
}
function rm_write_history(): array {
    $file=rm_write_dir().'/history.jsonl';
    $lines=is_file($file)?@file($file,FILE_IGNORE_NEW_LINES|FILE_SKIP_EMPTY_LINES):[];
    $rows=[];
    if(is_array($lines)){
        foreach(array_slice($lines,-20) as $line){
            $record=json_decode($line,true);
            if(is_array($record))$rows[]=$record;
        }
    }
    return rm_text_result(['changes'=>$rows,'no_credentials_in_history'=>true]);
}
function rm_write_apply(string $id): array {
    $lock=rm_write_lock();
    try {
       $row=rm_write_stage_read($id);
       $owner=rm_owner();
       if(!$row||!$owner||!hash_equals((string)($row['owner_fp']??''),hash('sha256',$owner['password_hash']))||
          ($row['status']??'')!=='approved'||($row['approved_until']??0)<time())
          return rm_tool_error('Zmiana nie jest zatwierdzona lub zatwierdzenie wygasło.');
       $action=(string)$row['action'];$name=(string)$row['filename'];
       $path=rm_write_target($name,$action);
       if($path===null)return rm_tool_error('Docelowy plik poza dozwoloną listą.');
       $current=rm_write_current($path);
       $sha=$current===null?null:hash('sha256',$current);
       if($sha!==($row['old_sha']??null))return rm_tool_error('Plik zmienił się po zatwierdzeniu (SHA). Utwórz nową poprawkę.');
       $new=$row['new_content']??null;
       if($new!==null&&(!is_string($new)||hash('sha256',$new)!==($row['new_sha']??null)))
           return rm_tool_error('Błędna integralność przygotowanej zmiany.');
       if($new!==null&&str_ends_with($name,'.php')&&!rm_write_php_lint($new))
           return rm_tool_error('Błąd kontroli składni PHP; niczego nie opublikowano.');
       $dir=rm_write_dir();
       if($current!==null){
          $backup=$dir.'/backup-'.$id.'.bak';
          if(!rm_atomically_write($backup,$current) || hash_file('sha256',$backup)!==$sha)
              return rm_tool_error('Nie udało się potwierdzić kopii zapasowej — brak publikacji.');
       }
       if($action==='delete') {
          if(!@unlink($path))return rm_tool_error('Nie można usunąć pliku.');
       } elseif(!rm_write_public_atomic($path,(string)$new)){
          if($current!==null)rm_write_public_atomic($path,$current);
          else @unlink($path);
          return rm_tool_error('Publikacja nie powiodła się; podjęto próbę przywrócenia poprzedniej wersji.');
       }
       $row['status']='applied';$row['applied_at']=time();$row['new_content']=null;
       rm_write_stage_save($row);
       $record=['change_id'=>$id,'filename'=>$name,'action'=>$action,
           'previous_sha256'=>$sha,'new_sha256'=>$row['new_sha'],'time'=>gmdate(DATE_ATOM)];
       @file_put_contents($dir.'/history.jsonl',json_encode($record,JSON_THROW_ON_ERROR|JSON_UNESCAPED_SLASHES)."\n",FILE_APPEND|LOCK_EX);
       @chmod($dir.'/history.jsonl',0600);
       return rm_text_result(['applied'=>true,'change'=>$record,'backup_available'=>$current!==null]);
    } catch(Throwable $e){
        error_log('RentMor MCP site apply failed (details redacted)');
        return rm_tool_error('Błąd operacji na pliku. Sprawdź stan pliku przed kolejną próbą.');
    } finally {rm_write_unlock($lock);}
}
function rm_write_call(string $name,array $args): array {
    if(!rm_rate_allow('mcp-source-write',50,900))return rm_tool_error('Przekroczono limit operacji. Spróbuj później.');
    if($name==='list_site_changes')return rm_write_history();
    if($name==='apply_approved_site_change'){
       $id=$args['change_id']??null;
       return is_string($id)&&preg_match('/^[a-f0-9]{32}$/D',$id)?
           rm_write_apply($id):rm_tool_error('Nieprawidłowy identyfikator zmiany.');
    }
    if($name==='stage_site_change'){
       $action=$args['action']??null;$filename=$args['filename']??null;
       if(!is_string($action)||!in_array($action,['replace','create','delete'],true)||
          !is_string($filename)||strlen($filename)>120)
           return rm_tool_error('Nieprawidłowa operacja lub nazwa pliku.');
       foreach(['expected_sha256','old_text','new_text'] as $key)
          if(isset($args[$key])&&!is_string($args[$key]))return rm_tool_error('Nieprawidłowe dane zmiany.');
       return rm_write_prepare($action,$filename,$args['expected_sha256']??null,$args['old_text']??null,$args['new_text']??null);
    }
    if($name==='stage_site_rollback'){
       $id=$args['change_id']??null;
       if(!is_string($id)||!preg_match('/^[a-f0-9]{32}$/D',$id))return rm_tool_error('Nieprawidłowy identyfikator.');
       $row=rm_write_stage_read($id);
       if(!$row||($row['status']??null)!=='applied')return rm_tool_error('Brak opublikowanej zmiany.');
       $name=(string)$row['filename'];
       $path=rm_write_target($name,'replace');
       if($path===null)return rm_tool_error('Nieobsługiwany cel przywracania.');
       $now=rm_write_current($path);
       $nowSha=$now===null?null:hash('sha256',$now);
       if($nowSha!==($row['new_sha']??null))return rm_tool_error('Plik ma nowszą wersję; nie można bezpiecznie przywrócić.');
       if($row['old_sha']===null) {
           if($now===null)return rm_tool_error('Plik już nie istnieje.');
           return rm_write_prepare('delete',$name,$nowSha,null,null);
       }
       $backup=rm_write_dir().'/backup-'.$id.'.bak';
       if(!is_file($backup)||is_link($backup)||hash_file('sha256',$backup)!==$row['old_sha'])
           return rm_tool_error('Zweryfikowana kopia nie jest dostępna.');
       $restore=(string)@file_get_contents($backup);
       if($now===null){
           return rm_write_prepare('create',$name,null,null,$restore);
       }
       // Old file may be > 16 KiB; stage a complete replacement internally.
       $owner=rm_owner();if(!$owner)return rm_tool_error('Brak konta właściciela.');
       if(str_ends_with($name,'.php')&&!rm_write_php_lint($restore))
           return rm_tool_error('Nieudana kontrola składni kopii PHP.');
       $newId=bin2hex(random_bytes(16));
       $stage=['id'=>$newId,'action'=>'replace','filename'=>$name,
           'old_sha'=>$nowSha,'new_sha'=>hash('sha256',$restore),
           'new_content'=>$restore,
           'preview_before'=>substr((string)$now,0,16000),
           'preview_after'=>substr($restore,0,16000),
           'status'=>'pending','created'=>time(),
           'expires'=>time()+RM_WRITE_TTL,'approved_until'=>0,
           'owner_fp'=>hash('sha256',$owner['password_hash'])];
       rm_write_stage_save($stage);
       return rm_text_result(['change_id'=>$newId,'rollback_of'=>$id,'published'=>false,
          'approval_url'=>rm_base_url().'/approve-change.php?id='.$newId,
          'instruction'=>'Najpierw zatwierdź przywrócenie hasłem właściciela.']);
    }
    return rm_tool_error('Nieznana operacja.');
}
