<?php
declare(strict_types=1);

function rentmor_offer_details(array $product): string {
    $id = (string)($product['id'] ?? '');
    $offers = [
        '69388418830b8' => [
            'title' => 'Wynajem odkurzacza piorącego Puzzi w Krakowie',
            'intro' => 'Kärcher Puzzi 10/1 sprawdzi się przy samodzielnym praniu kanapy, narożnika, foteli, dywanów i tapicerki samochodowej, jeśli materiał dopuszcza czyszczenie na mokro. Przed rozpoczęciem sprawdź zalecenia producenta tapicerki i wykonaj próbę w niewidocznym miejscu.',
            'questions' => [
                'Co otrzymuję w zestawie?' => 'Odkurzacz Puzzi 10/1, opryskiwacz oraz końcówki do dywanów i tapicerki meblowej lub samochodowej. Pełny zestaw i aktualne ceny znajdziesz w opisie powyżej.',
                'Czy środek do prania jest w cenie wynajmu?' => 'Proszek Kärcher MR 760 oa można dokupić osobno. Cenę i wielkość porcji podajemy w opisie oferty. Używaj wyłącznie środka odpowiedniego do ekstrakcji i czyszczonej powierzchni.',
                'Jak przygotować tapicerkę do prania?' => 'Najpierw dokładnie odkurz ją na sucho. Sprawdź oznaczenia materiału, dobierz środek i zaplanuj czas na wyschnięcie. Nie przemaczaj tapicerki; po praniu zapewnij wentylację.',
                'Jak przygotować Puzzi do zwrotu?' => 'Opróżnij zbiornik brudnej wody i oczyść urządzenie zgodnie z instrukcją przekazaną przy odbiorze. Zwróć wszystkie końcówki i opryskiwacz. Szczegółowe zasady czyszczenia określają uzgodnienia i umowa.',
            ],
            'guide' => '/poradnik-pranie-tapicerki-karcher-puzzi.php',
            'guide_label' => 'Jak wyprać tapicerkę odkurzaczem Puzzi 10/1?',
        ],
        '69385df56774a' => [
            'title' => 'Wynajem żyrafy z odkurzaczem w Krakowie',
            'intro' => 'Zestaw szlifierki Stanley i odkurzacza przemysłowego służy do szlifowania gładzi na ścianach i sufitach z odsysaniem pyłu. Dobór papieru zależy od rodzaju i stanu powierzchni. Odsysanie ogranicza pylenie, ale nie zastępuje ochrony dróg oddechowych ani zabezpieczenia pomieszczenia.',
            'questions' => [
                'Co zawiera zestaw do szlifowania gładzi?' => 'Żyrafę Stanley z oświetleniem LED, odkurzacz Bosch lub Metabo oraz worek. Dostępny model odkurzacza potwierdzisz przy rezerwacji.',
                'Czy papier ścierny jest w cenie zestawu?' => 'Papier ścierny jest sprzedawany osobno. Aktualna cena i dostępne gradacje znajdują się w opisie. Dobierz gradację do powierzchni i wykonaj próbę na niewielkim fragmencie ściany.',
                'Czy mogę wynająć samą żyrafę?' => 'Tak, żyrafa jest również dostępna jako oddzielna oferta. Dostępność konkretnego urządzenia oraz zestawu potwierdzamy na wybrany termin.',
                'Jak przygotować zestaw do zwrotu?' => 'Po odłączeniu zasilania oczyść sprzęt zgodnie z instrukcją. Zwróć szlifierkę, odkurzacz i całe wydane wyposażenie. Sposób postępowania z workiem oraz zasady czyszczenia ustal przy odbiorze.',
            ],
            'guide' => '/poradnik-jaki-papier-scierny-do-zyrafy.php',
            'guide_label' => 'Jaki papier ścierny dobrać do żyrafy?',
        ],
        '69385f95de01c' => [
            'title' => 'Wynajem agregatu malarskiego WT20i w Krakowie',
            'intro' => 'WABROTECH WT20i jest przeznaczony do malowania natryskowego ścian, sufitów i innych dużych powierzchni. Przed rezerwacją sprawdź kartę techniczną farby: dopuszczenie do natrysku, zalecaną dyszę i ciśnienie. Wydajność agregatu wynosi do 2 l/min, a maksymalne ciśnienie robocze to 210 bar.',
            'questions' => [
                'Co otrzymuję wraz z agregatem?' => 'W zestawie znajdują się dysze 511, 517 i 521, pistolet, wąż ciśnieniowy 15 m, filtr paluszkowy, przedłużka 450 mm, olej konserwacyjny i instrukcja. Przed odbiorem potwierdź dostępność dyszy odpowiedniej do Twojej farby.',
                'Czy mogę użyć farby olejnej lub rozpuszczalnikowej?' => 'Nie. Agregat wynajmujemy wyłącznie do farb i gruntów wodnych. Nie dopuszczamy farb olejnych ani rozpuszczalnikowych.',
                'Którą dyszę wybrać do malowania?' => 'Dobierz ją według karty technicznej farby. Dysze 511, 517 i 521 mają różne średnice otworu. Maksymalny rozmiar dla WT20i to 0,021 cala; farba musi być zgodna z możliwościami urządzenia. Nie używaj materiałów strukturalnych.',
                'Jak przygotować agregat do zwrotu?' => 'Nie pozostawiaj farby w pompie ani wężu. Odciąż ciśnienie i przepłucz urządzenie zgodnie z instrukcją WT20i oraz zaleceniami przekazanymi przy odbiorze. Zwróć cały wydany osprzęt; w razie problemu z czyszczeniem skontaktuj się z RentMor.',
            ],
            'guide' => '/poradnik-agregat-malarski-jak-malowac-dobor-dysz.php',
            'guide_label' => 'Jak malować agregatem i dobrać dyszę 511, 517 lub 521?',
        ],
        '693844ff66e57' => [
            'title' => 'Wynajem zagęszczarki 18 kN, 101 kg w Krakowie',
            'intro' => 'Jednokierunkowa zagęszczarka spalinowa Dro-Masz o sile 18 kN i masie 101 kg nadaje się do przygotowania i zagęszczania podbudowy pod kostkę brukową oraz innych prac, w których takie parametry są odpowiednie do rodzaju materiału i grubości warstwy. Płyta robocza ma wymiary 60 × 45 cm. Przy odbiorze otrzymujesz również matę ochronną do kostki.',
            'questions' => [
                'Do czego przydaje się zagęszczarka 18 kN?' => 'Między innymi do zagęszczania odpowiednio przygotowanych warstw podbudowy pod chodnik, podjazd lub kostkę brukową. Dobór maszyny i grubości kolejnych warstw powinien wynikać z rodzaju gruntu lub kruszywa oraz wymagań wykonywanej nawierzchni.',
                'Czy można nią zagęszczać ułożoną kostkę brukową?' => 'W zestawie jest mata ochronna, która ogranicza ryzyko porysowania i uszkodzenia kostki. Przed pracą sprawdź zalecenia producenta kostki dotyczące zagęszczania i rodzaju maty; nie każda nawierzchnia dopuszcza takie samo postępowanie.',
                'Jak przetransportować maszynę ważącą 101 kg?' => 'Zagęszczarka ma koła transportowe, ale waży 101 kg. Zaplanuj bezpieczny załadunek, właściwe unieruchomienie urządzenia i odpowiedni pojazd lub przyczepę. Transport odbywa się we własnym zakresie.',
                'Ile kosztuje wynajem zagęszczarki w Krakowie?' => 'Aktualną stawkę za dobę i kaucję podajemy powyżej. W opisie oferty znajdują się też rabaty na 5 i 6 dób; przy dłuższym wynajmie warunki ustalamy indywidualnie.',
                'Na co uważać podczas pracy?' => 'Przestrzegaj instrukcji obsługi i stosuj ochronniki słuchu oraz odpowiednie obuwie. Silnik spalinowy wytwarza trujące spaliny, dlatego nie wolno używać urządzenia w zamkniętych pomieszczeniach.',
            ],
        ],
    ];
    if (!isset($offers[$id])) return '';
    $offer = $offers[$id];
    $e = static fn($s) => htmlspecialchars((string)$s, ENT_QUOTES, 'UTF-8');
    $html = '<section class="mt-8 border-t border-gray-200 pt-6" data-rentmor-offer-details="' . $e($id) . '">';
    $html .= '<h2 class="text-xl sm:text-2xl font-bold mb-4">' . $e($offer['title']) . '</h2><p class="mb-5">' . $e($offer['intro']) . '</p>';
    foreach ($offer['questions'] as $question => $answer) {
        $html .= '<h3 class="text-lg font-bold mt-5 mb-2">' . $e($question) . '</h3><p class="mb-4">' . $e($answer) . '</p>';
    }
    $html .= '<h3 class="text-lg font-bold mt-5 mb-2">Jak zarezerwować i odebrać sprzęt?</h3><p class="mb-4">Zadzwoń pod <a class="underline font-semibold" href="tel:+48733891281">733 891 281</a> i ustal termin. Odbiór i zwrot odbywają się osobiście w Krakowie, w Czyżynach przy ul. Woźniców. Dokładny adres otrzymasz przy rezerwacji. Transport zapewniasz we własnym zakresie. Przy odbiorze podpisujesz umowę i wpłacasz kaucję wskazaną w ofercie. Przedłużenie najmu wymaga wcześniejszego uzgodnienia.</p>';
    if (!empty($offer['guide']) && !empty($offer['guide_label'])) {
        $html .= '<p class="mb-4"><a class="underline font-semibold" href="' . $e($offer['guide']) . '">' . $e($offer['guide_label']) . '</a></p>';
    }
    if ($id === '69385df56774a') $html .= '<p class="mb-4"><a class="underline" href="/szlifierka-stanley-zyrafa-do-gladzi-gipsu/">Zobacz wynajem samej szlifierki żyrafy Stanley</a></p>';
    return $html . '<p><a class="underline" href="/zasady.php">Zasady wynajmu, kaucji i zwrotu sprzętu</a></p></section>';
}
