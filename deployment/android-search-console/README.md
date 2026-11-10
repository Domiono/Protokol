# Android Google / SEO

Android1.7.30/code45, PR152: natywny widok współdzielonych raportów Search Console. KonfiguracjaOAuth pozostaje wWWW; urządzenie nie otrzymuje tokenówGoogle ani sekretu klienta.

Walidacja: pełne testy Androida, API na fikcyjnych danych, cztery testy transakcji na fałszywymAPK. Podpisanyrelease musi mieć ten sam certyfikat co dotychczasowy publicznyAPK1.7.29/code44 i poprawną wersję/pakiet w aapt.

Publikacja używa przypiętego kluczaSSH; SFTP poprzednio zawodziło. APK jest przesyłany przezSSH do prywatnego pliku0600. Preflight sprawdzaSHA staregoAPK, blobSHA aktualnego protocol_sync.php, współdzielony helper i obie prywatne kopie dokumentacji. Dodawany jest wyłącznie hookSearchConsole do aktualnegoAPI, bez nadpisywania nowszych funkcji. Prywatne kopie, blokada, atomowe podmiany i pełny odczytzwrotny. Publicznydownload musi zgadzać się bajtowo, zwracać no-store i nazwęRentMor-1.7.30.apk. NiezalogowaneAPI401, bezpośrednihelper404. Brak testów na rzeczywistych danych i brak zapytańGoogle.

baseline.json dotyczy wyłącznie pierwszego wdrożenia. Po sukcesie starymanifest nie nadaje się do ponownego uruchomienia. Końcowa synchronizacja dokumentów wymaga osobnego progress-baseline.json i trybu --progress-only; ten tryb nie odczytujeAPK aniAPI.

Tymczasowyworkflow podpisania i publikacji pozostaje poza docelowymPR; po zakończeniu jest usuwany. Zwykłe workflow zachowują politykętest-only.
