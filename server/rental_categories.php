<?php
declare(strict_types=1);
require_once __DIR__ . '/related_offers.php';

function rm_categories(): array {
    return [
        'sprzet-budowlany' => ['key'=>'budowlane', 'name'=>'Sprzęt budowlany',
            'title'=>'Wynajem sprzętu budowlanego Kraków | RentMor',
            'heading'=>'Wynajem sprzętu budowlanego w Krakowie',
            'description'=>'Sprzęt do remontu i prac budowlanych w Krakowie, Czyżynach. Sprawdź aktualne ceny wynajmu, kaucje i wyposażenie urządzeń w RentMor.',
            'intro'=>'Wybierz urządzenie do przygotowania ścian, obróbki posadzek, cięcia lub prac instalacyjnych. W ofertach sprawdzisz parametry, wyposażenie i koszty wynajmu.',
            'advice'=>'Przy wyborze podaj rodzaj materiału i zakres pracy. Do urządzeń wytwarzających pył dobierz odpowiedni odciąg, a przed odbiorem sprawdź masę maszyny i możliwości transportu. Dostępność osprzętu oraz urządzenia potwierdzamy przy rezerwacji.'],
        'sprzet-ogrodniczy' => ['key'=>'ogrodnicze', 'name'=>'Sprzęt ogrodniczy',
            'title'=>'Wypożyczalnia sprzętu ogrodniczego Kraków | RentMor',
            'heading'=>'Wynajem sprzętu ogrodniczego w Krakowie',
            'description'=>'Wynajem sprzętu do prac ogrodowych w Krakowie, Czyżynach. Porównaj urządzenia, ceny dobowe i kaucje. Odbiór osobisty po rezerwacji w RentMor.',
            'intro'=>'Przygotuj ziemię, zadbaj o trawnik lub uporządkuj gałęzie. Poniżej znajdziesz aktualne urządzenia ogrodnicze oraz odnośniki do ich parametrów i cen.',
            'advice'=>'Dobierz sprzęt do wielkości terenu, rodzaju gleby lub średnicy gałęzi. Przeczytaj ograniczenia konkretnego modelu. Sprawdź masę i wymiary urządzenia, żeby przygotować odpowiedni samochód lub przyczepkę.'],
        'sprzet-czyszczacy' => ['key'=>'czyszczace', 'name'=>'Sprzęt czyszczący',
            'title'=>'Wynajem sprzętu czyszczącego Kraków | RentMor',
            'heading'=>'Wynajem sprzętu czyszczącego w Krakowie',
            'description'=>'Sprzęt do czyszczenia na wynajem w Krakowie, Czyżynach. Sprawdź urządzenia, końcówki, ceny dobowe i kaucje w aktualnej ofercie RentMor.',
            'intro'=>'Znajdź urządzenie do sprzątania i czyszczenia powierzchni. W szczegółach każdej oferty sprawdzisz przeznaczenie, dołączone akcesoria i dostępne dodatki.',
            'advice'=>'Dobierz sposób czyszczenia do materiału. Przed praniem tapicerki sprawdź zalecenia jej producenta i wykonaj próbę w niewidocznym miejscu. Uwzględnij czas schnięcia oraz koszt odpowiedniego środka czyszczącego.'],
    ];
}
function rm_category_escape(string $s): string { return htmlspecialchars($s, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'); }
function rm_category_items(array $catalogue, string $key): array {
    $items = [];
    foreach ($catalogue as $item) {
        if (!is_array($item) || !is_string($item['name'] ?? null)) continue;
        if (rm_related_normalize((string)($item['category'] ?? $item['cat'] ?? '')) !== $key) continue;
        $slug = rm_related_slug($item['name']);
        if ($slug !== '') $items[$slug] = $item;
    }
    ksort($items);
    return $items;
}
function rm_category_nav(): string {
    $html = '<nav class="rm-seo-links" aria-label="Kategorie sprzętu">';
    foreach (rm_categories() as $slug=>$category) {
        $html .= '<a href="/' . $slug . '/">' . rm_category_escape($category['name']) . '</a>';
    }
    return $html . '</nav>';
}
function rm_category_json(array $data): string {
    return (string)json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_HEX_TAG | JSON_HEX_AMP | JSON_HEX_APOS | JSON_HEX_QUOT);
}

/** Native disclosure remains usable with JavaScript disabled. */
function rm_category_menu(): string {
    $html = '<details class="rm-category-menu" id="rm-category-menu"><summary aria-label="Menu kategorii sprzętu" title="Kategorie sprzętu"><svg aria-hidden="true" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18M3 12h18M3 18h18"/></svg></summary><div class="rm-category-dropdown"><strong>Kategorie sprzętu</strong>';
    foreach (rm_categories() as $slug=>$category) {
        $html .= '<a href="/' . $slug . '/">' . rm_category_escape($category['name']) . '</a>';
    }
    return $html . '</div></details><script>(function(){var menu=document.getElementById("rm-category-menu");if(!menu)return;document.addEventListener("click",function(e){if(!menu.contains(e.target))menu.open=false;});document.addEventListener("keydown",function(e){if(e.key==="Escape"&&menu.open){menu.open=false;menu.querySelector("summary").focus();}});menu.addEventListener("focusout",function(e){if(e.relatedTarget&&!menu.contains(e.relatedTarget))menu.open=false;});})();</script>';
}

function rm_add_header_category_menu(string $html): string {
    if (str_contains($html, 'id="rm-category-menu"')) return $html;
    // Match only the known public header logo; leave unfamiliar templates intact.
    return (string)preg_replace_callback('~<nav\b[^>]*>.*?</nav>~is', static function ($nav) {
        if (!str_contains($nav[0], 'aria-label="RentMor.pl — strona główna"')) return $nav[0];
        return preg_replace_callback('~<a\b[^>]*aria-label="RentMor.pl — strona główna"[^>]*>.*?</a>~is', static function ($logo) {
            return '<div class="rm-header-brand">' . rm_category_menu() . $logo[0] . '</div>';
        }, $nav[0], 1);
    }, $html, 1);
}
