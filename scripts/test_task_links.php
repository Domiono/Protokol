<?php
declare(strict_types=1);
require __DIR__.'/../server/rental_task_links.php';
require __DIR__.'/../server/seo_structure.php';
function check_link($ok,$message){if(!$ok)throw new RuntimeException($message);}
$items=[
 ['id'=>'new-airless-id','name'=>'Agregat malarski WABROTECH WT20i','category'=>'budowlane'],
 ['id'=>'new-floor-id','name'=>'Szlifierka do betonu i posadzek 250mm','category'=>'budowlane'],
 ['id'=>'other','name'=>'Agregat prądotwórczy','category'=>'budowlane'],
];
$targets=rm_task_link_targets($items);check_link(count($targets)===2,'Two appropriate targets');
check_link($targets['airless']['url']==='/agregat-malarski-wabrotech-wt20i/','Actual catalogue slug');
$html=rm_task_equipment_links($items);check_link(substr_count($html,'<a href=')===2,'One link per task');
check_link(str_contains($html,'wynajem agregatu malarskiego w Krakowie'),'Descriptive airless link');
check_link(str_contains($html,'wynajem szlifierki do betonu w Krakowie'),'Descriptive floor link');
check_link(!str_contains($html,'href="/"'),'No task link to homepage');
check_link(rm_task_equipment_links([])==='','No absent-product links');
check_link(rm_airless_offer_link('Oferta',[$items[1]])==='','Guide link omitted when offer removed');
$changed=$items;$changed[0]['name'].=' nowa oferta';
check_link(str_contains(rm_airless_offer_link('Oferta',$changed),'-nowa-oferta/'),'Renames update links');
check_link(str_contains(rm_airless_offer_link('<script>',$items),'&lt;script&gt;'),'Labels escaped');
check_link(!str_contains(rm_task_equipment_links([['name'=>'Agregat malarski WT20i','category'=>'ogrodnicze']]),'href='),'Category respected');
$home=rm_home_sections($items);check_link(substr_count($home,'data-rm-task-links=')===1,'Home contextual links once');
check_link(str_contains($home,$targets['airless']['url']),'Home points to actual offer');
check_link(!str_contains(rm_home_sections([]),'data-rm-task-links='),'Home tolerates empty catalogue');
$category=file_get_contents(__DIR__.'/../server/category.php');check_link(str_contains($category,"if ($"."category['key']==='budowlane') echo rm_task_equipment_links($"."catalogue)"),'Category context restricted');
$guide=file_get_contents(__DIR__.'/../server/poradnik-agregat-malarski-jak-malowac-dobor-dysz.php');check_link(str_contains($guide,'data-rm-airless-offer-link="v1"'),'Early guide link present');
check_link(!str_contains($guide,'href="/agregat-malarski-wabrotech-wt20i-wynajem-krakow-malowanie-natryskowe/"'),'Guide does not hardcode potentially stale URL');
echo "PASS: task links actual targets, renamed/missing equipment, no homepage targets, escaping, home/category/guide integration\n";
