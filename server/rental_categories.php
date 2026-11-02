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
