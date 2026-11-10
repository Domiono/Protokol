<?php
declare(strict_types=1);
/** Dedicated directories; user supplied names never become filesystem paths. */
function rm_ai_equipment_dir(string $base,string $label,bool $create=false): string {
    if($label===''||strlen($label)>300||preg_match('//u',$label)!==1)throw new InvalidArgumentException('Nieprawidłowy sprzęt.');
    $root=$base.'/equipment-knowledge';$path=$root.'/'.hash('sha256',$label);
    foreach([$root,$path]as$p){
        if(is_link($p))throw new RuntimeException('Nieprawidłowy magazyn sprzętu.');
        if(!file_exists($p)&&$create&&!@mkdir($p,0700)&&!is_dir($p))throw new RuntimeException('Brak zapisu wiedzy sprzętu.');
        if(file_exists($p)&&(!is_dir($p)||realpath($p)!==$p))throw new RuntimeException('Nieprawidłowy magazyn sprzętu.');
    }
    if($create){
        $file=$path.'/label.txt';
        if(is_link($file))throw new RuntimeException('Nieprawidłowa etykieta sprzętu.');
        if(file_exists($file)){
            if(!is_file($file)||filesize($file)>300||file_get_contents($file)!==$label)throw new RuntimeException('Niezgodna etykieta sprzętu.');
        }else rm_ai_knowledge_atomic($file,$label);
    }
    return $path;
}
function rm_ai_equipment_notes(string $base): array {
    $root=$base.'/equipment-knowledge';
    if(is_link($root))throw new RuntimeException('Nieprawidłowy magazyn sprzętu.');
    if(!file_exists($root))return [];
    if(!is_dir($root)||realpath($root)!==$root)throw new RuntimeException('Nieprawidłowy magazyn sprzętu.');
    $entries=scandir($root);if($entries===false||count($entries)>152)throw new RuntimeException('Nieprawidłowa lista wiedzy sprzętu.');
    $notes=[];
    foreach($entries as$key){
        if($key==='.'||$key==='..')continue;
        if(!preg_match('/^[a-f0-9]{64}$/D',$key))throw new RuntimeException('Nieprawidłowy wpis wiedzy sprzętu.');
        $path=$root.'/'.$key;$file=$path.'/label.txt';
        if(is_link($path)||!is_dir($path)||is_link($file))throw new RuntimeException('Nieprawidłowy wpis wiedzy sprzętu.');
        // A directory can exist briefly before the initial atomic label write.
        if(!file_exists($file))continue;
        if(!is_file($file)||filesize($file)>300)throw new RuntimeException('Nieprawidłowa etykieta sprzętu.');
        $label=(string)file_get_contents($file);
        if($label===''||preg_match('//u',$label)!==1||!hash_equals($key,hash('sha256',$label)))throw new RuntimeException('Nieprawidłowa etykieta sprzętu.');
        $notes[$label]=rm_ai_knowledge_read($path,false)['text'];
    }
    return $notes;
}
