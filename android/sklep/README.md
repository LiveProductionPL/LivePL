# Google Play: LivePL, Poczta LivePL, LivePL Connect i TVML

Wszystko do wpisów w Sklepie Google Play dla trzech aplikacji LivePL, a na końcu dla TVML. Teksty są zgodne z App Store
(`ios/sklep/metadane.json`). Pliki leżą w układzie katalogów [fastlane](https://docs.fastlane.tools/actions/supply/),
więc później da się je wgrywać automatycznie:

```
sklep/<pakiet>/pl-PL/title.txt                    nazwa (do 30 znaków)
sklep/<pakiet>/pl-PL/short_description.txt        krótki opis (do 80 znaków)
sklep/<pakiet>/pl-PL/full_description.txt         pełny opis (do 4000 znaków)
sklep/<pakiet>/pl-PL/changelogs/default.txt       „Co nowego”
sklep/<pakiet>/pl-PL/images/icon.png              ikona 512×512 (PNG z kanałem alfa)
sklep/<pakiet>/pl-PL/images/featureGraphic.png    grafika funkcji 1024×500 (PNG bez kanału alfa)
sklep/<pakiet>/pl-PL/images/phoneScreenshots/     zrzuty telefonu 1080×1920 (9:16)
sklep/<pakiet>/pl-PL/images/tenInchScreenshots/   zrzuty tabletu 10" 1440×2560 (9:16), na razie tylko LivePL
```

Ikony i grafiki funkcji tworzy `npx electron android/narzedzia/grafiki.js`, a zrzuty
`android/narzedzia/zrzuty-sklep.sh <APK LivePL> <APK Poczty LivePL> [APK LivePL Connect]` z działającego
emulatora (z wydania 1.0,
żeby w stopce nie było „-debug”). Wszystko w Play Console robi Karol; AI tam niczego nie tworzy ani nie akceptuje.

## Wspólne dane

| Pole | LivePL (`eu.livepl.przegladarka`) | Poczta LivePL (`eu.livepl.poczta`) | LivePL Connect (`eu.livepl.connect`) |
|---|---|---|---|
| Język domyślny | polski (pl-PL) | polski (pl-PL) | polski (pl-PL) |
| Aplikacja czy gra | aplikacja | aplikacja | aplikacja |
| Bezpłatna czy płatna | bezpłatna | bezpłatna | bezpłatna |
| Kategoria | Produktywność | Biznes | Narzędzia (na iPhonie: Narzędzia i Biznes) |
| E-mail kontaktowy | biuro@liveproduction.pl | biuro@liveproduction.pl | biuro@liveproduction.pl |
| Strona | https://livepl.eu | https://livepl.eu | https://livepl.eu |
| Telefon | według uznania Karola | według uznania Karola | według uznania Karola |
| Polityka prywatności | https://livepl.eu/polityka-prywatnosci#aplikacje | to samo | to samo |
| Reklamy | nie zawiera | nie zawiera | nie zawiera |
| Wersja | 1.0 (`versionCode` = numer przebiegu na GitHubie) | 1.0 | 1.0 |

## Dostęp do aplikacji

- **LivePL:** wszystkie funkcje działają bez logowania i bez specjalnego dostępu. Strony programów są publiczne,
  a logowanie do poczty to zwykła strona internetowa w przeglądarce.
- **Poczta LivePL:** bez konta pocztowego widać tylko ekran logowania. Google wymaga konta testowego dla
  recenzentów: login i hasło skrzynki demonstracyjnej wpisuje Karol sam w Play Console (Treść aplikacji → Dostęp
  do aplikacji), z instrukcją „Zaloguj się na ekranie startowym podanym kontem”. Warto też zrobić zrzuty skrzynki
  z tego konta i dodać je jako pierwsze zrzuty Poczty LivePL (teraz są tylko ekrany logowania).
- **LivePL Connect:** bez komputera z programem LivePL Connect widać tylko ekran połączenia. Recenzentom trzeba
  podać ID i stałe hasło komputera testowego, który jest włączony w czasie przeglądu (jak w App Store). Wpisuje
  je Karol sam w Play Console, z instrukcją „Wpisz ID i hasło na ekranie startowym i stuknij Połącz”.

## Odbiorcy i treść

- **Grupa docelowa:** 18 lat i więcej (narzędzia firmowe). Aplikacje nie są przeznaczone dla dzieci.
- **Aplikacja informacyjna (news):** nie. **Aplikacja rządowa:** nie. **Funkcje finansowe:** nie.
  **Zdrowie:** nie. **COVID-19:** nie.
- **Identyfikator wyświetlania reklam:** nie używamy (w manifestach nie ma uprawnienia `AD_ID`).
- **Uprawnienia:** internet, stan sieci, aparat (zdjęcia do formularzy i załączników), a w LivePL także mikrofon
  i lokalizacja, gdy prosi o nie strona. LivePL Connect: tylko internet, stan sieci i wibracja
  (ekran nie gaśnie dzięki fladze okna, bez osobnego uprawnienia), bez aparatu, mikrofonu i lokalizacji. Wszystkie z pytaniem o zgodę. Brak uprawnień wymagających deklaracji
  (SMS, połączenia, dostęp do wszystkich plików, lokalizacja w tle, usługi ułatwień dostępu).

## Ocena treści (kwestionariusz IARC)

Kategoria: **Narzędzia, produktywność, komunikacja lub inne**. Odpowiedzi:

| Pytanie | LivePL | Poczta LivePL | LivePL Connect |
|---|---|---|---|
| Przemoc, strach, seks, wulgaryzmy, używki, hazard, humor dla dorosłych | nie | nie | nie |
| Czy użytkownicy mogą się kontaktować lub wymieniać treści? | tak (przeglądarka otwiera dowolne strony) | tak (poczta e-mail) | nie (steruje własnym komputerem, bez czatu i wymiany treści z innymi) |
| Czy aplikacja udostępnia innym użytkownikom lokalizację? | nie | nie | nie |
| Zakupy cyfrowe | nie | nie | nie |
| Czy to przeglądarka albo wyszukiwarka (nieograniczony dostęp do internetu)? | **tak** | nie (linki otwiera przeglądarka telefonu) | nie (łączy tylko z komputerem po ID i haśle) |

Spodziewana ocena: dla wszystkich (PEGI 3), w LivePL i Poczcie LivePL z oznaczeniem interakcji użytkowników,
a w LivePL także nieograniczonego dostępu do internetu. Przy LivePL Connect odpowiedź o wymianie treści
to decyzja Karola: zdalny pulpit pokazuje wszystko, co jest na ekranie komputera.

## Bezpieczeństwo danych

Dane stron internetowych, które użytkownik otwiera, zbierają te strony, a nie aplikacja. Ciasteczka, Moje strony,
karty i pobrane pliki zostają w telefonie. Do formularza:

- Czy aplikacja zbiera lub udostępnia dane? **Tak.**
- Czy dane są szyfrowane podczas przesyłania? **Tak** (tylko HTTPS).
- Czy użytkownik może poprosić o usunięcie danych? **Tak**, pisząc na biuro@liveproduction.pl
  (rejestrację urządzenia usuwa też urząd LivePL).
- Udostępnianie innym firmom: **nie**.

| Typ danych | LivePL | Poczta LivePL | Po co | Wymagane? |
|---|---|---|---|---|
| Identyfikatory urządzenia lub inne | tak | tak (i LivePL Connect) | rejestracja „Urządzenie LivePL” w urzędzie LivePL: kod klucza urządzenia, model telefonu, wersja Androida i aplikacji; cele: działanie aplikacji, bezpieczeństwo i zapobieganie oszustwom | tak (automatycznie) |
| Informacje o aplikacji i wydajności → Diagnostyka | tak | nie | biblioteka Google ML Kit (tłumacz stron) wysyła do Google dane diagnostyczne: model i system telefonu, wersję aplikacji, pary języków, czasy i kody błędów, bez treści stron ([opis Google](https://developers.google.com/ml-kit/android-data-disclosure)); cel: analityka | tak |
| Wiadomości → E-maile | nie | tak | treść poczty idzie do serwera poczty Live Production (poczta.livepl.eu); cel: działanie aplikacji | tak |
| Dane osobowe → Adres e-mail | nie | tak | logowanie do poczty i nadawca wiadomości; cel: działanie aplikacji, zarządzanie kontem | tak |
| Zdjęcia i filmy, Pliki i dokumenty | nie | tak | tylko gdy użytkownik sam dołączy je do wiadomości; cel: działanie aplikacji | nie (opcjonalnie) |

**LivePL Connect** zbiera tylko „Identyfikatory urządzenia lub inne” (rejestracja urządzenia, jak wyżej). Obraz
ekranu, ruchy myszy i klawisze płyną szyfrowanym WebRTC wprost między telefonem a komputerem, a serwer
connect.livepl.eu tylko kojarzy urządzenia po ID; według zasad Google dane przesyłane wprost między
urządzeniami (end-to-end) nie są „zbierane”, jeśli nie trafiają do twórcy aplikacji. ID i hasło ostatnich
połączeń zostają w telefonie. Gdy serwer Connect zapisuje dzienniki połączeń (adres IP, ID, czas), trzeba
dodać „Informacje o aplikacji i wydajności → Dzienniki awarii / Inne” albo „Identyfikatory urządzenia”; to wie
sesja „Connect LP”, która prowadzi serwer.

Do decyzji Karola: rejestracja urządzenia idzie sama, bez pytania (tak jak na Windows i iPhonie). Zasady Google
o danych użytkownika wymagają wyraźnej informacji w aplikacji, gdy dane wysyłane są w sposób, którego użytkownik
może się nie spodziewać. Opis jest w „Urządzenie LivePL” w menu i w polityce prywatności, ale można dodać
jednorazowy komunikat przy pierwszym uruchomieniu.

## Co nowego (1.0)

- LivePL: `changelogs/default.txt`
- Poczta LivePL: `changelogs/default.txt`
- LivePL Connect: `changelogs/default.txt`

## Kolejność w Play Console

1. Utwórz aplikacje „LivePL”, „Poczta LivePL” i „LivePL Connect” (polski, aplikacja, bezpłatna) i zaakceptuj
   deklaracje dewelopera.
2. Podpisywanie przez Google Play z własnym kluczem aplikacji (PEPK) i certyfikat klucza przesyłania:
   opis w [android/README.md](../README.md), sekcja „Google Play”.
3. Wpis w Sklepie: teksty, ikona, grafika funkcji, zrzuty z tego katalogu, kategoria i kontakt.
4. Treść aplikacji: polityka prywatności, dostęp do aplikacji, reklamy, ocena treści, grupa docelowa,
   bezpieczeństwo danych (odpowiedzi wyżej).
5. Test wewnętrzny: wgraj `LivePL-N.aab`, `Poczta-LivePL-N.aab` i `LivePL-Connect-N.aab` z artefaktu
   `livepl-wydanie-N`, dodaj testerów.
6. Po sprawdzeniu: wersja produkcyjna. Konto organizacji nie musi przechodzić testu zamkniętego z 12 testerami.

## TVML (`pl.tvml`)

Natywna aplikacja serwisu tvml.pl (opis w [android/README.md](../README.md), sekcja TVML). Teksty są zgodne
z wpisem TVML w App Store (`ios/sklep/metadane.json`, `com.LiveProduction.TVML`). Pliki: `pl.tvml/pl-PL/…`,
7 zrzutów telefonu (Start, artykuł, pogoda, wideo, Więcej, zima, Wigilia w nocy), ikona 512 px z ikony iOS
i grafika funkcji z nocną panoramą Leszna.

| Pole | TVML |
|---|---|
| Nazwa | TVML (2.0 to tylko numer wersji) |
| Język domyślny | polski (pl-PL) |
| Aplikacja czy gra, cena | aplikacja, bezpłatna |
| Kategoria | Wiadomości i czasopisma (w App Store: Wiadomości) |
| E-mail kontaktowy | redakcja@tvml.pl |
| Strona | https://tvml.pl/ |
| Polityka prywatności | https://tvml.pl/strona/polityka-prywatnosci (jak w App Store) |
| Reklamy | **zawiera reklamy** (decyzja Karola): sieci reklamowych nie ma, ale czasem są „Artykuły sponsorowane”, a Google liczy jako reklamy też treści płatne i promocje własne |
| Dostęp do aplikacji | wszystko działa bez logowania |

- **Deklaracja aplikacji z wiadomościami** (Treść aplikacji → Aplikacje z wiadomościami): **tak**. Google wymaga
  przy niej danych wydawcy i kontaktu do redakcji (strony „O nas”, „Redakcja”, „Kontakt” są w aplikacji
  w Więcej) oraz autora i daty przy artykułach (są). Dane wydawcy potwierdza Karol.
- **Grupa docelowa:** 13 lat i więcej (decyzja Karola): grupy 13–15, 16–17 i 18+. Bez grup poniżej 13 lat,
  bo te włączają zasady Google dla rodzin (Families). Identyfikatora wyświetlania reklam nie ma (brak `AD_ID`).
- **Ocena treści (IARC):** kategoria „Informacje, nauka, wiadomości” (Reference, News, or Educational).
  Jak w App Store: przemoc realistyczna rzadka i łagodna (wiadomości o wypadkach i przestępstwach), treści
  użytkowników: tak (komentarze czytelników tylko do odczytu, moderowane przez redakcję), lokalizacja: nie,
  zakupy: nie, nieograniczony dostęp do internetu: nie (strony tvml.pl otwierają się w karcie przeglądarki).
- **Bezpieczeństwo danych:** aplikacja nie wysyła identyfikatorów ani danych o użytkowniku; zapisane artykuły,
  rozmiar tekstu i wygląd zostają w telefonie. tvml.pl dostaje zwykłe zapytania HTTPS z agentem `TVMLApp`
  (jak przy wejściu na stronę). Propozycja odpowiedzi: „nie zbiera danych”, szyfrowanie: tak. Do decyzji Karola:
  filmy grają w odtwarzaczu YouTube (youtube-nocookie), który sam rozmawia z Google; zwykle się tego nie zaznacza
  jako danych aplikacji, ale warto wspomnieć o YouTube w polityce prywatności. Formularz trzeba poprawić, gdy
  dojdą powiadomienia (token FCM), odsłony z `installationId` albo Play Integrity (kolejny krok API).
- **Podpisywanie:** TVML ma **osobny klucz aplikacji** (`tvml.keystore`, alias `tvml`), który Karol przekazuje
  Google przez PEPK przy pierwszej wersji, tak jak klucz LivePL; AAB podpisuje wspólny klucz przesyłania
  (ten sam certyfikat `livepl-przesylanie-certyfikat.pem` wgrywa się w Play Console TVML).
- **Linki do artykułów (App Links):** `https://tvml.pl/.well-known/assetlinks.json` dla pakietu `pl.tvml`
  z odciskiem SHA-256 klucza aplikacji TVML: `41:83:0B:7B:19:3C:E6:65:79:04:4A:1C:F0:88:8A:11:93:FC:A0:8D:20:3A:59:F1:A5:91:FB:79:3A:83:73:4B`.
  Wgrywa go sesja TVML Connect po zgodzie Karola.
