# Mortii — grafika bezpośrednio w głównym czacie

Status: OPUBLIKOWANE i sprawdzone w Chromium z emulacją Androida390×844 na produkcji (run42784823755 SUCCESS), a także w interaktywnej przeglądarce.

Przyczyna starego przycisku: mortii.js tworzył tekstowy launcher, a osobny dekorator rentmor-ai-mortii-visual.js/css zwracał HTTP403. Potwierdzono uprawnienia0600 obu dekoratorów. Zwiększanie numeru cache nie naprawiało braku dostępu. W pomiarze przed zmianą stary przycisk miał230×61.59px na home i kategorii; brak błędu składniJS nie oznaczał poprawnej grafiki.

Rozwiązanie: produkcyjny mortii.js bezpośrednio tworzy obrazkowy przycisk i awatary nagłówka/odpowiedzi. mortii.css ustawia koło62×62px, ukrywane podczas otwartej rozmowy i przywracane po zamknięciu. Usunięto odwołania do dekoratora z public_metadata.php/category.php/polityka-prywatnosci.php; rental_categories.php otrzymał tylko nowe wersje zasobów. Brak dodatkowej nakładki, obserwatoraDOM czy przerabiania przycisku po jego utworzeniu.

Zasoby: /mortii.js?v=6, /mortii.css?v=3 oraz dwa małe WebP w assets z nazwami zawierającymi skrótySHA256. Publiczne statyczne pliki0644, prywatne backupy0600/katalog0700. Najnowsze źródła i ścisły manifest poprzednich wersji: repair/. Manifest nie pozwala bezrefleksyjnie powtórzyć wdrożenia na już zmienionym serwerze.

Zakres: wyłącznie prezentacja. Funkcja booking i wszystko za nią pozostały bajtowo zgodne z odczytanym aktualnym produkcyjnymJS; API, klientowskie dane, reguły rezerwacji i SMS nie były zmieniane. Nie publikowano całego przestarzałego main.

Walidacja: 17lokalnych DOMPASS; pełny readback6istniejącychplików i2nowychobrazów, publiczneHTTP200+zgodnośćbajtowa. Produkcyjne strony /, /sprzet-budowlany/, /szlifierka-katowa-230mm-bosch-professional-gws-2200/, /polityka-prywatnosci.php: koło62×62, załadowanyobraz, noweURLcache, brakstaregodekoratora, otwieranie/ukrywanie/zamykanie i brakpageerrors. Zrzuty iJSON w artefakcie13748920706. Pierwsza próba automatycznie przywróciła stare pliki, gdy panelcookies uniemożliwił kliknięcie; późniejszy test korzysta z normalnego przycisku „Tylko niezbędne”. Nie używa forceclick ani omijania interfejsu. Brak płatnychzapytańAI i rzeczywistychzgłoszeń.

Kolejne prace: czytać docs/CONTINUE-RENTMOR.md oraz centralny dziennik, przechowywać identyczne kopie na serwerze. Nie przywracać nieaktywnego dekoratora ani starego mortii.js z innej gałęzi. Fizyczny telefon właściciela nie był zdalnie sterowany — testmobilny to rzeczywistyChromium w emulacjiAndroida.
