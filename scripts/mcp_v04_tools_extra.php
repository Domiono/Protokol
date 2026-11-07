<?php
declare(strict_types=1);
/** RentMor Control 0.4: OAuth-protected code diagnostics and PREVIEW ONLY. No writes. */
function rm_ext_file_names(): array {
    return array_values(array_unique(array_merge(rm_source_files(),rm_public_files())));
}
function rm_ext_locate(string $file): ?string {
    if(!in_array($file,rm_ext_file_names(),true))return null;
    return in_array($file,rm_source_files(),true)?rm_resolve_source($file):rm_resolve_public_file($file);
}
function rm_ext_tools(): array {
    $secure=['securitySchemes'=>[['type'=>'oauth2','scopes'=>[RM_OAUTH_SCOPE]]]];
    $readonly=['readOnlyHint'=>true,'destructiveHint'=>false,'openWorldHint'=>false];
    $names=rm_ext_file_names();
    return [
      ['name'=>'inspect_site_files',
       'description'=>'Wymaga OAuth. Zwraca hash SHA256, rozmiar i dostępność bezpiecznie dozwolonych plików PHP/CSS/JS. Nie czyta danych prywatnych ani nie zapisuje.',
       'inputSchema'=>['type'=>'object','properties'=>(object)[],'additionalProperties'=>false],
       '_meta'=>$secure,'annotations'=>['title'=>'Stan plików i sumy kontrolne']+$readonly],
      ['name'=>'search_site_source',
       'description'=>'Wymaga OAuth. Szuka dosłownego fragmentu w kodzie ze ścisłej listy dozwolonych plików. Bez regex i bez odczytu prywatnych plików.',
       'inputSchema'=>['type'=>'object','properties'=>[
           'query'=>['type'=>'string','minLength'=>2,'maxLength'=>120],
           'filename'=>['type'=>'string','enum'=>$names],
           'max_results'=>['type'=>'integer','minimum'=>1,'maximum'=>25]],
           'required'=>['query'],'additionalProperties'=>false],
       '_meta'=>$secure,'annotations'=>['title'=>'Znajdź fragment kodu']+$readonly],
      ['name'=>'preview_site_patch',
       'description'=>'Wymaga OAuth. Sprawdza dokładnie jedną podmianę fragmentu tekstu i SHA256. TYLKO PODGLĄD: niczego nie zmienia ani nie publikuje.',
       'inputSchema'=>['type'=>'object','properties'=>[
           'filename'=>['type'=>'string','enum'=>$names],
           'expected_sha256'=>['type'=>'string','pattern'=>'^[a-f0-9]{64}$'],
           'old_text'=>['type'=>'string','minLength'=>1,'maxLength'=>2048],
           'new_text'=>['type'=>'string','maxLength'=>4096]],
           'required'=>['filename','expected_sha256','old_text','new_text'],'additionalProperties'=>false],
       '_meta'=>$secure,'annotations'=>['title'=>'Podgląd zmiany bez zapisu']+$readonly],
    ];
}
function rm_ext_is_tool(string $name): bool {
    return in_array($name,['inspect_site_files','search_site_source','preview_site_patch'],true);
}
function rm_ext_read(string $path): ?string {
    $size=@filesize($path);
    if($size===false||$size>RM_MCP_MAX_FILE_BYTES||$size<0)return null;
    $contents=@file_get_contents($path);
    return is_string($contents)&&strlen($contents)<=RM_MCP_MAX_FILE_BYTES?$contents:null;
}
function rm_ext_call_tool(string $name,array $args): array {
    if($name==='inspect_site_files') {
        $files=[];
        foreach(rm_ext_file_names() as $file) {
            $path=rm_ext_locate($file);
            $hash=$path===null?false:@hash_file('sha256',$path);
            $files[]=[
                'filename'=>$file,
                'available'=>$path!==null,
                'bytes'=>$path===null?null:filesize($path),
                'sha256'=>is_string($hash)?$hash:null,
                'php_writable'=>$path!==null&&is_writable($path)
            ];
        }
        return rm_text_result(['files'=>$files,'authenticated'=>true,'changes_allowed'=>false]);
    }
    if($name==='search_site_source') {
        $needle=$args['query']??null;
        $file=$args['filename']??null;
        $limit=$args['max_results']??15;
        if(!is_string($needle)||strlen($needle)<2||strlen($needle)>120||str_contains($needle,"\0")||
           !is_int($limit)||$limit<1||$limit>25||
           ($file!==null&&(!is_string($file)||!in_array($file,rm_ext_file_names(),true))))
           return rm_tool_error('Nieprawidłowy fragment, plik lub limit.');
        $found=[];
        foreach($file!==null?[$file]:rm_ext_file_names() as $item) {
            $path=rm_ext_locate($item);if($path===null)continue;
            $source=rm_ext_read($path);if($source===null)continue;
            foreach(explode("\n",$source) as $i=>$line) {
                $pos=strpos($line,$needle);if($pos===false)continue;
                $excerpt=trim($line);
                if(strlen($excerpt)>240)$excerpt=substr($excerpt,max(0,$pos-60),220);
                $found[]=['filename'=>$item,'line'=>$i+1,'excerpt'=>$excerpt];
                if(count($found)>=$limit)break 2;
            }
        }
        return rm_text_result(['results'=>$found,'count'=>count($found),'truncated'=>count($found)>=$limit,'authenticated'=>true]);
    }
    if($name==='preview_site_patch') {
        $file=$args['filename']??null;$sha=$args['expected_sha256']??null;
        $old=$args['old_text']??null;$new=$args['new_text']??null;
        if(!is_string($file)||!is_string($sha)||!preg_match('/^[a-f0-9]{64}$/D',$sha)||
           !is_string($old)||strlen($old)<1||strlen($old)>2048||
           !is_string($new)||strlen($new)>4096||str_contains($old,"\0")||str_contains($new,"\0"))
            return rm_tool_error('Nieprawidłowe parametry podglądu.');
        $path=rm_ext_locate($file);if($path===null)return rm_tool_error('Plik poza dozwoloną listą.');
        $source=rm_ext_read($path);if($source===null)return rm_tool_error('Plik za duży lub nieczytelny.');
        $liveSha=hash('sha256',$source);
        if(!hash_equals($liveSha,$sha))return rm_tool_error('Niezgodny SHA256 — plik został zmieniony.');
        if($old===$new||substr_count($source,$old)!==1)
            return rm_tool_error('Tekst musi się różnić i występować dokładnie jeden raz.');
        $after=str_replace($old,$new,$source);
        if(strlen($after)>RM_MCP_MAX_FILE_BYTES)return rm_tool_error('Wynik jest za duży.');
        $offset=strpos($source,$old);
        return rm_text_result([
            'filename'=>$file,'original_sha256'=>$liveSha,'preview_sha256'=>hash('sha256',$after),
            'changed_at_line'=>1+substr_count(substr($source,0,$offset),"\n"),
            'replaced_bytes'=>strlen($old),'inserted_bytes'=>strlen($new),
            'before_preview'=>substr($old,0,512),'after_preview'=>substr($new,0,512),
            'applied'=>false,'reason'=>'Tylko podgląd. Publikacja wymaga osobnej zgody.'
        ]);
    }
    return rm_tool_error('Nieznane narzędzie rozszerzenia.');
}
