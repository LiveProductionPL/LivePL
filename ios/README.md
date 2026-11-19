# LivePL na iPhone'a i iPada

Aplikacja w SwiftUI z WKWebView (silnik Safari, jedyny dozwolony na iPhonie). Wygląda jak „Reżyserka”
z LivePL na Windows:

- **Reżyserka** to ekran startowy. Ma pole „Adres albo szukaj”, kafelki programów, otwarte karty, Moje strony,
  kartę certyfikatu LivePL i przełącznik dzień/noc przy zegarze.
- **Dolny pasek** działa jak pasek po lewej na Windows. Jest tam Reżyserka, programy (kropka oznacza, że program
  jest otwarty) i licznik kart. Każdy program ma jedną własną kartę, która pamięta stronę.
- **Karta strony** ma u góry wstecz, adres z kłódką, odśwież i menu. W menu są: dalej, udostępnij,
  znajdź na stronie, dodaj do Moich stron, kopiuj adres, otwórz w Safari i zamknij.
  Odświeżasz też, przeciągając stronę w dół.
- **Okna stron** (`target=_blank`, `window.open`) otwierają się jako nowe karty. Pobrane pliki trafiają do arkusza
  „Udostępnij” (stamtąd np. Zachowaj w Plikach). Linki `mailto:`, `tel:` i App Store otwiera iOS.
- **Karty wracają** po ponownym uruchomieniu, ale ładują się dopiero wtedy, gdy na nie wejdziesz.
  Logowania (ciasteczka) i Moje strony zostają w telefonie.

Lista programów to ten sam [programy.json](../programy.json) co na Windows. Programy z `"ukryty": true` są
pod „Więcej programów”. Panel urzędu (`tylkoZYubiKey`) otwiera na telefonie stronę publiczną
cert.livepl.eu, bo logowanie YubiKey działa tylko w LivePL na Windows.

## Budowanie bez Maca

Projekt Xcode powstaje z [project.yml](project.yml) przez XcodeGen. Buduje go
[.github/workflows/ios.yml](../.github/workflows/ios.yml) na Macach GitHuba, bez opłat dla publicznego
repozytorium. Każda zmiana w `ios/` wysłana na gałąź `ios` przechodzi przez te kroki:

1. budowa na symulator iPhone'a;
2. zrzuty ekranu w trybie dzień i noc (Reżyserka, Poczta, TVML, Live Production), do pobrania jako artefakt `zrzuty-N`;
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
