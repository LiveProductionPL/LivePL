# LivePL, Poczta LivePL, LivePL Connect i TVML na iPhone'a i iPada

Cztery aplikacje z jednego projektu ([project.yml](project.yml)), ze wspólnym kodem w [Wspolne](Wspolne):

- **LivePL** (`eu.livepl.przegladarka`, folder [LivePL](LivePL)) to przeglądarka z ekranem Start, kartami i menu.
- **Poczta LivePL** (`eu.livepl.poczta`, folder [Poczta](Poczta)) to sama poczta.livepl.eu na cały ekran.
  - Ikona: logo poczty na granacie nagłówka.
  - Liczba nieprzeczytanych na ikonie.
  - Linki do innych stron otwierają się w przeglądarce.
  - Okna otwierane przez pocztę są w arkuszu z „Gotowe” ([OknoStrony.swift](Wspolne/OknoStrony.swift)).
- **LivePL Connect** (`eu.livepl.connect`, folder [Connect](Connect)) to zdalny pulpit connect.livepl.eu na cały ekran.
  - Ikona: ekran i czerwona strzałka myszy, jak na stronie i w programie na Windows.
  - Bez gestu „wstecz”, odświeżania przeciągnięciem i podglądu linków, bo na zdalnym ekranie palec steruje myszą.
  - [MostConnect.swift](Connect/MostConnect.swift) podmienia stronie trzy funkcje, których WKWebView na iPhonie nie daje:
    blokadę wygaszania ekranu w trakcie sesji (`navigator.wakeLock`), wibrację przy przytrzymaniu (`navigator.vibrate`)
    i pełny ekran (`requestFullscreen`, chowa pasek stanu i kreskę na dole). Strona na serwerze się nie zmienia.
  - Rejestruje się w urzędzie osobno („iPhone-… Connect”).
- **TVML** (`com.LiveProduction.TVML`, folder [TVML](TVML)) to natywna aplikacja serwisu tvml.pl (wersja 2.0, zastępuje aplikację z 2019).
  - Treści z API serwisu (`https://tvml.pl/api/v1/`, zapas `tvml-app.php?route=`): zakładki, bloki ekranu Start,
    pasek „NA ŻYWO”, wydanie okolicznościowe z kolorami i ikoną. Wszystkim zarządza redakcja w panelu tvml.pl ([Dane.swift](TVML/Dane.swift), [Motyw.swift](TVML/Motyw.swift)).
  - Pogoda ze sceną Ratusza zależną od stanu, pory dnia i wydania ([PogodaWidok.swift](TVML/PogodaWidok.swift)).
  - Nie używa wspólnego kodu przeglądarki ani urzędu certyfikacji. Komentarze tylko do odczytu, ze zgłaszaniem do redakcji.

Wszystkie dopisują „LivePL/<wersja>” do identyfikatora przeglądarki. Dzięki temu poczta wie, że jest w aplikacji,
i nie proponuje „Dodaj do ekranu” w Safari (livepl-settings.js na serwerze poczty).

## LivePL

Aplikacja w SwiftUI z WKWebView (silnik Safari, jedyny dozwolony na iPhonie). Wygląda jak „Reżyserka”
z LivePL na Windows:

- **Start** to ekran startowy. Ma u góry logo, „LivePL” i „Przeglądarka” oraz przycisk menu przeglądarki.
  Niżej są pole „Adres albo szukaj”, kafelki programów, otwarte karty, Moje strony i przełącznik dzień/noc przy zegarze.
- **Menu przeglądarki** ([MenuPrzegladarki.swift](LivePL/Widoki/MenuPrzegladarki.swift)) jest na ekranie Start
  i na otwartej stronie, w menu ⋯ → „Przeglądarka”. Ma pozycje: nowa karta, otwarte karty, dodaj stronę,
  wygląd (auto/dzień/noc), Urządzenie LivePL, certyfikat LivePL dla iPhone'a, o programie
  i wyczyść dane przeglądania.
- **Dolny pasek** działa jak pasek po lewej na Windows. Są tam same ikony: Start, programy (kropka oznacza, że program
  jest otwarty) i licznik kart. Każdy program ma jedną własną kartę, która pamięta stronę.
- **Karta strony** ma u góry wstecz, adres z kłódką, odśwież i menu. W menu są: dalej, udostępnij,
  znajdź na stronie, dodaj do Moich stron, kopiuj adres, otwórz w Safari i zamknij.
  Odświeżasz też, przeciągając stronę w dół.
- **Okna stron** (`target=_blank`, `window.open`) otwierają się jako nowe karty. Pobrane pliki trafiają do arkusza
  „Udostępnij” (stamtąd np. Zachowaj w Plikach). Linki `mailto:`, `tel:` i App Store otwiera iOS.
- **Karty wracają** po ponownym uruchomieniu, ale ładują się dopiero wtedy, gdy na nie wejdziesz.
  Logowania (ciasteczka) i Moje strony zostają w telefonie.
- **Certyfikaty LivePL** ([NaszeCA.swift](LivePL/NaszeCA.swift)): aplikacja ufa urzędowi LivePL (LivePL Root CA R1) sama,
  tylko dla naszych domen z [assets/ca/domeny.json](../assets/ca/domeny.json), tak jak LivePL na Windows.
  - Sprawdza cały łańcuch zasadami SSL systemu, a publiczne certyfikaty (Let's Encrypt) działają jak zawsze.
  - Niczego nie trzeba instalować w iPhonie.
  - W menu przeglądarki jest „Certyfikat LivePL dla iPhone'a” dla tych, którzy chcą root
    w całym systemie (np. Poczta Apple przy podpisanych mailach). iOS pozwala go dodać tylko przez Ustawienia
    z pliku pobranego w Safari.
- **Urządzenie LivePL** ([Urzadzenie.swift](LivePL/Urzadzenie.swift)) to rejestracja iPhone'a w urzędzie, jak na Windows.
  - Klucz EC P-256 powstaje w Secure Enclave, odpowiedniku TPM, i nie da się go skopiować.
  - Wniosek PKCS#10 idzie do cert.livepl.eu (`/urzadzenia/wniosek`). Urząd wystawia certyfikat od razu,
    a LivePL sprawdza, że pasuje do klucza i pochodzi z LivePL CA 1.
  - Co 6 h idzie raport (`/urzadzenia/raport`), z którego LivePL dowiaduje się m.in. o unieważnieniu.
  - Stan rejestracji jest w pęku kluczy, więc po przeinstalowaniu nie powstaje drugi wpis.
  - Rejestracja idzie sama przy pierwszym uruchomieniu, bez pytania; po błędzie (np. bez sieci) ponawia się przy kolejnym
    otwarciu (najczęściej co 10 min). W symulatorze jest wyłączona, żeby budowy na GitHubie nie zakładały urządzeń.
    Stan i kod: menu przeglądarki → „Urządzenie LivePL”. Poczta LivePL rejestruje się osobno („iPhone-… Poczta”).
  - Panel urzędu pokazuje na razie „klucz w TPM” (to Secure Enclave) i „dysk: nie wiadomo”, bo zna tylko BitLocker.
- **Tłumacz stron** ([Tlumacz.swift](LivePL/Tlumacz.swift)) korzysta z Apple Translation, które tłumaczy
  w telefonie, bez wysyłania tekstu do nikogo.
  - Gdy strona jest w innym języku, pod adresem pojawia się propozycja „Przetłumacz”. Z niej wybierzesz
    też „Zawsze tłumacz: …” albo „Nigdy na …”. Ręcznie: menu → „Przetłumacz na polski” / „Pokaż oryginał”.
  - Treść dopisywaną później przez stronę też tłumaczy.
  - Podmienia tylko tekst, nigdy pola formularzy, hasła ani kod.
  - Przy programach z paska (nasze polskie strony) sam niczego nie proponuje.
  - Języki pobiera iOS przy pierwszym użyciu.

Wymagany iOS 18 (Apple Translation).

Lista programów to ten sam [programy.json](../programy.json) co na Windows. Programy z `"ukryty": true` są
pod „Więcej programów” i nigdy na dolnym pasku (otwarte są w liście kart). Tak samo panel urzędu (`tylkoZYubiKey`): na telefonie otwiera
tylko stronę publiczną cert.livepl.eu, bo logowanie YubiKey działa tylko w LivePL na Windows.

## Budowanie bez Maca

Projekt Xcode powstaje z [project.yml](project.yml) przez XcodeGen. Buduje go
[.github/workflows/ios.yml](../.github/workflows/ios.yml) na Macach GitHuba, bez opłat dla publicznego
repozytorium. Każda zmiana w `ios/` wysłana na gałąź `ios` przechodzi przez te kroki:

1. budowa czterech aplikacji na symulator iPhone'a;
2. zrzuty ekranu w trybie dzień i noc (Start, Poczta, TVML, Live Production, Poczta LivePL, Connect),
   do pobrania jako artefakt `zrzuty-N`;
3. **zrzuty do App Store**, tylko gdy opis zmiany zawiera `[zrzuty-sklep]`: iPhone 6,9″ i iPad 13″ równolegle,
   w osobnych zadaniach (pierwszy rozruch symulatora trwa kilkanaście minut), artefakty `sklep-N-iphone-69` i `sklep-N-ipad-13`.
   Do App Store Connect wgrywa je `node ios/narzedzia/sklep-zrzuty.mjs <AuthKey Admin> <Issuer ID> <folder> [tvml- …]`
   (w folderze podfoldery `iphone-69` i `ipad-13`; z przedrostkiem tylko ta aplikacja);
4. **TestFlight**, tylko gdy opis zmiany zawiera `[testflight]`: archiwum Release, podpis i wysyłka do App Store Connect. `[testflight: TVML]` (albo kilka nazw po przecinku: LivePL, Poczta, Connect, TVML) wysyła tylko te aplikacje.
   Budowy nadają się i do TestFlight, i do wysłania do App Store (bez „tylko testy wewnętrzne”).

Teksty do App Store (nazwy, opisy, słowa kluczowe, kategorie, ocena wiekowa) są w [sklep/metadane.json](sklep/metadane.json),
a wpisuje je `node ios/narzedzia/sklep-metadane.mjs <AuthKey Admin> <Issuer ID> [--tylko <bundle id>]`.
TVML ma własne adresy (polityka prywatności i kontakt na tvml.pl) i „Co nowego”, bo 2.0 to aktualizacja.

Grafiki (ikona jasna i ciemna, Wstęga, ikony Poczty i Connect, ekrany startowe) tworzy
`npx electron ios/narzedzia/grafiki.js` z wektorowej Wstęgi w [narzedzia/logo.js](narzedzia/logo.js) i znaku Connect.

## Konfiguracja Apple (jednorazowo)

Konto Apple Developer: Karol Kowalski, Team ID `WHKQ7CW48T`. Identyfikator aplikacji to `eu.livepl.przegladarka`.

1. **Klucze API** zakładasz w App Store Connect: Użytkownicy i dostęp → Integracje → App Store Connect API →
   Klucze zespołu → +. Potrzebne są dwa:
   - „LivePL” z rolą **Admin**. Zostaje tylko w tym komputerze i służy do certyfikatu i profilu.
   - „LivePL GitHub” z rolą **App Manager**. Trafia do sekretów GitHuba i służy tylko do wysyłania budów.

   Plik `AuthKey_….p8` da się pobrać tylko raz. Zapisz oba w `%USERPROFILE%\LivePL-klucze\apple\`,
   a z tej samej strony przepisz **Issuer ID**.
2. **Certyfikat i profil** robi `node ios/narzedzia/apple-konfiguracja.mjs <AuthKey klucza Admin> <Issuer ID>`:
   - identyfikator aplikacji;
   - certyfikat Apple Distribution; jego klucz prywatny powstaje tutaj i zostaje w zaszyfrowanym `.p12`;
   - profil „LivePL App Store”.
3. **Aplikację** dodajesz w App Store Connect: Aplikacje → + → Nowa aplikacja → iOS. Wpisujesz nazwę LivePL,
   język polski, Bundle ID `eu.livepl.przegladarka` i SKU `livepl-ios`.
4. **Sekrety GitHuba** wpisujesz sam: `node ios/narzedzia/ustaw-sekrety.mjs <AuthKey klucza GitHub> <Issuer ID>`.
   Wartości idą prosto z plików do `gh secret set` i nigdzie się nie wyświetlają.
5. **TestFlight:** w App Store Connect wejdź w zakładkę TestFlight, dodaj grupę testów wewnętrznych i siebie do niej.
   Potem zainstaluj na iPhonie aplikację TestFlight z App Store.

**Poczta LivePL** korzysta z tego samego certyfikatu. Potrzebuje tylko własnego identyfikatora i profilu:
`node ios/narzedzia/apple-konfiguracja.mjs <AuthKey Admin> <Issuer ID> --bundle eu.livepl.poczta --nazwa "Poczta LivePL" --profil "Poczta LivePL App Store"`.
Wpis aplikacji „Poczta LivePL” (SKU `livepl-poczta-ios`) już jest w App Store Connect. Potem ponownie `ustaw-sekrety.mjs`,
który dopisze sekret `APPLE_PROFIL_POCZTA`. Bez niego TestFlight wysyła tylko LivePL i ostrzega.

**LivePL Connect** tak samo: `--bundle eu.livepl.connect --nazwa "LivePL Connect" --profil "LivePL Connect App Store"`,
wpis aplikacji „LivePL Connect” (SKU `livepl-connect-ios`) w App Store Connect i ponownie `ustaw-sekrety.mjs`
(sekret `APPLE_PROFIL_CONNECT`). Do przeglądu Apple potrzebny jest włączony komputer testowy z programem LivePL Connect:
jego ID i stałe hasło wpisuje się w App Store Connect w danych do przeglądu.

Certyfikat dystrybucyjny jest ważny rok. Po wygaśnięciu usuń `LivePL-klucze\apple\dystrybucja.*` i powtórz kroki 2 i 4.
