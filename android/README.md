# LivePL na Androida

Aplikacja w Kotlinie i Jetpack Compose (Material 3) z Android System WebView, czyli silnikiem Chrome.
Wygląda jak „Reżyserka” z LivePL na Windows i działa jak [LivePL na iPhone'a](../ios/README.md):

- **Reżyserka** to ekran startowy. Ma pole „Adres albo szukaj”, kafelki programów, otwarte karty, Moje strony,
  kartę „Certyfikat LivePL” i przełącznik dzień/noc przy zegarze.
- **Dolny pasek** działa jak pasek po lewej na Windows. Jest tam Reżyserka, programy (kropka oznacza, że program
  jest otwarty) i licznik kart. Licznik otwiera listę kart: zamykanie, „Nowa karta” i „Zamknij wszystkie”.
  Każdy program ma jedną własną kartę. Przytrzymanie programu na pasku pozwala go zamknąć.
- **Karta strony** ma u góry wstecz, adres z kłódką, odśwież i menu. Stuknięcie w adres pozwala wpisać inny.
  W menu są: dalej, udostępnij, znajdź na stronie, przetłumacz na polski, dodaj do Moich stron, kopiuj adres,
  otwórz w przeglądarce i zamknij. Odświeżasz też, przeciągając stronę w dół. Gdy strona się nie otworzy,
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
- **Systemowy przycisk Wstecz** cofa stronę. Na początku historii wraca do Reżyserki, a okno otwarte przez
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

Lista programów to ten sam [programy.json](../programy.json) co na Windows i iPhonie. Programy z `"ukryty": true`
są pod „Więcej programów”. Panel urzędu (`tylkoZYubiKey`) otwiera na telefonie stronę publiczną
cert.livepl.eu, bo logowanie YubiKey działa tylko w LivePL na Windows.

Wymagania: Android 8.0 lub nowszy. Identyfikator: `eu.livepl.przegladarka`. Wersja testowa z GitHuba ma
identyfikator `eu.livepl.przegladarka.debug` i nazwę „LivePL test”, więc może stać obok wydania.

## Budowanie bez Android Studio

Buduje [.github/workflows/android.yml](../.github/workflows/android.yml) na Linuksie GitHuba, bez opłat dla
publicznego repozytorium. Każda zmiana w `android/` wysłana na gałąź `android` przechodzi przez te kroki:

1. **Debug APK** i sprawdzenie kodu (lint). Wynik to artefakt `livepl-debug-N`.
2. **Zrzuty ekranu z emulatora** Androida 16 (Pixel 7): Reżyserka, Poczta i TVML, każde w dzień i w nocy.
   Wynik to artefakt `zrzuty-N` z dziennikiem emulatora.
3. **Podpisany APK i AAB**, tylko gdy są ustawione sekrety kluczy (niżej). Wynik to artefakt `livepl-wydanie-N`:
   - `LivePL-N.apk` na app.livepl.eu: klucz aplikacji, tylko procesory telefonów (ARM), ok. 16 MB;
   - `LivePL-N.aab` do Google Play: klucz przesyłania, wszystkie procesory (Sklep wybiera właściwe);
   - `SHA256SUMS.txt`.
4. **Wysyłka do Google Play**, tylko gdy opis zmiany zawiera `[play]` i jest ustawiony klucz konta usługi.
   AAB trafia do testu wewnętrznego jako szkic, a publikuje go człowiek w Play Console.

Numer budowy (`versionCode`) to numer przebiegu workflow, a wersja (`versionName`) jest w
[app/build.gradle.kts](app/build.gradle.kts). Artefakty pobierasz tak:

```powershell
gh run list --repo LiveProductionPL/LivePL --workflow android.yml --limit 3
gh run download <numer przebiegu> --repo LiveProductionPL/LivePL -n livepl-wydanie-<N>
```

Lokalnie wystarczą JDK 17+ i [narzędzia wiersza poleceń Androida](https://developer.android.com/studio#command-tools)
(bez Android Studio). Ustaw `JAVA_HOME` i `ANDROID_HOME`, a potem w folderze `android`: `.\gradlew.bat assembleDebug`.
Skrypt [narzedzia/zrzuty.sh](narzedzia/zrzuty.sh) robi zrzuty z działającego emulatora tak samo jak GitHub.

Grafiki tworzą dwa skrypty, uruchamiane z katalogu `przegladarka-livepl`:

- `npx electron android/narzedzia/grafiki.js` tworzy Wstęgę (dzień i noc), ikonę adaptacyjną z warstwą
  jednobarwną do ikon motywowych, ikony programów z [assets/programy](../assets/programy) i ikonę 512 px
  do Google Play ([sklep/ikona-512.png](sklep/ikona-512.png)). Wstęga pochodzi z tej samej wektorowej Wstęgi co
  na iPhonie ([ios/narzedzia/logo.js](../ios/narzedzia/logo.js)).
- `node android/narzedzia/ikony.mjs` pobiera ikony interfejsu z Material Symbols (Google, licencja Apache 2.0).

## Klucze podpisu

Są dwa klucze. Oba leżą w `%USERPROFILE%\LivePL-klucze\android\`, każdy z losowym hasłem w pliku obok.
Tworzy je `node android/narzedzia/zrob-klucz.mjs` (z argumentem `przesylanie` drugi). Skrypt nie nadpisuje
istniejących kluczy. Oba powstały 19.11.2026.

| Klucz | Pliki | Do czego | Odcisk SHA-256 certyfikatu (publiczny) |
|---|---|---|---|
| aplikacji | `livepl.keystore`, `livepl-haslo.txt` | APK z app.livepl.eu; ten sam klucz dostaje Google Play do podpisywania wersji ze Sklepu | `45:99:A9:F4:32:0C:F3:4B:5D:08:65:50:F5:9A:D3:F4:15:27:C9:A9:B0:D1:1B:F0:77:46:CE:1B:C7:FD:7C:41` |
| przesyłania | `livepl-przesylanie.keystore`, `livepl-przesylanie-haslo.txt`, `livepl-przesylanie-certyfikat.pem` | podpis AAB wysyłanego do Google Play | `F5:A6:7E:F5:1F:9B:35:68:8A:9A:6E:A8:2E:AB:AE:2E:3F:6E:43:EC:65:EB:D3:F6:99:F2:44:8D:BE:B1:74:14` |

- **Klucza aplikacji nie wolno zgubić ani zmienić.** Android instaluje aktualizację tylko wtedy, gdy ma ten sam
  podpis. Zrób kopię obu kluczy i haseł w bezpiecznym miejscu poza tym komputerem.
- Zgubiony klucz przesyłania da się wymienić przez Play Console. Klucza aplikacji nie da się wymienić.
- **Sekrety GitHuba** wpisujesz sam: `node android/narzedzia/ustaw-sekrety.mjs`. Ustawia `ANDROID_KLUCZ`,
  `ANDROID_KLUCZ_HASLO`, `ANDROID_KLUCZ_ALIAS`, `ANDROID_PRZESYLANIE` i `ANDROID_PRZESYLANIE_HASLO`.
  Wartości idą prosto z plików do `gh secret set` i nigdzie się nie wyświetlają.

Lokalnie podpisany APK zbudujesz tak (w folderze `android`; do AAB podstaw klucz przesyłania z aliasem `przesylanie`):

```powershell
$env:LIVEPL_KLUCZ_PLIK = "$env:USERPROFILE\LivePL-klucze\android\livepl.keystore"
$env:LIVEPL_KLUCZ_HASLO = (Get-Content "$env:USERPROFILE\LivePL-klucze\android\livepl-haslo.txt").Trim()
.\gradlew.bat assembleRelease "-Plivepl.tylkoTelefony"
```

## Dystrybucja

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
   ([sklep/ikona-512.png](sklep/ikona-512.png)), adres polityki prywatności, ocena treści, kategoria
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

### app.livepl.eu (uzupełnienie)

Podpisany `LivePL-N.apk` z artefaktu `livepl-wydanie-N` trafia na app.livepl.eu, obok instalatorów na Windows.
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
  (Play App Signing z kluczem aplikacji) pakiet `eu.livepl.przegladarka` z tym kluczem obejmie więc też APK
  z app.livepl.eu. Przed globalnym startem w 2027 warto sprawdzić w Play Console, w sekcji weryfikacji
  twórców, że pakiet ma status „zarejestrowany”.

Źródła: [harmonogram](https://developer.android.com/developer-verification),
[pytania i odpowiedzi](https://developer.android.com/developer-verification/guides/faq),
[zasady testu zamkniętego](https://support.google.com/googleplay/android-developer/answer/14151465).
