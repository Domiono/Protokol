<?php
declare(strict_types=1);

/**
 * Editorial SEO sections for the 15 catalogue offers not handled by
 * rental_offer_details.php's original curated entries.
 * No prices, deposits, availability, URLs or product data are changed.
 */
function rentmor_extra_offers(): array {
    return [
        'wiertnica-glebowa' => [
            'title' => 'Wiertnica spalinowa do gleby z wiertłami – wynajem Kraków',
            'intro' => 'Spalinowa wiertnica do ziemi 6,7 KM ułatwia wykonywanie otworów pod słupki ogrodzeniowe, podpory i nasadzenia. W komplecie są wiertła o średnicy 15, 20 i 30 cm oraz przedłużka 50 cm. Dobór średnicy i głębokości zależy od planowanych prac oraz warunków gruntu.',
            'questions' => [
                'Jakie średnice wierteł są dostępne?' => 'Zestaw zawiera wiertła 15, 20 i 30 cm oraz przedłużkę 50 cm. Przed rozpoczęciem pracy sprawdź możliwość bezpiecznego wiercenia w danym gruncie i wymagania dotyczące otworu.',
                'Czy wiertnica nadaje się do otworów pod słupki ogrodzeniowe?' => 'Tak, może pomóc w przygotowaniu otworów pod słupki, o ile średnica wiertła i grunt odpowiadają zadaniu. Najpierw sprawdź przebieg przewodów, rur i innych instalacji pod ziemią.',
                'Co należy przygotować do pracy?' => 'To sprzęt spalinowy wymagający właściwej mieszanki paliwowej zgodnie z instrukcją. Urządzenie może gwałtownie zareagować na przeszkodę w gruncie; zachowaj stabilną pozycję i stosuj środki ochrony.',
            ],
        ],
        'bosch-gws-2200' => [
            'title' => 'Szlifierka kątowa Bosch GWS 2200 230 mm – wynajem Kraków',
            'intro' => 'Bosch Professional GWS 2200 to duża szlifierka kątowa 230 mm do cięcia i obróbki odpowiednich materiałów z tarczą dobraną do zadania. Ma silnik 2200 W, funkcję łagodnego rozruchu i masę około 5,4 kg. Sprawdzi się przy pracach, które wymagają większej średnicy tarczy niż w małej szlifierce 125 mm.',
            'questions' => [
                'Czym różni się szlifierka 230 mm od modelu 125 mm?' => 'Model 230 mm przyjmuje większą tarczę i jest przeznaczony do innych zakresów prac niż kompaktowa szlifierka 125 mm. Rzeczywista głębokość cięcia zależy od osłony, tarczy i materiału; przed pracą sprawdź instrukcję.',
                'Jakie tarcze pasują do Bosch GWS 2200?' => 'Urządzenie jest przeznaczone do tarcz o średnicy maksymalnie 230 mm i ma wrzeciono M14. Dobierz wyłącznie tarczę i osłonę dopuszczone przez producenta do konkretnego materiału i prędkości obrotowej.',
                'Czy szlifierka nadaje się do pracy z betonem?' => 'Do cięcia odpowiednich materiałów mineralnych potrzebna jest właściwa tarcza oraz środki ograniczające pył. Do szlifowania większych powierzchni posadzek bardziej odpowiednia może być osobna szlifierka do betonu.',
            ],
        ],
        'dewalt-dws520ktr' => [
            'title' => 'Zagłębiarka DeWALT DWS520KTR z szyną 1,5 m – wynajem Kraków',
            'intro' => 'Zagłębiarka DeWALT DWS520KTR to pilarka tarczowa do prostych i dokładnych cięć w odpowiednich materiałach drewnopochodnych. W zestawie jest prowadnica 1,5 m z dwoma zaciskami oraz tarcza do drewna 48-zębna. Moc urządzenia to 1300 W, a maksymalna głębokość cięcia przy 90° bez prowadnicy wynosi 59 mm.',
            'questions' => [
                'Czy w zestawie jest szyna prowadząca?' => 'Tak. Oferta obejmuje szynę DeWALT o długości 150 cm, dwa zaciski i walizkę. Ułóż prowadnicę stabilnie i sprawdź położenie materiału przed cięciem.',
                'Do czego przydaje się zagłębiarka?' => 'Zagłębiarka pomaga wykonywać długie, proste cięcia w płytach oraz odpowiednio przygotowanych elementach drewnianych. Dobierz tarczę do ciętego materiału i sprawdź, czy dany rodzaj cięcia jest dopuszczony.',
                'Jak głęboko tnie DeWALT DWS520KTR?' => 'Według oferty maksymalna głębokość przy kącie 90° to 59 mm bez prowadnicy i 55 mm z prowadnicą; przy 45° odpowiednio 44 i 40 mm. Ustaw głębokość zgodnie z grubością obrabianego materiału.',
            ],
        ],
        'stiga-src-775' => [
            'title' => 'Glebogryzarka Stiga SRC 775 RG – wynajem Kraków',
            'intro' => 'Spalinowa glebogryzarka Stiga SRC 775 RG służy do spulchniania i przygotowania odpowiedniej gleby pod uprawy oraz nasadzenia. Ma szerokość roboczą 75 cm, deklarowaną głębokość pracy 26 cm, sześć noży i biegi do jazdy w przód oraz w tył.',
            'questions' => [
                'Do jakich prac ogrodowych nadaje się glebogryzarka?' => 'Urządzenie pomaga rozdrabniać i spulchniać ziemię przed sadzeniem oraz zakładaniem grządek. Przy bardzo zbitym gruncie lub kamieniach zaplanuj pracę etapami i sprawdź warunki podłoża.',
                'Jaką szerokość roboczą ma Stiga SRC 775 RG?' => 'W ofercie podano 75 cm szerokości i 26 cm głębokości roboczej. Rzeczywista głębokość pracy zależy również od rodzaju ziemi i ustawienia maszyny.',
                'Czy glebogryzarka zmieści się do samochodu?' => 'Maszyna waży około 50 kg, a szerokość podawana w ofercie wynosi 75 cm. Przed odbiorem sprawdź przestrzeń bagażową i zaplanuj bezpieczny załadunek oraz unieruchomienie.',
            ],
        ],
        'cedrus-rb02-pro' => [
            'title' => 'Rębak Cedrus RB02 PRO do gałęzi 8 cm – wynajem Kraków',
            'intro' => 'Cedrus RB02 PRO to spalinowy rozdrabniacz do gałęzi przeznaczony do porządkowania ogrodu po cięciu drzew i krzewów. Maksymalna średnica gałęzi podana w ofercie wynosi 8 cm, wydajność deklarowana to 3–5 m³/h, a masa urządzenia około 125 kg. Parametry nie oznaczają jednak, że każdy rodzaj drewna można rozdrabniać z taką samą wydajnością.',
            'questions' => [
                'Jak grube gałęzie można rozdrabniać Cedrus RB02 PRO?' => 'W ofercie podano maksymalną średnicę 8 cm. Przed podaniem gałęzi oceń gatunek, stan drewna i kształt materiału, a podczas pracy przestrzegaj instrukcji oraz ograniczeń urządzenia.',
                'Czy ten rębak nadaje się na większe porządki w ogrodzie?' => 'To większa maszyna niż kompaktowy rębak do gałęzi 3 cm. Może być przydatna po przycinaniu drzew, lecz tempo pracy zależy od rodzaju gałęzi, sposobu podawania i warunków.',
                'Jak przetransportować rębak o masie 125 kg?' => 'Zaplanuj odpowiedni pojazd lub przyczepę, bezpieczny załadunek i mocowanie. Przed odbiorem ustal sposób przewozu; nie zakładaj, że maszynę można bezpiecznie załadować samodzielnie do bagażnika.',
            ],
        ],
        'energotytan-ph32uth' => [
            'title' => 'Zaciskarka PEX Energotytan PH32UTH U/TH – wynajem Kraków',
            'intro' => 'Ręczna hydrauliczna zaciskarka Energotytan PH32UTH służy do zaprasowywania odpowiednich złączek instalacyjnych PEX. Oferta obejmuje matryce w profilach U i TH, a także akcesoria do przygotowania rur. Przed wykonaniem połączenia należy sprawdzić wymagany profil i średnicę złączki.',
            'questions' => [
                'Jakie matryce U i TH są w zestawie?' => 'Zestaw obejmuje U16, U20, U25, U32 oraz TH16, TH20, TH26 i TH32. Sama zgodność średnicy nie wystarczy: profil zacisku musi odpowiadać wymaganiom producenta złączki.',
                'Czy zaciskarka pasuje do każdej złączki PEX?' => 'Nie. Różne systemy instalacyjne wymagają określonego profilu matrycy i sposobu zaprasowywania. Przed wynajmem sprawdź oznaczenie złączek i zalecenia producenta.',
                'Jakie akcesoria są dołączone?' => 'Według oferty oprócz zaciskarki dostępne są kalibrator i gratownik, sprężyny do gięcia oraz nożyce do rur PEX. Przy odbiorze sprawdź kompletność i potrzebne rozmiary.',
            ],
        ],
        'norton-tr202' => [
            'title' => 'Przecinarka do płytek na mokro Norton Clipper TR202 – wynajem Kraków',
            'intro' => 'Norton Clipper TR202 to przecinarka stolikowa do odpowiednich płytek ceramicznych, umożliwiająca cięcie z chłodzeniem wodnym. Ma silnik 900 W, tarczę 200 mm i maksymalną długość cięcia 650 mm. Głowica pozwala na cięcia pod kątem do 45°.',
            'questions' => [
                'Jak długie płytki można ciąć na TR202?' => 'Maksymalna długość cięcia podana w ofercie to 650 mm. Przy większych formatach lub nietypowych cięciach sprawdź wcześniej, czy parametry maszyny odpowiadają materiałowi.',
                'Czy przecinarka umożliwia cięcie pod kątem 45 stopni?' => 'Tak, głowicę można pochylić do 45°. W ofercie podano maksymalną głębokość cięcia 35 mm przy 90° i 25 mm przy 45°.',
                'Dlaczego stosuje się cięcie na mokro?' => 'Woda pomaga chłodzić tarczę i ograniczać część pyłu podczas cięcia. Używaj wyłącznie właściwej tarczy do materiału i przestrzegaj zasad bezpieczeństwa elektrycznego.',
            ],
        ],
        'cedrus-wr01-h' => [
            'title' => 'Wertykulator spalinowy Cedrus WR01-H 40 cm – wynajem Kraków',
            'intro' => 'Wertykulator Cedrus WR01-H 2w1 o silniku 6,5 KM jest przeznaczony do sezonowej pielęgnacji trawnika. Ma szerokość pracy 40 cm, regulację głębokości i kosz o pojemności 45 l. Pomaga usuwać filc i mech z odpowiednio przygotowanej darni.',
            'questions' => [
                'Kiedy warto wykonać wertykulację trawnika?' => 'Wertykulację wykonuje się na trawniku w odpowiedniej kondycji i porze sezonu, kiedy murawa może się zregenerować. Unikaj zbyt agresywnej pracy na młodej, przesuszonej lub osłabionej trawie.',
                'Jak szeroko pracuje wertykulator Cedrus?' => 'Szerokość robocza wynosi 40 cm, a głębokość można regulować. Ustawienie dobierz ostrożnie do stanu murawy i nie zagłębiaj ostrzy nadmiernie w podłoże.',
                'Czy urządzenie ma kosz na zebrany materiał?' => 'Tak, według oferty kosz ma 45 l. Po zabiegu może być konieczne dodatkowe wygrabienie trawnika i zebranie filcu.',
            ],
        ],
        'dewalt-dwe4357' => [
            'title' => 'Szlifierka DeWALT DWE4357 125 mm do betonu – wynajem Kraków',
            'intro' => 'DeWALT DWE4357 to szlifierka kątowa 125 mm z silnikiem 1700 W i regulacją obrotów. W ofercie są osłony do pracy przy cięciu i szlifowaniu betonu. To narzędzie ręczne przydatne m.in. przy obróbce krawędzi oraz mniejszych fragmentów powierzchni.',
            'questions' => [
                'Czy można wynająć szlifierkę z osłoną przeciwpyłową?' => 'Tak, oferta wymienia osłonę do szlifowania betonu i osłonę do cięcia betonu. Dopasuj tarczę, osłonę i odciąg do rodzaju pracy; dostępność akcesoriów potwierdź przy rezerwacji.',
                'Do jakiej wielkości tarcz jest DeWALT DWE4357?' => 'Urządzenie pracuje z tarczami 125 mm i pozwala regulować prędkość w zakresie podanym w ofercie: 2200–10 500 obr./min. Stosuj tylko kompatybilny osprzęt o odpowiedniej dopuszczalnej prędkości.',
                'Czy można dobrać odkurzacz do szlifowania?' => 'Oferta przewiduje możliwość wynajmu zestawu z odkurzaczem Bosch GAS 25 L. Zanim rozpoczniesz szlifowanie, potwierdź zgodność złącza i skuteczność odciągu dla danego pyłu.',
            ],
        ],
        'odkurzacz-przemyslowy' => [
            'title' => 'Odkurzacz przemysłowy Bosch GAS 25 L SFC / Metabo – wynajem Kraków',
            'intro' => 'Odkurzacz przemysłowy Bosch GAS 25 L SFC lub Metabo ASR 25L SC sprawdzi się przy pracach remontowych i porządkowych oraz w zestawie z kompatybilnymi elektronarzędziami. Oferta obejmuje worek z włókniny i urządzenia wyposażone w gniazdo do elektronarzędzia.',
            'questions' => [
                'Czy odkurzacz uruchamia się razem z elektronarzędziem?' => 'W ofercie podano gniazdo do podłączenia elektronarzędzia i funkcję automatycznego uruchamiania. Przed podłączeniem sprawdź maksymalną dopuszczalną moc narzędzia oraz zgodność przewodu i węża.',
                'Czy odkurzacz nadaje się do szlifowania gładzi lub betonu?' => 'Może współpracować z odpowiednią szlifierką, ale dobór urządzenia, klasy pyłowej i filtracji musi odpowiadać rodzajowi pyłu oraz przepisom bezpieczeństwa. Ustal kompatybilność zestawu przed wynajmem.',
                'Czy worek jest w zestawie?' => 'Według oferty worek z włókniny jest dodawany bezpłatnie. Przy odbiorze sprawdź stan filtra i wymagany sposób użytkowania odkurzacza.',
            ],
        ],
        'dewalt-d25810k' => [
            'title' => 'Młot kujący DeWALT D25810K SDS-Max 7,1 J – wynajem Kraków',
            'intro' => 'DeWALT D25810K to młot udarowo-kujący SDS-Max o energii udaru 7,1 J. Przydaje się do skuwania płytek, przygotowania bruzd i lżejszych prac rozbiórkowych w odpowiednim podłożu. Waży około 5,6 kg, a zestaw obejmuje dłuto płaskie 25/400 mm.',
            'questions' => [
                'Czy młot nadaje się do skuwania płytek?' => 'Tak, oferta wymienia skuwanie płytek i dłutowanie jako przykładowe zastosowania. Dostosuj sposób pracy do podłoża i sprawdź przebieg instalacji ukrytych w ścianie lub podłodze.',
                'Czy młot DeWALT D25810K służy do wiercenia?' => 'Oferta dotyczy młota do kucia z uchwytem SDS-Max. Przed wynajmem sprawdź, czy potrzebujesz kucia, czy urządzenia z funkcją wiercenia — to różne zadania.',
                'Co jest w zestawie?' => 'Według oferty młot jest wydawany z walizką i dłutem płaskim 25/400 mm. Wybór innego osprzętu należy uzgodnić przed odbiorem.',
            ],
        ],
        'dewalt-d25911k' => [
            'title' => 'Młot wyburzeniowy DeWALT D25911K 27 J – wynajem Kraków',
            'intro' => 'DeWALT D25911K-QS to cięższy młot wyburzeniowy z uchwytem SDS-Max i energią udaru do 27 J. Jest przeznaczony do intensywniejszych prac związanych z kuciem i usuwaniem odpowiednich fragmentów betonu lub muru. Waży około 12,7 kg.',
            'questions' => [
                'Kiedy wybrać młot 27 J zamiast 7,1 J?' => 'Młot 27 J jest większy i przeznaczony do cięższych zadań wyburzeniowych, natomiast model 7,1 J może być wygodniejszy przy lżejszym dłutowaniu. Dobierz sprzęt do materiału, czasu pracy i możliwości operatora.',
                'Czy tym młotem można skuwać beton?' => 'Tak, urządzenie jest oferowane do prac związanych z kuciem odpowiednich elementów betonowych. Nie usuwaj elementów konstrukcyjnych bez wcześniejszej oceny ich roli i bezpieczeństwa.',
                'Co warto wiedzieć przed wynajmem?' => 'Masa około 12,7 kg i silny udar wymagają zaplanowania pracy oraz ochrony słuchu, wzroku i dróg oddechowych. Sprawdź instalacje w miejscu kucia i dobierz właściwe dłuto SDS-Max.',
            ],
        ],
        'ulsonix-ozonator' => [
            'title' => 'Ozonator ULSONIX 20 000 mg/h z UV – wynajem Kraków',
            'intro' => 'Generator ozonu ULSONIX ma deklarowaną wydajność 20 000 mg/h, lampę UV i tryby automatyczne. Przed wynajmem oceń, czy ozonowanie jest właściwą metodą dla konkretnego problemu. Ozon w stężeniach używanych do zabiegów może być niebezpieczny dla ludzi i zwierząt.',
            'questions' => [
                'Czy podczas ozonowania można przebywać w pomieszczeniu?' => 'Nie. Ludzie i zwierzęta nie mogą przebywać w pomieszczeniu podczas pracy urządzenia. Stosuj instrukcję producenta, zabezpiecz pomieszczenie i zapewnij wymagany czas przed ponownym wejściem oraz odpowiednie wietrzenie.',
                'Czy ozonowanie zastępuje sprzątanie i usunięcie źródła zapachu?' => 'Nie. W pierwszej kolejności usuń źródło problemu i wykonaj zwykłe czyszczenie. Nie należy zakładać, że sam ozonator usunie każdą przyczynę nieprzyjemnego zapachu lub zapewni dezynfekcję.',
                'Jakie funkcje ma oferowany ozonator?' => 'W ofercie podano wydajność 20 000 mg/h, lampę UV, tryby AUTO i wyświetlacz LCD. Sposób użycia oraz ograniczenia zależą od instrukcji konkretnego urządzenia.',
            ],
        ],
        'bosch-gcm-305' => [
            'title' => 'Ukośnica Bosch GCM 305-216 D – wynajem Kraków',
            'intro' => 'Ukośnica Bosch GCM 305-216 D służy do dokładnych cięć poprzecznych i pod kątem w odpowiednich elementach drewnianych, listwach i panelach. Ma funkcję Dual Bevel, laser i tarczę 216 mm. W ofercie podano zakres cięcia do 66 × 305 mm w określonej konfiguracji.',
            'questions' => [
                'Do jakich prac przydaje się ukośnica Bosch?' => 'Jest przydatna przy docinaniu listew przypodłogowych, paneli oraz elementów drewnianych. Upewnij się, że tarcza jest właściwa do materiału i nie przekraczaj możliwości cięcia urządzenia.',
                'Czy ukośnica umożliwia cięcie pod kątem?' => 'Tak, model oferuje regulację kątów oraz funkcję podwójnego odchylania głowicy Dual Bevel. Przed cięciem zablokuj ustawienia i prawidłowo podeprzyj materiał.',
                'Czy można dobrać odkurzacz?' => 'W opisie oferty przewidziano możliwość wynajęcia ukośnicy z odkurzaczem Bosch GAS 25 L. Przy rezerwacji potwierdź dostępność zestawu i kompatybilność odsysania.',
            ],
        ],
        'karcher-hd720' => [
            'title' => 'Myjka ciśnieniowa Kärcher HD 7/20 G Classic – wynajem Kraków',
            'intro' => 'Spalinowa myjka wysokociśnieniowa Kärcher HD 7/20 G Classic służy do czyszczenia odpowiednich, odpornych na wodę powierzchni. Oferuje ciśnienie robocze 200 bar, wydajność do 700 l/h i wąż o długości 10 m. Sprawdza się tam, gdzie nie ma wygodnego dostępu do zasilania elektrycznego.',
            'questions' => [
                'Czy myjka może pobierać wodę z beczki?' => 'Nie. W ofercie wyraźnie podano brak funkcji zasysania wody z alternatywnego źródła. Zapewnij dopływ wody zgodnie z wymaganiami urządzenia.',
                'Czy myjka nadaje się do czyszczenia kostki brukowej?' => 'Może służyć do czyszczenia wybranych powierzchni zewnętrznych, w tym odpowiedniej kostki. Przed pracą dobierz ciśnienie i dyszę, aby nie uszkodzić nawierzchni i fug.',
                'Czy można dobrać przystawkę do mycia kostki?' => 'W ofercie jest opcja wynajmu przystawki Kärcher FR 30 Me o średnicy 30 cm. Sprawdź aktualne warunki zestawu i dostępność przy rezerwacji.',
            ],
        ],
    ];
}
