# Pobieranie aktualnej aplikacji

PR149, Android1.7.28/code43, publikacja42830958339 SUCCESS. Przycisk korzysta z download-app.php z parametrem czasu i pokazuje zainstalowaną wersję. Endpoint nie otwiera sesji ani danych prywatnych: odczytuje wyłącznie RentMor.apk i publiczny app-release.json.

Przy kolejnych wydaniach trzeba aktualizować łącznie APK oraz metadane version/version_code/size/sha256. Niezgodność manifestu zAPK celowo zwraca503, zamiast pobrania niewłaściwej wersji. Endpoint wysyła no-store oraz nazwę pliku z wersją. Metadane powstały w runnerze po podpisaniu i potwierdzeniu wersji/podpisu. Nie kopiować stałego staregoAPK ani staregoSHA do nowego wydania.

publish.py jest historycznym, ściśle przypiętym do poprzedniego SHA procesem tej jednej publikacji. Nie uruchamiać go ponownie na zmienionym serwerze. Tymczasowe aktywne workflow usuwa się po zakończeniu. Testy endpointu korzystają wyłącznie z fikcyjnegoAPK w katalogu tymczasowym i loopbackPHP.

Stare instalacje1.7.25 mają staryprzycisk i wymagają jednorazowego otwarcia https://rentmor.pl/download-app.php?release=43 oraz zainstalowania pobranego pliku jako aktualizacji. Nie usuwać danych ani nie odinstalowywać aplikacji. Ostateczne postępy: docs/CONTINUE-RENTMOR.md i docs/RENTMOR-PROJECT-LOG.md.
