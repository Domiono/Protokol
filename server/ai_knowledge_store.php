<?php
declare(strict_types=1);
require_once __DIR__.'/ai_equipment_knowledge.php';
/** Reads only whitelisted public catalogue fields and a dedicated private knowledge note. */
function rm_ai_knowledge_text(string $text,int $max): string {
    if(preg_match('//u',$text)!==1)throw new RuntimeException('Nieprawidłowy tekst wiedzy.');
    $text=html_entity_decode($text,ENT_QUOTES|ENT_HTML5,'UTF-8');
    $text=preg_replace('~<(script|style)\b[^>]*>.*?</\1>~is','',$text);
    $text=strip_tags(str_replace(['<br>','<br/>','<br />',"</p>"],"\n",$text));
    $text=trim($text);
    if(strlen($text)>$max){$text=substr($text,0,$max);while(preg_match('//u',$text)!==1)$text=substr($text,0,-1);}
    return $text;
}
function rm_ai_catalogue(string $file): array {
    if(is_link($file)||!is_file($file)||filesize($file)>1048576)throw new RuntimeException('Katalog strony jest niedostępny.');
    $raw=file_get_contents($file);$data=json_decode((string)$raw,true);
    if(!is_array($data)||!array_is_list($data)||count($data)>150)throw new RuntimeException('Katalog strony jest nieprawidłowy.');
    $out=[];
    foreach($data as$p){
        if(!is_array($p)||!is_string($p['name']??null)||!is_numeric($p['price']??null)||!is_numeric($p['deposit']??null))throw new RuntimeException('Nieprawidłowa pozycja katalogu.');
        $price=(float)$p['price'];$deposit=(float)$p['deposit'];
        if(!is_finite($price)||!is_finite($deposit)||$price<0||$deposit<0)throw new RuntimeException('Nieprawidłowa cena katalogu.');
        $out[]=['name'=>rm_ai_knowledge_text($p['name'],300),'price_pln_per_day'=>$price,'deposit_pln'=>$deposit,
            'description'=>rm_ai_knowledge_text(is_string($p['desc']??null)?$p['desc']:'',6000)];
    }
    return ['products'=>$out,'sha256'=>hash('sha256',(string)$raw)];
}
function rm_ai_knowledge_read(string $dir,bool $equipment=true): array {
    $file=$dir.'/knowledge.json';
    if(is_link($file))throw new RuntimeException('Niebezpieczna ścieżka wiedzy.');
    if(!file_exists($file))return ['text'=>'','revision'=>hash('sha256',''),'updated_at'=>null,'equipment_notes'=>$equipment?rm_ai_equipment_notes($dir):[]];
    if(!is_file($file)||filesize($file)>25000)throw new RuntimeException('Wiedza jest niedostępna.');
    $raw=file_get_contents($file);$data=json_decode((string)$raw,true);
    if(!is_array($data)||!is_string($data['text']??null)||strlen($data['text'])>12000||preg_match('//u',$data['text'])!==1||!is_string($data['updated_at']??null))throw new RuntimeException('Wiedza jest uszkodzona. Zapis wstrzymany.');
    return ['text'=>$data['text'],'revision'=>hash('sha256',(string)$raw),'updated_at'=>$data['updated_at'],'equipment_notes'=>$equipment?rm_ai_equipment_notes($dir):[]];
}
function rm_ai_knowledge_atomic(string $path,string $raw): void {
    if(is_link($path))throw new RuntimeException('Niebezpieczna ścieżka wiedzy.');
    $tmp=tempnam(dirname($path),'.knowledge-');if($tmp===false)throw new RuntimeException('Brak zapisu wiedzy.');
    try{
        if(!chmod($tmp,0600)||file_put_contents($tmp,$raw,LOCK_EX)!==strlen($raw)||!rename($tmp,$path))throw new RuntimeException('Brak zapisu wiedzy.');
    }finally{if(is_file($tmp))unlink($tmp);}
}
function rm_ai_knowledge_save(string $dir,string $text,string $revision): void {
    if(strlen($text)>12000||preg_match('//u',$text)!==1||preg_match('/[\x00-\x08\x0B\x0C\x0E-\x1F]/',$text))throw new InvalidArgumentException('Wiedza: maksymalnie 12000 bajtów poprawnego tekstu.');
    if(preg_match('/\bsk-[A-Za-z0-9_-]{20,}\b|-----BEGIN .*PRIVATE KEY-----/',$text))throw new InvalidArgumentException('Usuń klucz lub sekret z wiedzy.');
    if(is_link($dir.'/knowledge.lock'))throw new RuntimeException('Blokada wiedzy niedostępna.');
    $lock=fopen($dir.'/knowledge.lock','c');
    if(!$lock||!flock($lock,LOCK_EX)){if($lock)fclose($lock);throw new RuntimeException('Blokada wiedzy niedostępna.');}
    try{
        if(!chmod($dir.'/knowledge.lock',0600))throw new RuntimeException('Blokada wiedzy niedostępna.');
        $current=rm_ai_knowledge_read($dir,false);
        if(!hash_equals($current['revision'],$revision))throw new RuntimeException('Wiedzę zmieniono w innym oknie. Odśwież stronę przed zapisem.');
        $file=$dir.'/knowledge.json';
        if(is_file($file))rm_ai_knowledge_atomic($dir.'/knowledge.previous.json',(string)file_get_contents($file));
        rm_ai_knowledge_atomic($file,json_encode(['text'=>trim($text),'updated_at'=>gmdate('c')],JSON_THROW_ON_ERROR|JSON_UNESCAPED_UNICODE));
    }finally{flock($lock,LOCK_UN);fclose($lock);}
}
function rm_ai_knowledge_context(array $catalogue,array $note,string $question): string {
    $products=$catalogue['products'];$terms=preg_split('/[^\pL\pN]+/u',mb_strtolower($question,'UTF-8'),-1,PREG_SPLIT_NO_EMPTY);
    $scored=[];
    foreach($products as$i=>$p){$hay=mb_strtolower($p['name'].' '.$p['description'],'UTF-8');$score=0;
        foreach(array_unique($terms)as$t)if(mb_strlen($t,'UTF-8')>=3&&str_contains($hay,$t))$score++;
        $scored[]=['i'=>$i,'score'=>$score];
    }
    usort($scored,fn($a,$b)=>($b['score']<=>$a['score'])?:($a['i']<=>$b['i']));
    $detail=array_column(array_slice(array_filter($scored,fn($r)=>$r['score']>0),0,3),'i');
    foreach($products as$i=>&$p){if(!in_array($i,$detail,true))unset($p['description']);else$p['description']=rm_ai_knowledge_text($p['description'],3500);}unset($p);
    $equipmentNotes=[];
    foreach($detail as$i){$name=$products[$i]['name'];$text=$note['equipment_notes'][$name]??'';if($text!==''&&count($equipmentNotes)<2)$equipmentNotes[]=['equipment'=>$name,'text'=>$text];}
    $data=['source'=>'Aktualny katalog RentMor.pl odczytany podczas zapytania','products'=>$products,
        'owner_knowledge'=>$note['text'],'owner_updated_at'=>$note['updated_at'],'equipment_knowledge'=>$equipmentNotes];
    $context=json_encode($data,JSON_THROW_ON_ERROR|JSON_UNESCAPED_UNICODE);
    if(strlen($context)>80000){foreach($data['products']as&$product)unset($product['description']);unset($product);$context=json_encode($data,JSON_THROW_ON_ERROR|JSON_UNESCAPED_UNICODE);}
    while(strlen($context)>80000&&count($data['equipment_knowledge'])>0){array_pop($data['equipment_knowledge']);$context=json_encode($data,JSON_THROW_ON_ERROR|JSON_UNESCAPED_UNICODE);}
    if(strlen($context)>80000)throw new RuntimeException('Za dużo wiedzy w zapytaniu.');
    return $context;
}
