<?php
declare(strict_types=1);
require_once __DIR__.'/rental_categories.php';
/** Read only the public catalogue. Never manufacture a URL for missing equipment. */
function rm_task_link_catalogue(): array {
    $raw=@file_get_contents(__DIR__.'/data.json');
    $items=is_string($raw)?json_decode($raw,true):null;
    return is_array($items)?$items:[];
}
function rm_task_link_targets(array $catalogue): array {
    $targets=[];
    foreach(rm_category_items($catalogue,'budowlane') as $slug=>$item){
        $name=rm_related_normalize($item['name']);
        if(str_contains($name,'agregat malarski')&&str_contains($name,'wt20i'))
            $targets['airless']=['url'=>'/'.$slug.'/','label'=>'wynajem agregatu malarskiego w Krakowie'];
        if((str_contains($name,'szlifierka do betonu')||str_contains($name,'msw fg 250'))&&str_contains($name,'250'))
            $targets['floor']=['url'=>'/'.$slug.'/','label'=>'wynajem szlifierki do betonu w Krakowie'];
    }
    return $targets;
}
function rm_task_equipment_links(?array $catalogue=null): string {
    $targets=rm_task_link_targets($catalogue??rm_task_link_catalogue());
    if(!$targets)return '';
    $e=static fn($s)=>htmlspecialchars($s,ENT_QUOTES,'UTF-8');$parts=[];
    if(isset($targets['airless']))$parts[]='Do malowania ścian i sufitów sprawdź <a href="'.$e($targets['airless']['url']).'">'.$e($targets['airless']['label']).'</a>.';
    if(isset($targets['floor']))$parts[]='Do obróbki posadzki zobacz <a href="'.$e($targets['floor']['url']).'">'.$e($targets['floor']['label']).'</a>.';
    return '<p class="rm-seo-muted" data-rm-task-links="v1">'.implode(' ',$parts).'</p>';
}
function rm_airless_offer_link(string $label,?array $catalogue=null): string {
    $target=rm_task_link_targets($catalogue??rm_task_link_catalogue())['airless']??null;
    if($target===null)return '';
    $e=static fn($s)=>htmlspecialchars($s,ENT_QUOTES,'UTF-8');
    return '<a href="'.$e($target['url']).'">'.$e($label).'</a>';
}
