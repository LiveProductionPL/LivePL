# LivePL na iPhone'a i iPada

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
  - Na ekranie Start jest propozycja „Zarejestruj ten iPhone” („Nie teraz” chowa ją do następnego uruchomienia).
    Szczegóły są w menu przeglądarki → „Urządzenie LivePL”.
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
pod „Więcej programów”, a nie na dolnym pasku. Tak samo panel urzędu (`tylkoZYubiKey`): na telefonie otwiera
tylko stronę publiczną cert.livepl.eu, bo logowanie YubiKey działa tylko w LivePL na Windows.

## Budowanie bez Maca

Projekt Xcode powstaje z [project.yml](project.yml) przez XcodeGen. Buduje go
[.github/workflows/ios.yml](../.github/workflows/ios.yml) na Macach GitHuba, bez opłat dla publicznego
repozytorium. Każda zmiana w `ios/` wysłana na gałąź `ios` przechodzi przez te kroki:

1. budowa na symulator iPhone'a;
2. zrzuty ekranu w trybie dzień i noc (Start, Poczta, TVML, Live Production), do pobrania jako artefakt `zrzuty-N`;
3. **TestFlight**, tylko gdy opis zmiany zawiera `[testflight]`: archiwum Release, podpis i wysyłka do App Store Connect.

Grafiki (ikona jasna i ciemna, Wstęga, ikona Poczty) tworzy `npx electron ios/narzedzia/grafiki.js`
z wektorowej Wstęgi w [narzedzia/logo.js](narzedzia/logo.js).

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

Certyfikat dystrybucyjny jest ważny rok. Po wygaśnięciu usuń `LivePL-klucze\apple\dystrybucja.*` i powtórz kroki 2 i 4.
