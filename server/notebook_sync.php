<?php
declare(strict_types=1);

/**
 * Private, admin-only multi-device notebook. Never place the file in public_html.
 * Server revisions and idempotent operation IDs prevent silent replay/overwrite.
 */
function rm_notebook_initial(): array {
    return ['revision'=>0,'notes'=>[],'processed'=>[]];
}
function rm_notebook_read(string $path): array {
    if (!file_exists($path)) return rm_notebook_initial();
    if (!is_file($path) || !is_readable($path)) throw new RuntimeException('Notebook unreadable');
    $raw=file_get_contents($path);
    if (!is_string($raw) || $raw==='') throw new RuntimeException('Notebook empty');
    $data=json_decode($raw,true);
    if (!is_array($data) || !isset($data['revision'],$data['notes'],$data['processed']) ||
        !is_int($data['revision']) || !is_array($data['notes']) || !is_array($data['processed'])) {
        throw new RuntimeException('Notebook invalid');
    }
    return $data;
}
function rm_notebook_id($id): bool {
    return is_string($id) && preg_match('/^[a-f0-9-]{36}$/D',$id)===1;
}
function rm_notebook_apply(array &$data,array $operations): array {
    if (count($operations)>100) throw new InvalidArgumentException('Too many notebook updates');
    $results=[];
    foreach ($operations as $op) {
        if (!is_array($op)) throw new InvalidArgumentException('Invalid note update');
        $id=$op['id']??null;$opId=$op['op_id']??null;$base=$op['base_revision']??null;
        if (!rm_notebook_id($id) || !rm_notebook_id($opId) || !is_int($base) || $base<0)
            throw new InvalidArgumentException('Invalid note identifier');
        if (array_key_exists($opId,$data['processed'])) {
            // Never replay an operation after another device has changed its note.
            $results[]=['op_id'=>$opId,'ok'=>true,'revision'=>$data['processed'][$opId]];
            continue;
        }
        $old=$data['notes'][$id]??null;
        $current=is_array($old)?(int)($old['revision']??0):0;
        if ($current!==$base) {
            $results[]=['op_id'=>$opId,'ok'=>false,'conflict'=>true,'revision'=>$current];
            continue;
        }
        $deleted=$op['deleted']??null;$done=$op['done']??null;$text=$op['text']??null;$category=$op['category']??null;
        if (!is_bool($deleted) || !is_bool($done) || !is_string($text) ||
            trim($text)==='' || strlen($text)>8000 ||
            (function_exists('mb_strlen')?mb_strlen($text,'UTF-8'):strlen($text))>2000 ||
            !in_array($category,['Zakupy','Umowy','Inne'],true)) {
            throw new InvalidArgumentException('Invalid notebook text or category');
        }
        $revision=++$data['revision'];
        $data['notes'][$id]=['id'=>$id,'text'=>$text,'category'=>$category,'done'=>$done,
                            'deleted'=>$deleted,'revision'=>$revision];
        $data['processed'][$opId]=$revision;
        $results[]=['op_id'=>$opId,'ok'=>true,'revision'=>$revision];
    }
    // Keep a finite replay ledger; old devices with very stale changes get conflicts.
    if (count($data['processed'])>10000) $data['processed']=array_slice($data['processed'],-8000,null,true);
    return $results;
}
function rm_notebook_write(string $path,array $data): void {
    $encoded=json_encode($data,JSON_PRETTY_PRINT|JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR);
    $temp=$path.'.'.bin2hex(random_bytes(8)).'.tmp';
    if (file_put_contents($temp,$encoded,LOCK_EX)!==strlen($encoded)) { @unlink($temp);throw new RuntimeException('Notebook write failed'); }
    @chmod($temp,0600);
    if (!@rename($temp,$path)) { @unlink($temp);throw new RuntimeException('Notebook replacement failed'); }
}
function rm_notebook_handle(string $role,string $method): never {
    if ($role!=='admin') rm_json_response(403,['ok'=>false,'error'=>'Notatnik dostępny tylko dla administratora.']);
    if (!in_array($method,['GET','POST'],true)) rm_json_response(405,['ok'=>false,'error'=>'Niedozwolona metoda.']);
    if ($method==='POST' && ($_SERVER['HTTP_X_RENTMOR_SYNC']??'')!=='1')
        rm_json_response(403,['ok'=>false,'error'=>'Niedozwolony zapis.']);
    $directory=dirname(__DIR__).'/rentmor-private';
    if ((!is_dir($directory)&&!@mkdir($directory,0700,true))||!is_dir($directory))
        rm_json_response(503,['ok'=>false,'error'=>'Prywatny magazyn notatnika jest niedostępny.']);
    $path=$directory.'/notebook.json';
    $lock=@fopen($path.'.lock','c+');
    if (!$lock || !@flock($lock,$method==='POST'?LOCK_EX:LOCK_SH))
        rm_json_response(503,['ok'=>false,'error'=>'Notatnik jest chwilowo zajęty.']);
    try {
        $data=rm_notebook_read($path);
        if ($method==='POST') {
            $raw=file_get_contents('php://input');
            if (!is_string($raw)||strlen($raw)>240000) throw new InvalidArgumentException('Notebook request too large');
            $input=json_decode($raw,true,512,JSON_THROW_ON_ERROR);
            if (!is_array($input)||!isset($input['operations'])||!is_array($input['operations']))
                throw new InvalidArgumentException('Missing operations');
            $before=$data;
            $results=rm_notebook_apply($data,$input['operations']);
            if ($data!==$before) rm_notebook_write($path,$data);
            $response=['ok'=>true,'role'=>'admin','results'=>$results];
        } else {
            $response=['ok'=>true,'role'=>'admin','notes'=>array_values($data['notes'])];
        }
        flock($lock,LOCK_UN);fclose($lock);
        rm_json_response(200,$response);
    } catch (InvalidArgumentException|JsonException $e) {
        flock($lock,LOCK_UN);fclose($lock);
        rm_json_response(400,['ok'=>false,'error'=>'Nieprawidłowa aktualizacja notatnika.']);
    } catch (Throwable $e) {
        flock($lock,LOCK_UN);fclose($lock);
        rm_json_response(503,['ok'=>false,'error'=>'Dane notatnika są niedostępne. Zapis wstrzymany.']);
    }
}
