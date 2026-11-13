<?php
declare(strict_types=1);
require_once __DIR__ . '/rental_offer_details.php';

function rm_offer_fields(): array {
    return ['title'=>'Nagłówek dodatkowego opisu', 'intro'=>'Dodatkowy opis, zastosowania i ograniczenia',
        'booking_title'=>'Nagłówek rezerwacji i odbioru', 'booking_text'=>'Rezerwacja, odbiór i zwrot',
        'rental_note'=>'Informacja pod nazwą sprzętu', 'price_note'=>'Dodatkowe podsumowanie kosztów',
        'related_title'=>'Nagłówek powiązanego sprzętu', 'related_note'=>'Informacja pod powiązanym sprzętem',
        'guide'=>'Adres poradnika', 'guide_label'=>'Nazwa linku do poradnika', 'rules_label'=>'Nazwa linku do zasad'];
}
function rm_offer_defaults(array $product): array {
    $base = rentmor_default_offer($product);
    $base += ['title'=>'','intro'=>'','questions'=>[], 'related'=>[], 'guide'=>'', 'guide_label'=>''];
    $base['questions'] = array_map(static fn($q,$a)=>['question'=>$q,'answer'=>$a], array_keys($base['questions']), array_values($base['questions']));
    $base['related'] = array_map(static fn($r)=>['url'=>$r[0],'label'=>$r[1]], $base['related']);
    $base['booking_title']='Jak zarezerwować i odebrać sprzęt?';
    $base['booking_text']='Zadzwoń pod 733 891 281 lub wyślij zgłoszenie przez formularz rezerwacji przy sprzęcie. Rezerwacja jest potwierdzona dopiero po akceptacji przez RentMor. Doba wynajmu trwa 24 godziny od ustalonej godziny odbioru, np. od 16:00 do 16:00 następnego dnia. Odbiór i zwrot odbywają się osobiście w Krakowie, w Czyżynach przy ul. Woźniców. Dokładny adres otrzymasz przy rezerwacji. Transport zapewniasz we własnym zakresie — nie świadczymy usługi dowozu. Przy odbiorze podpisujesz umowę i wpłacasz kaucję wskazaną w ofercie. Przedłużenie najmu wymaga wcześniejszego uzgodnienia.';
    $base['rental_note']='Cena podstawowa za dobę wynajmu. Doba wynajmu trwa 24 godziny od ustalonej godziny odbioru, np. od 16:00 do 16:00 następnego dnia. Kaucja i ewentualne dodatki zgodnie z opisem. Dostępność na termin potwierdzamy przy rezerwacji.';
    $base['related_title']='Sprzęt przydatny do tego zadania';
    $base['related_note']='Dostępność każdego urządzenia potwierdzamy osobno podczas rezerwacji.';
    $base['rules_label']='Zasady wynajmu, kaucji i zwrotu sprzętu';
    $base['price_note']='';
    $name=strtolower((string)($product['name']??''));
    if (str_contains($name,'szlifierka do betonu') && str_contains($name,'250')) {
        $format=static fn($v)=>rtrim(rtrim(number_format($v,2,',',''),'0'),',');
        $price=(float)($product['price']??0);
        $base['price_note']='Koszt wynajmu: sama szlifierka '.$format($price).' zł/doba; z odkurzaczem '.$format($price+30).' zł/doba (odkurzacz +30 zł/doba). Zużycie tarczy do betonu dodatkowo: 40 zł za każde rozpoczęte 0,5 mm. Kaucja jest podana poniżej.';
    }
    return $base;
}
function rm_offer_content(array $product): array {
    return array_replace(rm_offer_defaults($product), is_array($product['offer_content']??null)?$product['offer_content']:[]);
}
function rm_offer_revision(array $product): string {
    return hash('sha256', json_encode(['content'=>rm_offer_content($product),'product'=>array_intersect_key($product,array_flip(['id','name','desc','category','price','deposit','seo_title','seo_description','olx','images']))], JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR));
}
function rm_offer_text($value, int $max): string {
    if (!is_string($value) || !preg_match('//u',$value) || strlen($value)>$max) throw new InvalidArgumentException('Nieprawidłowa lub zbyt długa treść oferty.');
    return trim(str_replace(["\r\n","\r"],"\n",$value));
}
function rm_offer_url(string $url): string {
    if ($url!=='' && (!preg_match('~^/(?!/)[a-zA-Z0-9/_?&=.%#-]*$~D',$url) && !preg_match('~^https://rentmor\.pl/[a-zA-Z0-9/_?&=.%#-]*$~D',$url))) throw new InvalidArgumentException('Podaj adres strony lub poradnika w domenie RentMor.pl.');
    return $url;
}
function rm_validate_offer_content($input): array {
    if (!is_array($input)) throw new InvalidArgumentException('Brak treści oferty.');
    $out=[];
    foreach(rm_offer_fields() as $key=>$label) $out[$key]=rm_offer_text($input[$key]??'',in_array($key,['intro','booking_text'],true)?16000:4000);
    $out['guide']=rm_offer_url($out['guide']);
    foreach(['questions'=>['question','answer'],'related'=>['url','label']] as $key=>$columns) {
        $rows=$input[$key]??[];
        if (!is_array($rows) || count($rows)>40) throw new InvalidArgumentException('Maksymalnie 40 pozycji w sekcji.');
        $out[$key]=[];
        foreach($rows as $row) {
            if (!is_array($row)) throw new InvalidArgumentException('Nieprawidłowa pozycja oferty.');
            $a=rm_offer_text($row[$columns[0]]??'',2000); $b=rm_offer_text($row[$columns[1]]??'',12000);
            if ($a==='' && $b==='') continue;
            if ($a==='' || $b==='') throw new InvalidArgumentException('Uzupełnij oba pola pozycji lub usuń ją.');
            if($key==='related') $a=rm_offer_url($a);
            $out[$key][]=[$columns[0]=>$a,$columns[1]=>$b];
        }
    }
    if(strlen(json_encode($out,JSON_UNESCAPED_UNICODE))>100000) throw new InvalidArgumentException('Treść oferty jest zbyt długa.');
    return $out;
}
/** Missing field means an older client: retain the saved content, never reset it. */
function rm_save_offer_content(array $old, array $input): array {
    if (!array_key_exists('offer_content',$input)) return is_array($old['offer_content']??null)?['offer_content'=>$old['offer_content']]:[];
    if (!is_string($input['offer_revision']??null) || !hash_equals(rm_offer_revision($old),$input['offer_revision'])) throw new InvalidArgumentException('Opis zmienił się w drugim edytorze. Odśwież ofertę przed ponownym zapisem.');
    $content=rm_validate_offer_content($input['offer_content']);
    $defaults=rm_offer_defaults($old);
    foreach($content as $key=>$value) if($value===$defaults[$key]) unset($content[$key]);
    return ['offer_content'=>$content];
}
function rm_offer_for_editor(array $p): array {
    $p['offer_revision']=rm_offer_revision($p);
    $p['offer_content']=rm_offer_content($p);
    return $p;
}
function rm_render_offer_content(array $product,bool $manual=true): string {
    // Unknown offers keep their historical empty extension unless explicitly edited.
    if (!isset($product['offer_content']) && rentmor_default_offer($product)===[]) return '';
    $o=rm_offer_content($product);
    $e=static fn($s)=>htmlspecialchars((string)$s,ENT_QUOTES,'UTF-8');
    $p=static fn($s)=>nl2br($e($s));
    $html='<section class="mt-8 border-t border-gray-200 pt-6" data-rentmor-offer-details="'.$e($product['id']??'').'">';
    if($o['title']!=='')$html.='<h2 class="text-xl sm:text-2xl font-bold mb-4">'.$e($o['title']).'</h2>';
    if($o['intro']!=='')$html.='<p class="mb-5">'.$p($o['intro']).'</p>';
    foreach($o['questions'] as $r)$html.='<h3 class="text-lg font-bold mt-5 mb-2">'.$e($r['question']).'</h3><p class="mb-4">'.$p($r['answer']).'</p>';
    if($o['booking_title']!=='')$html.='<h3 class="text-lg font-bold mt-5 mb-2">'.$e($o['booking_title']).'</h3>';
    if($o['booking_text']!=='')$html.='<p class="mb-4">'.str_replace('733 891 281','<a class="underline font-semibold" href="tel:+48733891281">733 891 281</a>',$p($o['booking_text'])).'</p>';
    if($o['guide']!=='' && $o['guide_label']!=='')$html.='<p class="mb-4"><a class="underline font-semibold" href="'.$e(rm_offer_url($o['guide'])).'">'.$e($o['guide_label']).'</a></p>';
    $customRelated=array_intersect_key($product['offer_content']??[],array_flip(['related','related_title','related_note']));
    if(($manual || $customRelated) && $o['related']){
        $html.='<nav class="mt-6 mb-5 border-t border-gray-200 pt-5" aria-label="Sprzęt powiązany"><h3 class="text-lg font-bold mb-3">' . $e($o['related_title']) . '</h3><ul class="space-y-3">';
        foreach($o['related'] as $r)$html.='<li><a class="underline font-semibold" href="'.$e(rm_offer_url($r['url'])).'">'.$e($r['label']).'</a></li>';
        $html.='</ul><p class="text-sm mt-3">' . $p($o['related_note']) . '</p></nav>';
    }
    if($o['rules_label']!=='')$html.='<p><a class="underline" href="/zasady.php">'.$e($o['rules_label']).'</a></p>';
    return $html.'</section>';
}
