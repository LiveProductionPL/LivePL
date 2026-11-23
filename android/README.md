# LivePL na Androida

Trzy aplikacje w Kotlinie i Jetpack Compose (Material 3) z Android System WebView, czyli silnikiem Chrome,
z jednego kodu (warianty `przegladarka`, `poczta` i `connect`):

- **LivePL** (`eu.livepl.przegladarka`): przeglądarka, która wygląda jak „Reżyserka” z LivePL na Windows
  i działa jak [LivePL na iPhone'a](../ios/README.md);
- **Poczta LivePL** (`eu.livepl.poczta`): firmowa poczta na cały ekran (opis niżej);
- **LivePL Connect** (`eu.livepl.connect`, ta sama nazwa pod ikoną): zdalny pulpit Live Production na cały ekran
  (opis niżej).

Obok, w osobnym module [tvml](tvml), jest natywne **TVML** (`pl.tvml`):
serwis informacyjny tvml.pl, jak TVML na iPhonie (opis niżej).

Przeglądarka LivePL:

- **Start** to ekran startowy. W nagłówku jest Wstęga, a obok niej „LivePL” i „Przeglądarka” (razem nie wyżej
  niż logo) oraz przycisk ⋮ menu przeglądarki.
  Niżej: pole „Adres albo szukaj”, kafelki programów, otwarte karty, Moje strony i przełącznik dzień/noc przy zegarze.
- **Menu przeglądarki** (⋮ na ekranie Start, a na otwartej stronie podmenu „Przeglądarka” w menu ⋮) ma:
  Nowa karta, Otwarte karty, Dodaj stronę do Moich stron, Wygląd (automatycznie / dzień / noc),
  Certyfikat LivePL dla telefonu, Urządzenie LivePL (stan rejestracji w urzędzie), O programie (wersja, strona LivePL, kod źródłowy na licencji MIT,
  © 2026 Live Production · Karol Kowalski) i „Wyczyść dane przeglądania…”. Czyszczenie, po potwierdzeniu,
  zamyka karty i usuwa ciasteczka, dane stron i pamięć podręczną; Moje strony, wygląd i słowniki tłumacza zostają.
- **Dolny pasek** działa jak pasek po lewej na Windows. Ma same ikony: Start (Wstęga), programy (kropka oznacza, że program
  jest otwarty) i licznik kart. Licznik otwiera listę kart: zamykanie, „Nowa karta” i „Zamknij wszystkie”.
  Każdy program ma jedną własną kartę. Przytrzymanie ikony pokazuje jej nazwę, a przy otwartym programie
  menu „Zamknij”. Nazwy ikon zna też czytnik ekranu (TalkBack).
- **Karta strony** ma u góry wstecz, adres z kłódką, odśwież i menu. Stuknięcie w adres pozwala wpisać inny.
  W menu są: dalej, udostępnij, znajdź na stronie, przetłumacz z Google, dodaj do Moich stron, kopiuj adres,
  otwórz w przeglądarce, podmenu „Przeglądarka” i zamknij. Odświeżasz też, przeciągając stronę w dół. Gdy strona się nie otworzy,
  LivePL mówi po polsku dlaczego i pokazuje „Spróbuj ponownie”.
- **Tłumaczenie stron** robi Tłumacz Google w telefonie ([ML Kit](https://developers.google.com/ml-kit/language/translation)),
  więc tekst strony nigdzie nie wychodzi. Gdy strona jest w obcym języku, pod adresem pojawia się
  „Język strony: … · Przetłumacz z Google”. Słownik danego języka (ok. 30 MB) pobiera się raz, przy pierwszym
  tłumaczeniu. Najpierw tłumaczy się to, co widać na ekranie, a nowe treści (np. lista w poczcie) na bieżąco.
  „Oryginał” przywraca stronę. Kolejne strony w tym samym języku tłumaczą się w tej karcie same.
  Tłumaczenie idzie fragment po fragmencie, więc bywa mniej gładkie niż na translate.google.com.
  Pasek zawsze mówi, że to tłumaczenie Google: tego wymagają
  [zasady ML Kit](https://developers.google.com/ml-kit/language/translation/translation-terms), które odsyłają do
  [wymagań Google Cloud Translation](https://cloud.google.com/translate/attribution) (tam też jest pełne
  zastrzeżenie o tłumaczeniach maszynowych).
- **Systemowy przycisk Wstecz** cofa stronę. Na początku historii wraca do ekranu Start, a okno otwarte przez
  stronę zamyka się i wraca do strony, która je otworzyła.
- **Okna stron** (`target=_blank`, `window.open`) otwierają się jako nowe karty. Linki `mailto:`, `tel:`
  i `intent:` otwiera Android. Pobrane pliki trafiają do folderu Pobrane (także pliki `blob:` i `data:`).
  Pola na pliki w formularzach otwierają wybór plików (z aparatem, gdy strona chce zdjęcia).
  Gdy strona prosi o aparat, mikrofon albo lokalizację, LivePL najpierw pyta, a potem prosi o zgodę Androida.
  `alert`, `confirm` i `prompt` pokazują się jako okna systemowe. Filmy można oglądać na pełnym ekranie.
- **Karty wracają** po ponownym uruchomieniu, ale ładują się dopiero wtedy, gdy na nie wejdziesz.
  Logowania (ciasteczka) i Moje strony zostają w telefonie. Do kopii zapasowej Androida idą tylko
  ustawienia i Moje strony, bez logowań.
- **Dzień i noc** obejmuje też strony: dostają `prefers-color-scheme` jak w LivePL na Windows.
  Strony bez trybu nocnego zostają jasne (bez sztucznego przyciemniania).
- **Linki z innych aplikacji** może otwierać LivePL. Można go też ustawić jako przeglądarkę domyślną.
- **Certyfikaty LivePL:** aplikacja sama ufa urzędowi certyfikacji LivePL (LivePL Root CA), ale tylko na naszych
  domenach, jak LivePL na Windows (`src/nasze-ca.js`) i na iPhonie. Root z [assets/ca](../assets/ca) i lista domen
  z [assets/ca/domeny.json](../assets/ca/domeny.json) trafiają przy budowie do konfiguracji bezpieczeństwa sieci
  Androida (`res/xml/siec.xml`, z niej korzysta też WebView). Android sam sprawdza cały łańcuch: podpisy, daty,
  nazwę strony i przeznaczenie certyfikatu. Na innych stronach obowiązują tylko certyfikaty systemowe, a zły
  certyfikat zawsze kończy się komunikatem, bez „otwórz mimo to”. Instalować certyfikat w telefonie trzeba tylko
  dla innych aplikacji; menu przeglądarki prowadzi do instrukcji na cert.livepl.eu.
- **Urządzenie LivePL:** przy pierwszym uruchomieniu aplikacja sama, bez pytania, rejestruje telefon w urzędzie
  LivePL, jak `src/urzadzenie.js` na Windows i LivePL na iPhonie. Klucz EC P-256 powstaje w Android Keystore
  (StrongBox, jeśli telefon go ma, inaczej TEE) i nie da się go skopiować. Wniosek PKCS#10 idzie do
  `https://cert.livepl.eu/urzadzenia/wniosek` (nazwa „Android-KOD”, model i wersja Androida, wersja LivePL,
  `tpm` = klucz sprzętowy), urząd od razu wystawia certyfikat, a aplikacja sprawdza, że pasuje do klucza
  i prowadzi do roota urzędu. Potem `/urzadzenia/stan/<id>` i raport `/urzadzenia/raport/<id>` co 6 godzin.
  Certyfikat razem z kluczem pokazuje się stronom z naszych domen, gdy proszą o certyfikat klienta.
  Każda aplikacja ma własny klucz, więc Poczta LivePL zgłasza się jako „Android-KOD Poczta”, LivePL Connect jako
  „Android-KOD Connect”, a wersje testowe
  z dopiskiem „test”. Emulator (zrzuty na GitHubie) do prawdziwego urzędu się nie zgłasza.
- **Propozycja Poczty LivePL:** gdy w przeglądarce otwarta jest Poczta, a aplikacji Poczta LivePL nie ma na
  telefonie, pod adresem pojawia się „Poczta ma własną aplikację” z przyciskiem „Zainstaluj” (Sklep Google Play,
  a bez niego strona Sklepu) i „×”, które chowa propozycję na 30 dni. Gdy Poczta LivePL jest zainstalowana, menu ⋮
  karty Poczty ma „Otwórz w aplikacji Poczta”. Dopóki Poczty LivePL nie ma w Sklepie, strona Sklepu jest pusta.
- **Identyfikator przeglądarki** (User-Agent) to ten z Chrome z dopiskiem `LivePL/<wersja>` na końcu, więc nasze
  strony wiedzą, że są w aplikacji LivePL (np. poczta nie proponuje dodania się do ekranu).

Lista programów to ten sam [programy.json](../programy.json) co na Windows i iPhonie. Programy z `"ukryty": true`
są pod „Więcej programów”. Tam też jest panel urzędu (`tylkoZYubiKey`), którego nie ma na dolnym pasku: na telefonie
otwiera stronę publiczną cert.livepl.eu, bo logowanie YubiKey działa tylko w LivePL na Windows.

Wymagania: Android 8.0 lub nowszy. Wersje testowe z GitHuba mają identyfikatory z `.debug` na końcu i nazwy
„LivePL test”, „Poczta test” i „Connect test”, więc mogą stać obok wydań.

## Poczta LivePL

Osobna aplikacja z tym samym silnikiem: poczta.livepl.eu na cały ekran, bez paska adresu, jak Poczta LivePL na iPhonie
(`ios/Poczta/PocztaApp.swift`).

- **Ikona:** logo poczty (L i koperta z [assets/programy/poczta.svg](../assets/programy/poczta.svg)) na granacie
  #071c2d, takim jak nagłówek poczty, z warstwą jednobarwną do ikon motywowych Androida 13+.
- **Cały ekran:** pasek stanu ma jasne ikony na granacie. Od Android System WebView 140 strona idzie pod paski
  i sama robi miejsce (`env(safe-area-inset-*)`); na starszym WebView aplikacja sama zostawia miejsce na paski.
- **Tylko poczta:** w aplikacji otwiera się wyłącznie poczta.livepl.eu. Kliknięty link do innej strony
  (także z `target=_blank`) otwiera domyślna przeglądarka telefonu.
- **Okna poczty** (podgląd załącznika, drugie konto) otwierają się nad pocztą z przyciskami „Udostępnij” i „Gotowe”.
- Załączniki pobierają się do Pobranych, a do wiadomości można dołączyć plik albo zdjęcie z aparatu.
- **Bez tłumacza:** poczta jest po polsku, więc Poczta LivePL nie ma bibliotek ML Kit i waży ok. 1 MB zamiast
  ok. 15 MB. Silnik tłumaczenia jest osobno w każdym wariancie (`SilnikTlumaczenia.kt` w `app/src/przegladarka`
  i pusty w `app/src/jednaStrona`).
  Poczta nie prosi o lokalizację ani mikrofon.
- **Logowanie trzyma 90 dni:** serwer poczty przedłuża je, gdy w identyfikatorze przeglądarki widzi „LivePL/”
  i „Android”, a aplikacja zapisuje ciasteczka na stałe przy każdym wyjściu do tła.
- **Nieprzeczytane:** liczba z tytułu poczty (np. „(12) Poczta” albo „12 nieprzeczytanych”) jest w nazwie okna
  na liście ostatnich aplikacji. Android nie ma liczby na ikonie bez powiadomień, więc na ikonie jej nie ma.
- Systemowy Wstecz zamyka okno poczty, cofa stronę, a na początku historii chowa aplikację.

Ekran aplikacji jednej strony jest wspólny dla Poczty LivePL i LivePL Connect
(`app/src/jednaStrona/java/.../JednaStronaActivity.kt`); każda z nich ustawia tylko kolor, adres i zachowania.

## LivePL Connect

Zdalny pulpit Live Production (connect.livepl.eu) jako osobna aplikacja, jak LivePL Connect na iPhonie. Łączy
się z komputerem z programem LivePL Connect po ID i haśle; obraz i sterowanie płyną przez WebRTC wprost między
urządzeniami. Całe sterowanie robi strona, aplikacja dokłada to, czego strona sama nie umie:

- **Ikona:** ekran z czerwoną strzałką kursora, przerysowana wektorowo z ikony programu na Windows
  (`LivePL-RMM/connect/server/web/ikona-512.png`), na grafitowym tle #15181d, z warstwą jednobarwną.
- **Cały ekran** na ciemnym tle #0d0f12, jak góra strony, w dzień i w nocy.
- **Ekran nie gaśnie w trakcie sesji:** WebView nie ma `navigator.wakeLock`, więc aplikacja podkłada go stronie
  (tylko na connect.livepl.eu). Gdy strona prosi o blokadę wygaszania, aplikacja włącza `FLAG_KEEP_SCREEN_ON`,
  a zdejmuje ją, gdy strona ją zwalnia, przechodzi na inny adres albo aplikacja idzie do tła.
- **Przytrzymanie palca to prawy przycisk** komputera: aplikacja nie zaznacza wtedy tekstu i nie pokazuje
  podglądu linku, a strona sama rozpoznaje przytrzymanie, wysyła prawy klik i wibruje (`navigator.vibrate`,
  uprawnienie VIBRATE; jak w Chrome, wibracja działa po pierwszym dotknięciu strony).
- **Bez odświeżania przeciągnięciem**, bo przeciąganie jednym palcem przewija ekran komputera.
- **Systemowy Wstecz kończy sesję:** strona zapisuje sesję w historii (`history.pushState`), więc cofnięcie
  wraca do ekranu połączeń; na nim Wstecz chowa aplikację.
- Bez aparatu, mikrofonu i lokalizacji. Bez tłumacza (jak Poczta LivePL), ok. 1 MB.

## TVML

Serwis informacyjny Telewizji TVML (tvml.pl) natywnie w Compose, bez WebView, przeniesiony z TVML na iPhonie
(gałąź `ios`, folder `ios/TVML`) według projektu zatwierdzonego przez Karola. Pod ikoną i w Sklepie samo „TVML”;
wersja 2.0 jak na iPhonie i w API (`minAppVersion.android`). Kod: [tvml/src/main/java/pl/tvml](tvml/src/main/java/pl/tvml).

- **Wszystko z panelu tvml.pl** przez API dla aplikacji ([tvml.pl/api/v1](https://tvml.pl/api/v1/config), zapasowo
  `tvml-app.php?route=`, a bez API stare akcje strony): zakładki z `config.tabs` (`home`, `video`, `weather`,
  `more`, `subportal:slug`; „Więcej” jest zawsze), bloki Startu z `config.home` w tej kolejności (nieznane typy,
  np. ogłoszenia, aplikacja pomija), wersja wyglądu i jej kolory z `config.theme` (API 1.1), pasek pod banerem
  z `config.ticker`, imieniny z `config.today`, gminy, subportale i strony redakcji (`config.pages`).
  Odpowiedzi z ETag wracają jako 304, a bez internetu aplikacja pokazuje ostatnie pobrane dane.
- **Bez artykułów sponsorowanych** (Karol, 22.11.2026): aplikacja pomija bloki z kategorią `artykuly-sponsorowane`
  albo `props.sponsored` i artykuły z `flags.sponsored` albo z tej kategorii (serwer od API 1.7 też je ukrywa).
- **Pasek pod banerem:** etykieta z `config.ticker.label` (np. „NAJNOWSZE”), kropka i „NA ŻYWO” tylko przy
  `config.ticker.live`; każda wiadomość otwiera swój artykuł.
- **Sonda albo kalendarz wydarzeń** (blok `poll`, `/api/v1/spotlight`): trwająca sonda z wyborem i „Zagłosuj”,
  wyniki dopiero po głosie, głos zapamiętany tylko w telefonie (bez identyfikatorów); bez sondy kalendarz miesiąca
  z kropkami w dniach z wydarzeniami i listą wybranego dnia (domyślnie dziś, `/api/v1/events?from=D&to=D`).
  Wydarzenie otwiera się w aplikacji (`/api/v1/events/{id}`: opis, organizator, bilety), a z artykułem: artykuł.
  Osobny blok `events` aplikacja pokazuje tylko bez bloku `poll`.
- **Więcej:** podstrony z `config.pages` w sekcjach „TVML” i „Dokumenty” (pole `section`, API 1.7.1) otwierają się
  w aplikacji: wersja dla aplikacji (`appUrl`, `&motyw=jasny|ciemny`), a zapasowo treść z `/api/v1/pages/<slug>`
  w kolorach aplikacji; WebView bez JavaScriptu, linki do podstron i artykułów w aplikacji, reszta w systemie.
  Strony w budowie się nie pokazują (`ready`, `app`; dla starszych odpowiedzi: tekst krótszy niż 40 znaków
  i `mapa-strony`). Ogłoszenia drobne i Fotorelacje natywnie, tylko gdy coś w nich jest. Stopka
  z `config.about.footer`.
- **Subportale** (Sport, Muzyka, Kultura, Na sygnale) czyta przez kategorię subportalu z panelu
  (`config.subportals[].category`, pusta = nazwa subportalu): `muzyka` to `muzyka-tvml` (`?category=muzyka`
  jest puste), a `na-sygnale` nie ma w panelu kategorii ani tagu (`?subportal=na-sygnale` jest puste).
- **Baner nad każdym ekranem:** logo z ozdobą wersji (bałwanek, czapka Mikołaja, gwiazda, flaga, „2027”),
  pogoda, szukaj, pasek życzeń z odliczaniem, śnieg albo iskry, przewijany pasek „NA ŻYWO”.
- **Artykuł:** „Wróć”, rozmiar tekstu (trzy), udostępnianie, pasek postępu czytania; „Odsłuchaj” czyta polskim
  głosem telefonu (silnik mowy Androida), „Zapisz” i „Komentarze” po prawej, tagi na końcu. Filmy z treści
  i z kanału grają w aplikacji (youtube-nocookie na stronie osadzającej z adresem tvml.pl i z parametrem `origin`,
  bo bez strony osadzającej YouTube pokazuje „Błąd 153”), z pełnym ekranem i „Otwórz w YouTube”.
- **Pogoda:** scena z leszczyńskim Ratuszem rysowana natywnie (stan ze stacji, pora dnia, pora roku, wersja),
  teraz, 24 godziny i 14 dni.
- **Ikona zimowa i świąteczna** (`config.theme.appIcon`: `winter`, `xmas`, `default`): trzy `activity-alias`
  w manifeście, zawsze dokładnie jeden włączony ([IkonaAplikacji.kt](tvml/src/main/java/pl/tvml/IkonaAplikacji.kt)).
  Zmiana idzie, gdy aplikacja schodzi z ekranu. Na Pixel Launcherze (emulator, Android 16) ikona na ekranie
  głównym zostaje i tylko zmienia wygląd; niektóre inne launchery mogą ją przy zmianie zdjąć z ekranu głównego.
  Ikony powstają z ikon TVML na iPhonie (`npx electron android/narzedzia/grafiki-tvml.js <folder z ikonami iOS>`),
  z warstwą jednobarwną do ikon motywowych.
- **Języki** (API 1.8.0): w „Więcej” → „Język”: „Jak w systemie” (ukraiński albo angielski telefon, inaczej polski),
  Polski, Українська, English, tylko języki z `config.languages`. To samo w banerze: pigułka z flagą bieżącego języka
  (vector drawable `flaga_pl`, `flaga_gb`, `flaga_ua`) rozwija wybór pozostałych. Każde zapytanie do `/api/v1` ma `lang=`, więc
  treść tłumaczy redakcja, a zapis odpowiedzi jest osobny dla każdego języka. Napisy na ekranach są w słownikach
  `config.i18n` (klucze `app.*`, te same co na stronie i w TVML na iPhonie) zapisanych w telefonie; po polsku
  i bez klucza zostaje dotychczasowy tekst polski ([Jezyk.kt](tvml/src/main/java/pl/tvml/Jezyk.kt)).
  Daty po ukraińsku i angielsku z formatów systemu.
  Tłumaczenie w przygotowaniu: jedno zdanie nad polskim tekstem; w przetłumaczonym artykule „Pokaż oryginał
  po polsku” i bez „Odsłuchaj” (lektor czyta po polsku). Zmiana języka buduje ekrany od nowa, bez restartu.
  Tłumaczenie na żądanie: otwarcie artykułu po ukraińsku albo angielsku samo zgłasza prośbę. Przy `translationEta`
  `now` (API 1.8.1) serwer tłumaczy od razu, a aplikacja pobiera artykuł co 4,5 s do 90 s, póki jest na ekranie,
  i podmienia tekst w miejscu; przy `hour` albo `morning` („będzie rano”) tylko odświeża po powrocie z tła.
  Aplikacja nie pobiera artykułów w tle: „Zapisane” jednym zapytaniem `articles?ids=` (bez próśb o tłumaczenie),
  a bez sieci ze skrótów zapisanych w telefonie.
- **Agent** `TVMLApp/2.0 (Android 16)`: tvml.pl ma dla niego wyjątek w liczniku odsłon.
- **Osobny klucz podpisu** (`tvml.keystore`), nie ten co LivePL (sekcja „Klucze podpisu”).
- **Bez śledzenia** i bez identyfikatora reklamowego; jedyne uprawnienie to internet. Na razie bez formularzy
  (zgłoszenia, komentarze, wydarzenia i ogłoszenia dodaje się na tvml.pl w karcie przeglądarki) i bez powiadomień:
  wejdą z kolejnym krokiem API (Play Integrity, FCM).
- **Linki** `https://tvml.pl/artykul/<id>` otwierają artykuł w aplikacji, gdy tvml.pl poda `assetlinks.json`
  dla pakietu (sesja TVML Connect, za zgodą Karola).
- Do zrzutów: `adb shell am start -n pl.tvml/pl.tvml.MainActivity --es ekran pogoda|artykul|wideo|wiecej|szukaj`,
  `--es motyw zima|czapeczka|wigilia|sylwester|listopad|zaloba`, `--es wyglad dzien|noc`, `--es ikona zwykla|zima|swieta`,
  `--es jezyk pl|uk|en|system` (skrypty zrzutów podają `pl`).
- Ok. 2 MB (wydanie po R8).

## Budowanie bez Android Studio

Buduje [.github/workflows/android.yml](../.github/workflows/android.yml) na Linuksie GitHuba, bez opłat dla
publicznego repozytorium. Każda zmiana w `android/` wysłana na gałąź `android` przechodzi przez te kroki:

1. **Debug APK** wszystkich czterech aplikacji i sprawdzenie kodu (lint). Wynik to artefakt `livepl-debug-N`.
2. **Zrzuty ekranu z emulatora** Androida 16 (Pixel 7): Start, Poczta i TVML w przeglądarce, Poczta LivePL
   i LivePL Connect, każde w dzień i w nocy, oraz aplikacja TVML (Start, artykuł, pogoda, wideo, zima,
   Wigilia w nocy). Wynik to artefakt `zrzuty-N` z dziennikiem emulatora.
3. **Podpisane APK i AAB**, tylko gdy są ustawione sekrety kluczy (niżej). Wynik to artefakt `livepl-wydanie-N`:
   - `LivePL-N.apk` (ok. 15 MB), `Poczta-LivePL-N.apk` i `LivePL-Connect-N.apk` (po ok. 1 MB)
     na app.livepl.eu: klucz aplikacji, tylko procesory telefonów (ARM); do tego `TVML-N.apk` (ok. 2 MB);
   - `LivePL-N.aab`, `Poczta-LivePL-N.aab`, `LivePL-Connect-N.aab` i `TVML-N.aab` do Google Play:
     klucz przesyłania, wszystkie procesory;
   - `SHA256SUMS.txt`.
4. **Wysyłka do Google Play**, tylko gdy opis zmiany zawiera `[play]` (LivePL), `[play-poczta]`
   (Poczta LivePL), `[play-connect]` (LivePL Connect) albo `[play-tvml]` (TVML) i jest ustawiony klucz konta usługi. AAB trafia do testu wewnętrznego jako szkic, a publikuje
   go człowiek w Play Console.

Numer budowy (`versionCode`) to numer przebiegu workflow, a wersja (`versionName`) jest w
[app/build.gradle.kts](app/build.gradle.kts). Artefakty pobierasz tak:

```powershell
gh run list --repo LiveProductionPL/LivePL --workflow android.yml --limit 3
gh run download <numer przebiegu> --repo LiveProductionPL/LivePL -n livepl-wydanie-<N>
```

Lokalnie wystarczą JDK 17+ i [narzędzia wiersza poleceń Androida](https://developer.android.com/studio#command-tools)
(bez Android Studio). Ustaw `JAVA_HOME` i `ANDROID_HOME`, a potem w folderze `android`: `.\gradlew.bat assembleDebug`
(wszystkie cztery aplikacje; jedna to np. `assemblePrzegladarkaDebug`, `assemblePocztaDebug`,
`assembleConnectDebug` albo `:tvml:assembleDebug`).
Wydania skraca R8 w trybie pełnym; biblioteki, które tworzą obiekty przez refleksję (ML Kit), mają reguły
w [app/proguard-rules.pro](app/proguard-rules.pro). Przed wydaniem warto sprawdzić tłumacza na podpisanym APK,
bo wersja testowa (debug) nie przechodzi przez R8.
Skrypt [narzedzia/zrzuty.sh](narzedzia/zrzuty.sh) robi zrzuty z działającego emulatora tak samo jak GitHub.
Zaufanie do urzędu LivePL sprawdzisz w budowie testowej z próbnym rootem i domeną, np.
`.\gradlew.bat assembleDebug "-Plivepl.testowyRoot=C:\test\testowy-root.crt" "-Plivepl.testoweDomeny=localhost"`
(jak `LIVEPL_DODATKOWY_ROOT` na Windows). Rejestrację urządzenia sprawdzisz z próbnym urzędem:
`"-Plivepl.adresUrzedu=http://localhost:8766"` (wtedy zgłasza się też emulator). Wydania tych opcji nie biorą pod uwagę.

Grafiki tworzą dwa skrypty, uruchamiane z katalogu `przegladarka-livepl`:

- `npx electron android/narzedzia/grafiki.js` tworzy Wstęgę (dzień i noc), ikonę adaptacyjną z warstwą
  jednobarwną do ikon motywowych, ikony programów z [assets/programy](../assets/programy) i ikonę 512 px
  i grafikę funkcji 1024×500 do Google Play ([sklep/eu.livepl.przegladarka/pl-PL/images/icon.png](sklep/eu.livepl.przegladarka/pl-PL/images/icon.png)). Wstęga pochodzi z tej samej wektorowej Wstęgi co
  na iPhonie ([ios/narzedzia/logo.js](../ios/narzedzia/logo.js)). Robi też ikonę Poczty LivePL
  (`app/src/poczta/res`) i jej grafiki do Sklepu ([sklep/eu.livepl.poczta/pl-PL/images/icon.png](sklep/eu.livepl.poczta/pl-PL/images/icon.png)),
  a także ikonę LivePL Connect (`app/src/connect/res`) i jej grafiki
  ([sklep/eu.livepl.connect/pl-PL/images/icon.png](sklep/eu.livepl.connect/pl-PL/images/icon.png)).
- `node android/narzedzia/ikony.mjs` pobiera ikony interfejsu z Material Symbols (Google, licencja Apache 2.0).
- `npx electron android/narzedzia/grafiki-tvml.js <folder z ikonami iOS>` robi trzy ikony TVML, ikonę 512 px
  i grafikę funkcji do Sklepu ([sklep/pl.tvml/pl-PL/images](sklep/pl.tvml/pl-PL/images)). Zrzuty TVML do Sklepu:
  `android/narzedzia/zrzuty-sklep.sh - - - <APK TVML>`.

## Klucze podpisu

Klucze leżą w `%USERPROFILE%\LivePL-klucze\android\`, każdy z losowym hasłem w pliku obok. Klucz aplikacji
LivePL jest wspólny dla LivePL, Poczty LivePL i LivePL Connect, TVML ma własny (inna marka; decyzja Karola),
a klucz przesyłania jest wspólny dla wszystkich czterech. Tworzy je `node android/narzedzia/zrob-klucz.mjs`
(z argumentem `przesylanie` klucz przesyłania, z `tvml` klucz TVML). Skrypt nie nadpisuje istniejących kluczy.
Wszystkie powstały 19.11.2026.

| Klucz | Pliki | Do czego | Odcisk SHA-256 certyfikatu (publiczny) |
|---|---|---|---|
| aplikacji | `livepl.keystore`, `livepl-haslo.txt` | APK z app.livepl.eu; ten sam klucz dostaje Google Play do podpisywania wersji ze Sklepu | `45:99:A9:F4:32:0C:F3:4B:5D:08:65:50:F5:9A:D3:F4:15:27:C9:A9:B0:D1:1B:F0:77:46:CE:1B:C7:FD:7C:41` |
| przesyłania | `livepl-przesylanie.keystore`, `livepl-przesylanie-haslo.txt`, `livepl-przesylanie-certyfikat.pem` | podpis AAB wysyłanego do Google Play | `F5:A6:7E:F5:1F:9B:35:68:8A:9A:6E:A8:2E:AB:AE:2E:3F:6E:43:EC:65:EB:D3:F6:99:F2:44:8D:BE:B1:74:14` |
| aplikacji TVML | `tvml.keystore`, `tvml-haslo.txt` (alias `tvml`) | TVML: APK spoza Sklepu i (przez PEPK) podpis wersji z Google Play; ten odcisk idzie do `assetlinks.json` na tvml.pl | `41:83:0B:7B:19:3C:E6:65:79:04:4A:1C:F0:88:8A:11:93:FC:A0:8D:20:3A:59:F1:A5:91:FB:79:3A:83:73:4B` |

- **Klucza aplikacji nie wolno zgubić ani zmienić.** Android instaluje aktualizację tylko wtedy, gdy ma ten sam
  podpis. Zrób kopię obu kluczy i haseł w bezpiecznym miejscu poza tym komputerem.
- Zgubiony klucz przesyłania da się wymienić przez Play Console. Klucza aplikacji nie da się wymienić.
- **Sekrety GitHuba** wpisujesz sam: `node android/narzedzia/ustaw-sekrety.mjs`. Ustawia `ANDROID_KLUCZ`,
  `ANDROID_KLUCZ_HASLO`, `ANDROID_KLUCZ_ALIAS`, `ANDROID_PRZESYLANIE`, `ANDROID_PRZESYLANIE_HASLO`
  oraz `ANDROID_KLUCZ_TVML` i `ANDROID_KLUCZ_TVML_HASLO` (bez nich nie powstaje APK TVML).
  Wartości idą prosto z plików do `gh secret set` i nigdzie się nie wyświetlają.

Lokalnie podpisany APK zbudujesz tak (w folderze `android`; do AAB podstaw klucz przesyłania z aliasem `przesylanie`):

```powershell
$env:LIVEPL_KLUCZ_PLIK = "$env:USERPROFILE\LivePL-klucze\android\livepl.keystore"
$env:LIVEPL_KLUCZ_HASLO = (Get-Content "$env:USERPROFILE\LivePL-klucze\android\livepl-haslo.txt").Trim()
.\gradlew.bat assemblePrzegladarkaRelease assemblePocztaRelease assembleConnectRelease "-Plivepl.tylkoTelefony"
```

TVML podpisuje własny klucz (zmienne `TVML_KLUCZ_*`, alias domyślnie `tvml`):

```powershell
$env:TVML_KLUCZ_PLIK = "$env:USERPROFILE\LivePL-klucze\android\tvml.keystore"
$env:TVML_KLUCZ_HASLO = (Get-Content "$env:USERPROFILE\LivePL-klucze\android\tvml-haslo.txt").Trim()
.\gradlew.bat :tvml:assembleRelease
```

## Dystrybucja

Teksty, grafiki, zrzuty i odpowiedzi do formularzy Google Play (dla wszystkich trzech aplikacji) są
w [sklep/README.md](sklep/README.md).

Główna droga to **Google Play**. APK z **app.livepl.eu** to uzupełnienie dla telefonów bez Sklepu
i dla osób, które wolą pobrać plik. Obie wersje mają ten sam podpis (klucz aplikacji), więc można przejść
z jednej na drugą bez odinstalowania.

### Google Play (główna droga)

Konto **LiveProduction** w Play Console jest aktywne (19.11.2026). To konto organizacji, więc nie obowiązuje
go test zamknięty z 12 testerami przez 14 dni (ten wymóg dotyczy kont osobistych założonych po 13.11.2023).
Aplikacji jeszcze nie ma. Kroki robi Karol w Play Console; AI niczego tam nie tworzy ani nie klika.

1. **Utwórz aplikację:** nazwa LivePL, język domyślny polski, aplikacja, bezpłatna.
2. **Podpisywanie przez Google Play (Play App Signing)** przy pierwszej wersji: zamiast klucza
   wygenerowanego przez Google wybierz **własny klucz aplikacji** przez eksport z magazynu kluczy Java
   narzędziem PEPK. Play Console daje do pobrania `pepk.jar` i klucz szyfrujący. Polecenie (Java 17+,
   hasło z `livepl-haslo.txt` podajesz, gdy PEPK o nie zapyta):

   ```powershell
   java -jar pepk.jar --keystore="$env:USERPROFILE\LivePL-klucze\android\livepl.keystore" --alias=livepl --output=livepl-dla-play.zip --include-cert --rsa-aes-encryption --encryption-key-path=encryption_public_key.pem
   ```

   Play Console pokazuje też własne polecenie; różni się tylko ścieżką klucza i aliasem (`livepl`).
   Plik `livepl-dla-play.zip` wysyłasz w Play Console. Tam też wgrywasz certyfikat klucza przesyłania
   `livepl-przesylanie-certyfikat.pem`. Dzięki temu wersje ze Sklepu mają ten sam podpis co APK ze strony.
3. **Pierwszy AAB** wysyłasz ręcznie: `LivePL-N.aab` z artefaktu `livepl-wydanie-N`, do testu wewnętrznego.
4. **Wpis w Sklepie:** opis, zrzuty ekranu (z artefaktu `zrzuty-N`), ikona 512 px
   i grafika funkcji, wszystko gotowe w [sklep](sklep/README.md), adres polityki prywatności, ocena treści, kategoria
   i formularz „Bezpieczeństwo danych”. LivePL sam nie zbiera danych, a strony, które otwierasz, mają własne zasady.
   Biblioteka ML Kit (tłumacz) wysyła jednak do Google dane diagnostyczne: model i system telefonu, wersję
   aplikacji, pary języków, czasy i kody błędów, bez treści stron. Trzeba je zaznaczyć w formularzu jako
   „diagnostyka” ([opis Google](https://developers.google.com/ml-kit/android-data-disclosure)).
   W opisie w Sklepie napisz, że tłumaczenie zapewnia Tłumacz Google (link do https://cloud.google.com/translate),
   tak jak wymagają zasady Google.
5. **Później: wysyłka z GitHuba.** W Google Cloud włącz „Google Play Android Developer API” i załóż konto
   usługi z kluczem JSON. W Play Console (Użytkownicy i uprawnienia) zaproś adres tego konta z prawem do
   zarządzania wersjami testowymi. Potem `node android/narzedzia/ustaw-sekrety.mjs --play <klucz.json>`
   ustawi sekret `PLAY_KONTO_USLUGI`. Od tej chwili zmiana z `[play]` w opisie wyśle AAB do testu
   wewnętrznego jako szkic.
6. **Poczta LivePL** to w Sklepie druga aplikacja (`eu.livepl.poczta`, nazwa „Poczta LivePL”). Kroki 1–4 są
   takie same: ten sam klucz aplikacji przez PEPK (z jednego konta wolno nim podpisywać kilka aplikacji),
   ten sam klucz przesyłania, ikona [sklep/eu.livepl.poczta/pl-PL/images/icon.png](sklep/eu.livepl.poczta/pl-PL/images/icon.png), plik `Poczta-LivePL-N.aab`.
   Wysyłkę z GitHuba włącza `[play-poczta]` w opisie zmiany.
7. **LivePL Connect** to trzecia aplikacja (`eu.livepl.connect`, nazwa „LivePL Connect”), tak samo jak Poczta:
   ten sam klucz aplikacji przez PEPK i klucz przesyłania, plik `LivePL-Connect-N.aab`, wysyłkę z GitHuba
   włącza `[play-connect]`. Recenzenci Google potrzebują ID i stałego hasła komputera testowego, włączonego
   w czasie przeglądu; wpisuje je Karol w Play Console (Dostęp do aplikacji).
8. **TVML** to czwarta aplikacja (nazwa „TVML”, pakiet `pl.tvml`), kategoria
   Wiadomości i czasopisma, z deklaracją aplikacji z wiadomościami. Podpisywanie przez Google Play z **osobnym**
   kluczem aplikacji TVML przez PEPK (`--keystore=...\tvml.keystore --alias=tvml`, hasło z `tvml-haslo.txt`,
   plik np. `tvml-dla-play.zip`) i tym samym certyfikatem klucza przesyłania co LivePL. Plik `TVML-N.aab`,
   wysyłkę z GitHuba włącza `[play-tvml]`; materiały i odpowiedzi do formularzy w [sklep/README.md](sklep/README.md).

### app.livepl.eu (uzupełnienie)

Podpisane `LivePL-N.apk`, `Poczta-LivePL-N.apk` i `LivePL-Connect-N.apk` z artefaktu `livepl-wydanie-N`
trafiają na app.livepl.eu, obok instalatorów na Windows.
Wgrywa go sesja „Connect LP”, dopiero po zgodzie Karola. Przy pierwszej instalacji Android poprosi, żeby
pozwolić przeglądarce instalować aplikacje. Kolejne wersje instalują się na poprzedniej, bo mają ten sam
klucz i wyższy numer budowy. LivePL na Androida nie sprawdza jeszcze sam aktualizacji (jak wersja na Windows),
więc użytkowników APK trzeba na razie powiadamiać o nowych wersjach. Użytkownicy Sklepu dostają aktualizacje same.

### Weryfikacja twórców Androida (stan na 19.11.2026)

Google wprowadza [weryfikację twórców](https://developer.android.com/developer-verification) także dla aplikacji
spoza Sklepu:

- **Od 30.09.2026** obowiązuje w Brazylii, Indonezji, Singapurze i Tajlandii, tylko dla instalacji
  z wybranych sklepów na certyfikowanych urządzeniach z Androidem 8+. APK pobranego ze strony to jeszcze nie dotyczy.
- **W 2027** Google rozszerza ją na cały świat i na wszystkie aplikacje na certyfikowanych urządzeniach.
  Niezarejestrowanej aplikacji zwykły użytkownik wtedy nie zainstaluje. Zostaną tylko „tryb zaawansowany”
  dla doświadczonych (opcje programisty, doba czekania) i instalacja przez ADB.
- **Co to znaczy dla LivePL:** aplikacje z podpisywaniem przez Google Play Google rejestruje automatycznie,
  a kto ma konto w Play Console, zarządza weryfikacją tam (także dla wersji spoza Sklepu). Po kroku 2
  (Play App Signing z kluczem aplikacji) pakiety `eu.livepl.przegladarka`, `eu.livepl.poczta`
  i `eu.livepl.connect` z tym kluczem obejmą więc też APK z app.livepl.eu. Przed globalnym startem w 2027 warto sprawdzić w Play Console, w sekcji weryfikacji
  twórców, że pakiet ma status „zarejestrowany”.

Źródła: [harmonogram](https://developer.android.com/developer-verification),
[pytania i odpowiedzi](https://developer.android.com/developer-verification/guides/faq),
[zasady testu zamkniętego](https://support.google.com/googleplay/android-developer/answer/14151465).
