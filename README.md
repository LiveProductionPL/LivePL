# Przeglądarka LivePL

Autor: Karol Kowalski · Copyright © 2026 Live Production Karol Kowalski · licencja [MIT](LICENSE) · instalator: [app.livepl.eu](https://app.livepl.eu/)

Własna przeglądarka do programów LivePL: poczty, strefy klienta, Sevetoo, egzamin.info.pl i panelu serwera. Wszystkie są w jednym oknie, a logowania zostają zapamiętane.

Działa na silniku LivePL, zbudowanym na Chromium (tak jak Edge i Opera). Plik programu to `node_modules/electron/dist/LivePL.exe`. Po aktualizacji silnika albo zmianie ikony utwórz go ponownie poleceniem `npm run exe`.

## Uruchomienie

W tym folderze:

```
npm start
```

## Co ma

Układ nazywa się **Reżyserka**: strona jest jak monitor w ramce, programy są kanałami po lewej, a na dole jest pasek wiadomości jak w telewizji.

- **Kanały po lewej.** Same ikony (logo Poczty, Live Production, TVML); nazwa jest w podpowiedzi. Pod dowolną ikoną można dodać krótki podpis (do 14 znaków): prawy klik na ikonie → „Dodaj podpis pod ikoną”. Liczba przy Poczcie to nieprzeczytane wiadomości. Ta sama liczba pojawia się na ikonie na pasku zadań.
- **Strona się nie wczytała?** Zamiast suchego komunikatu jest telewizorek z „przerwą w nadawaniu”, prosty opis, co się stało (brak internetu, zerwane połączenie, zły certyfikat…), rady i odliczanie do kolejnej próby. Szczegóły techniczne są pod „Szczegóły techniczne”.
- **Dostęp dla AI.** Claude i inne AI mogą korzystać z LivePL jak z dodatku w Chrome. Opis niżej.
- **Pasek wiadomości na dole.** Pokazuje powiadomienia z naszych programów (np. nowa wiadomość w poczcie), wzrost liczby nieprzeczytanych i pobrane pliki, każde z godziną. Kliknięcie przenosi do programu albo otwiera plik.
- **Adres albo szukaj (Ctrl+K)** w prawym dolnym rogu. Pole rozwija się na cały pasek, gdy coś wpisujesz.
- **Inne strony otwierają się jako karty** pod kanałami. Linki z poczty trafiają do nowej karty, a poczta zostaje na miejscu.
- **Dużo ikon na pasku.** Gdy kanały albo karty się nie mieszczą, przewijasz je kółkiem myszy. Nad listą i pod nią widać strzałkę z liczbą schowanych ikon, a kliknięcie strzałki przewija listę. Aktywna karta jest zawsze widoczna.
- **Przestawianie ikon.** Kanał albo kartę chwytasz myszą i upuszczasz wyżej lub niżej. Kolejność kanałów LivePL zapamiętuje (Ctrl+1…8 idzie według niej), a kolejność kart obowiązuje do zamknięcia programu.
- **Rejestracja przeglądarki w urzędzie LivePL** to jedno okienko i jeden przycisk „Zarejestruj”. Przy okazji LivePL może zainstalować certyfikat urzędu w Windows (Windows pyta o zgodę). Rejestracja już na to nie czeka. Numer rejestracji jest zapisany także przy certyfikacie urządzenia w Windows, więc gdy dane LivePL znikną, przeglądarka wraca do swojej rejestracji, a w urzędzie nie pojawia się drugi wpis. Ręcznie: menu ⋮ → Ustawienia → Urządzenie LivePL.
- **Pobrane pliki zapisują się same** w zwykłym folderze `Pobrane` (Ctrl+J go otwiera).
- **Po zamknięciu okna program działa dalej** przy zegarze, więc poczta sprawdza nowe wiadomości. Program zamykasz prawym przyciskiem na tej ikonie. Działanie w tle można wyłączyć w menu ⋮ → Ustawienia.
- **Ostatnio otwarte karty wracają** po ponownym uruchomieniu, z kartą, która była aktywna. Pozostałe karty ładują się dopiero po kliknięciu.
- **Dzień i noc.** Przycisk obok zegara LivePL przełącza tryb: automatycznie → dzień → noc. W trybie automatycznym LivePL jest ciemny od zachodu do wschodu słońca, a w dzień ma wygląd jak Windows, więc przy ciemnym Windows jest ciemny cały czas. Godziny dla Leszna (te same co na tvml.pl) LivePL liczy sam, bez internetu. Przełącza się w chwili wschodu i zachodu, bez ponownego uruchamiania: kolory paska, panele, logo w rogu i strony (`prefers-color-scheme`). Ikona okna, ikona przy zegarze Windows i ikona skrótu na pulpicie mają kolor dobrany do paska zadań Windows.
- **Udostępnianie ekranu** (np. na lekcjach w Sevetoo) otwiera okienko wyboru ekranu albo okna.
- **Prawy przycisk myszy:** kopiuj, wklej, poprawianie literówek, otwieranie linku w nowej karcie albo w zwykłej przeglądarce, tłumaczenie strony albo zaznaczenia.
- **Tłumacz stron.** Strona w innym języku niż polski ma obok adresu przycisk „Przetłumacz”. Domyślnie tłumaczy ten komputer, a tekst nigdzie nie wychodzi. Opis niżej.
- **Wejście mimo błędu certyfikatu** i **Bezpieczny DNS**: opis niżej.

## Błąd certyfikatu: wejście mimo to

Strona z nieprawidłowym certyfikatem jest domyślnie zablokowana. Na stronie błędu jest link „Wiem, co robię – wejdź mimo to…”. Rozwija ostrzeżenie i dane certyfikatu (dla kogo, wystawca, ważność, odcisk SHA-256). Potem wybierasz:

- **Tylko teraz:** wyjątek w pamięci, do zamknięcia LivePL.
- **Zapamiętaj ten certyfikat:** wyjątek trafia do ustawień (menu ⋮ → Ustawienia → Wyjątki certyfikatów, tam też go usuniesz).

Wyjątek to zawsze **adres z portem i odcisk SHA-256 jednego certyfikatu** (np. `203.0.113.10:2222` + odcisk), a nie zaufanie do całego serwera. Gdy serwer pokaże inny certyfikat, ostrzeżenie wraca z dopiskiem, że certyfikat się zmienił. Nowy certyfikat zastępuje stary dopiero po ponownym „Wejdź mimo to”. Strona otwarta mimo błędu ma czerwoną kłódkę. Okienko kłódki pokazuje wyjątek i ma przycisk „Usuń wyjątek i zablokuj znowu”. Po usunięciu otwarte połączenia są zamykane, a karty z tym adresem wczytują się od nowa.

Błąd zostaje twardy (bez przycisku):

- dla **naszych domen** z `assets/ca/domeny.json`,
- dla stron z **HSTS**, czyli z listy wbudowanej w silnik i takich, które same zażądały HSTS. LivePL pyta o to silnik zapytaniem `http://host:1/`. Dla hosta z HSTS silnik sam przekierowuje je na https, a port 1 jest zablokowany, więc nic nie wychodzi do sieci. Gdy silnik nie odpowie jasno, LivePL traktuje stronę jak HSTS. Adresy IP nie mają HSTS.

Odcisk i adres bierze z tego, co sam widział przy błędzie. Strona błędu tylko prosi „wejdź”, a LivePL sprawdza jeszcze raz, że to jego strona błędu (`ui/blad.html` w karcie), że to nie nasza domena i nie HSTS. AI (Dostęp dla AI) nie może kliknąć ani wpisać nic na stronie z ostrzeżeniem o certyfikacie. Decyzja zawsze należy do człowieka.

Gdy certyfikat jest dobry, ale dla innej nazwy (np. panel otwarty po IP, a certyfikat jest dla `panel.example.pl`), strona błędu mówi, dla jakiej nazwy jest certyfikat, i ma przycisk „Otwórz panel.example.pl:2222”. Port i ścieżka zostają.

Kod: `src/wyjatki-certyfikatow.js` (obsługa `app.on('certificate-error')`), strona błędu `ui/blad.*`, lista w `ui/panel.js`.

## Bezpieczny DNS

Menu ⋮ → Ustawienia → Bezpieczny DNS: **Wyłączony** (DNS z Windows i sieci), **Cloudflare** albo **Google** (DNS przez HTTPS). Z dostawcą LivePL pyta o adresy stron jego szyfrowany serwer. Gdy dostawca nie odpowiada, wraca do DNS z Windows (`app.configureHostResolver`, tryb `automatic`), więc strony nie przestają działać. Zmiana działa od razu i czyści pamięć DNS silnika.

To pomaga, gdy DNS operatora pamięta stary albo błędny wpis. Przykład: hotspot w telefonie przez jakiś czas odpowiadał, że nowa nazwa serwera nie istnieje, choć rekord był już dodany. Po starcie LivePL silnik przez ok. 5 s sprawdza dostawcę i do tego czasu pyta jeszcze DNS z Windows. „Wyłączony” zostawia silnik bez zmian (tak jak do tej pory). Domyślnie: wyłączony. Ustawienie: `dns` w `ustawienia.json`.

## Dostęp dla AI (Claude i inne)

LivePL może być „dodatkiem” dla AI, tak jak Claude w Chrome. Działa w standardzie MCP, więc obsługuje go Claude (aplikacja i Claude Code), a także Codex, Gemini, Copilot i inne AI, które umieją MCP.

**Włączanie:** menu ⋮ → Ustawienia → Dostęp dla AI. Do wyboru:

| Poziom | AI może |
|---|---|
| Wyłączony (domyślnie) | nic |
| Tylko czytanie | widzieć listę kart, czytać strony, przewijać, robić zrzuty ekranu |
| Czytanie i działanie | do tego otwierać strony, klikać, wpisywać tekst i naciskać klawisze |

**Podłączenie AI** (ten sam ekran):
- „Podłącz do aplikacji Claude” dopisuje LivePL do ustawień Claude (`claude_desktop_config.json`, kopia poprzedniej wersji obok jako `.przed-livepl`). Potem trzeba zamknąć i otworzyć Claude.
- „Kopiuj polecenie dla Claude Code” kopiuje gotowe `claude mcp add livepl …`.
- „Kopiuj ustawienia MCP dla innych AI” kopiuje wpis `mcpServers` do wklejenia w innym programie.

**Bezpieczeństwo:**
- Kiedy AI działa, na dole świeci czerwona lampka „AI” (z opisem, co robi), a ramka strony świeci na czerwono. Karty otwarte przez AI mają znaczek „AI”. Kliknięcie lampki → „Zatrzymaj AI teraz”.
- Programy z `"dlaAI": false` w `programy.json` (Panel serwera, Urząd certyfikacji) są dla AI zamknięte: nie zobaczy ich, nie otworzy i nie przejdzie na ich adres.
- AI nie wpisze nic w pole hasła (ani klawiszem, ani przez wklejanie) i nigdy nie dostaje wartości pól haseł. Zapisane hasła LivePL są poza jego zasięgiem.
- Połączenie idzie tylko przez lokalny potok Windows z jednorazowym kluczem (`dostep-ai.json` w danych LivePL, nowy przy każdym starcie). LivePL nie otwiera żadnego portu sieciowego.
- Kod AI działa w stronie w osobnym, odizolowanym świecie JavaScriptu: strona go nie widzi i nie może go podmienić.

**Jak to działa:** aplikacja AI uruchamia `LivePL.exe src\ai-most.js` ze zmienną `ELECTRON_RUN_AS_NODE=1`. Ten most rozmawia z AI przez MCP, a z działającym LivePL przez potok. Narzędzia: `list_tabs`, `get_page_text`, `read_page`, `find`, `screenshot`, `switch_tab`, `wait`, `scroll`, `open_tab`, `navigate`, `close_tab`, `click`, `type`, `press_key` (`src/ai-narzedzia.js`, wykonanie w `src/dostep-ai.js`). Ramki z tej samej domeny są obsługiwane, z innych domen nie.

## Tłumacz stron

Nie każda strona jest po polsku, a nie każdy zna angielski. LivePL tłumaczy strony na polski w miejscu: układ, zdjęcia i linki zostają, zmienia się tylko tekst.

**Jak się z niego korzysta:**
- Na stronie w innym języku obok adresu jest przycisk **„Przetłumacz”** (najechanie myszą pokazuje „Język strony: angielski. Przetłumaczyć na polski?”). Jedno kliknięcie tłumaczy całą stronę: najpierw to, co widać, potem resztę. Treść, którą strona dopisuje później (np. nowe wiadomości, przewijane listy), też się tłumaczy.
- Na przetłumaczonej stronie ten sam przycisk to **„Pokaż oryginał”**. Wraca dokładnie poprzedni tekst.
- Strzałka obok przycisku otwiera opcje: zmiana języka strony (gdy LivePL źle go rozpozna), **„Zawsze tłumacz z języka: …”** i **„Nigdy nie proponuj na …”** (dla tej strony).
- Kolejne strony otwierane w tej samej karcie po tłumaczeniu tłumaczą się same, aż do „Pokaż oryginał”.
- To samo jest w menu ⋮ („Przetłumacz stronę na polski” / „Pokaż oryginał”) i pod prawym przyciskiem myszy. Tam jest też **„Przetłumacz zaznaczenie”**: tłumaczenie pokazuje się w okienku obok, z przyciskiem „Kopiuj”, a strona się nie zmienia.
- Ustawienia: menu ⋮ → Ustawienia → Tłumacz stron.

**Kto tłumaczy:**

| Silnik | Jak działa | Co wychodzi z komputera |
|---|---|---|
| Ten komputer (domyślnie) | silnik Firefox Translations (Bergamot, WebAssembly) i modele językowe Mozilli | nic z tekstu stron; przy pierwszym użyciu języka LivePL pobiera jego słownik od Mozilli |
| DeepL (do włączenia) | serwis DeepL z własnym kluczem API; najlepsza jakość po polsku | tekst tłumaczonych stron trafia do DeepL SE (Niemcy, UE) |

- **Słowniki Mozilli:** pobierają się przy pierwszym tłumaczeniu z danego języka, z paskiem postępu na przycisku (ok. 25 MB na parę języków). Mozilla ma pary z polskim tylko z angielskim, więc inne języki idą przez angielski: niemiecki → angielski → polski (razem ok. 50 MB przy pierwszym razie). Obsługiwanych jest ok. 55 języków. Każdy plik ma sprawdzaną sumę SHA-256. Słowniki leżą w `%APPDATA%\LivePL\tlumacz\modele`, a w ustawieniach jest ich lista i „Usuń pobrane słowniki”. Pobieranie idzie z `firefox.settings.services.mozilla.com` (lista plików) i `firefox-settings-attachments.cdn.mozilla.net` (pliki).
- **Silnik w komputerze** działa w osobnym procesie (`src/tlumacz-silnik.js`), więc okno nie zwalnia. Tłumaczy ok. 100 słów na sekundę: to, co widać na ekranie, jest po polsku po 2–3 sekundach, a długi artykuł w całości po 1–2 minutach. Zajmuje ok. 300 MB pamięci (ok. 450 MB, gdy tłumaczy przez angielski). Po 10 minutach bez tłumaczenia LivePL go zamyka i zwalnia pamięć. Pliki silnika są w `src/bergamot` (opis i sumy w `src/bergamot/ZRODLO.md`).
- **Tłumaczenie przez angielski** to dwa osobne kroki (np. niemiecki → angielski, potem angielski → polski). Wbudowane w silnik `translateViaPivoting` przy HTML wrzuca cały akapit do pierwszego znacznika, więc LivePL go nie używa.
- Serwer plików Mozilli odrzuca zapytania z nagłówkiem przeglądarki Chrome (błąd 406), dlatego LivePL pobiera słowniki z krótkim `User-Agent: LivePL`, bez wersji i innych danych.
- **DeepL:** Ustawienia → Tłumacz stron → DeepL, potem klucz API. Darmowy klucz (500 tys. znaków miesięcznie) zakłada się na deepl.com/pro-api. Klucz wpisuje się sam i jest zaszyfrowany przez Windows (`safeStorage`, plik `tlumacz-deepl.bin`), jak hasła. „Sprawdź klucz” pokazuje zużycie znaków w tym miesiącu. Z DeepL tłumaczy się tylko to, co widać na ekranie, żeby oszczędzać limit.

**Bezpieczeństwo:**
- Programy LivePL z `programy.json` (poczta, strefa klienta, panele, urząd) zawsze tłumaczy komputer, także gdy włączony jest DeepL.
- Programy z `"dlaAI": false` (Panel serwera) i Urząd (`tylkoZYubiKey`) nie dostają propozycji tłumaczenia i nie działa na nich „Zawsze tłumacz”. Tłumaczą się tylko po wyraźnym kliknięciu (menu ⋮ albo prawy przycisk).
- Skrypt tłumacza (`src/preload-tlumacz.js`) działa w odizolowanym świecie JavaScriptu: strona go nie widzi i nie może go podmienić.
- Tłumacz zmienia tylko tekst na stronie. Nie dotyka pól formularzy (wpisanych wartości, list wyboru, pól haseł), miejsc do edycji, kodu (`code`, `pre`), elementów oznaczonych `translate="no"` lub `notranslate` ani formularzy z polem hasła.
- Strona prosi LivePL o tłumaczenie tylko wtedy, gdy użytkownik je włączył dla tej karty.

**Język strony** LivePL rozpoznaje sam w komputerze: z atrybutu `lang` strony i z kawałka jej tekstu (po piśmie i najczęstszych słowach). Wynik z tekstu wygrywa, bo wiele polskich stron ma w szablonie `lang="en"`.

**Ograniczenia:** ramki w środku strony (`iframe`) nie są tłumaczone. Tłumaczenie maszynowe czasem przesuwa granice linków albo pogrubień o słowo czy dwa (tekst czyta się dobrze, ale link może obejmować sąsiednie słowo); to cecha silnika, który dopasowuje znaczniki do przetłumaczonych słów. Atrybuty (`title`, `alt`, podpowiedzi w polach) zostają w oryginale. Wbudowane w Chromium `Translator` API w silniku LivePL nie działa: Electron nie ma modeli Chrome, a `Translator.availability()` nie odpowiada (sprawdzone w wersji 0.1.10, Chromium 156).

**Test bez silnika:** `LIVEPL_TLUMACZ_TEST=1` włącza atrapę, która zamiast tłumaczyć dopisuje „[pl]” przed każdym kawałkiem tekstu. Przydaje się do sprawdzania samej obsługi strony.

**Wspólna zasada LivePL na wszystkich urządzeniach: tłumaczy urządzenie, DeepL tylko opcjonalnie.**

| Urządzenie | Tłumaczenie w urządzeniu (domyślnie) | Opcjonalnie |
|---|---|---|
| Windows | Bergamot (Firefox Translations) i modele Mozilli | DeepL z własnym kluczem |
| iPhone | Apple Translation | DeepL z własnym kluczem |
| Android | ML Kit (Google, modele w telefonie) | DeepL z własnym kluczem |

Wszędzie obowiązuje to samo: domyślnie tekst nie wychodzi z urządzenia, DeepL włącza się świadomie w ustawieniach, z jasną informacją, co wychodzi, a programy LivePL i programy chronione tłumaczy zawsze urządzenie.

## Lista programów

Programy są w pliku `programy.json` (menu ⋮ → Ustawienia → Edytuj listę programów). Po zmianie uruchom LivePL ponownie.

| Pole | Znaczenie |
|---|---|
| `id` | krótka nazwa bez spacji |
| `nazwa` | pełna nazwa (w podpowiedzi) |
| `skrot` | podpis pod ikoną, jeśli nazwa jest długa |
| `adres` | adres strony |
| `ikona` | `poczta`, `klient`, `lekcje`, `egzamin`, `serwer` albo `strona` |
| `przyStarcie` | `true` – wczytuje się od razu po włączeniu |
| `wTle` | `true` – działa pełną parą także wtedy, gdy oglądasz inny program |
| `obrazek`, `obrazekNoc` | logo programu zamiast ikony (np. `assets/programy/poczta.svg`), nocna wersja opcjonalnie |
| `dlaAI` | `false` – AI nie ma dostępu do tego programu (np. panel serwera) |

## Skróty klawiszowe

| Skrót | Działanie |
|---|---|
| Ctrl+1 … Ctrl+8 | kolejne programy i karty, Ctrl+9 – ostatnia |
| Ctrl+Tab / Ctrl+Shift+Tab | następna / poprzednia |
| Ctrl+T | nowa karta |
| Ctrl+W | zamknij kartę |
| Ctrl+Shift+T | przywróć zamkniętą kartę |
| Ctrl+K, Ctrl+L | adres albo szukaj |
| Ctrl+F, F3 | szukaj na stronie |
| F5 / Ctrl+F5 | odśwież / odśwież bez pamięci podręcznej |
| Alt+← / Alt+→ | wstecz / dalej |
| Ctrl + / Ctrl − / Ctrl+0 | powiększenie |
| Ctrl+P | drukuj |
| Ctrl+J | folder z pobranymi |
| F11 | pełny ekran |
| F12 | narzędzia programisty |

## Gdzie co jest

- `src/main.js` – okno, karty, pobieranie, uprawnienia, zasobnik
- `src/dostep-ai.js`, `src/ai-most.js`, `src/ai-narzedzia.js` – dostęp dla AI (MCP)
- `src/nasze-ca.js` – zaufanie do LivePL Root CA dla naszych domen
- `src/wyjatki-certyfikatow.js` – wejście mimo błędu certyfikatu (wyjątki host:port + odcisk, HSTS)
- `src/bezpieczny-dns.js` – Bezpieczny DNS (DNS przez HTTPS)
- `src/tlumacz.js`, `src/tlumacz-silnik.js`, `src/preload-tlumacz.js`, `src/jezyki.js`, `src/bergamot/` – tłumacz stron
- `ui/` – pasek boczny i górny, pusta karta, ekran błędu, wybór ekranu
- dane przeglądarki (logowania, ustawienia okna): `%APPDATA%\LivePL`

## Do sprawdzenia po zalogowaniu

- Kliknięcie linku `mailto:` ma otwierać nową wiadomość w poczcie (`#mail-compose/to/…`).
- Powiadomienia Windows z poczty zaczną działać po dodaniu skrótu LivePL do menu Start.

## Aktualizacje

LivePL sam sprawdza nowe wersje (15 sekund po starcie, potem co 4 godziny) pod adresem `https://aktualizacje.livepl.eu/livepl/`. Nowa wersja pobiera się w tle, a na pasku wiadomości pojawia się „Nowa wersja LivePL … jest gotowa”. Wystarczy uruchomić program ponownie.

- Każda wersja jest podpisana kluczem Ed25519. LivePL sprawdza podpis, sumę paczki i sumę każdego pliku. Podrobionej wersji nie zainstaluje.
- Przed podmianą zostaje kopia poprzedniej wersji. W menu ⋮ → O programie można do niej wrócić.
- Automatyczne sprawdzanie można wyłączyć w menu ⋮ → Ustawienia.

**Wydanie nowej wersji:**

```
npm run wydanie -- 0.2.0 "Krótki opis zmian"
```

Polecenie podnosi numer wersji, pakuje i podpisuje program w folderze `wydanie/`. Zawartość tego folderu wgrywasz na serwer do `aktualizacje.livepl.eu/livepl/`. Kopia trafia też do `Dokumenty\LivePL\Wydania\LivePL\<wersja>\na-serwer\aktualizacja`.

Klucz do podpisywania leży w `%USERPROFILE%\LivePL-klucze\aktualizacje-prywatny.pem`, celowo poza OneDrive. Nie wysyłaj go nikomu i zrób kopię na pendrive. Bez niego nie wydasz kolejnej wersji, a nowy klucz wymagałby ręcznej aktualizacji każdego LivePL.

Aktualizacje obejmują kod programu (`src`, `ui`, `assets`, `programy.json`). Nowy silnik wymaga nowego instalatora, a LivePL sam o tym powie.

## Instalatory

```
npm run instalator
```

Polecenie buduje trzy pliki do `Dokumenty\LivePL\Wydania\LivePL\<wersja>` (trwa ok. 9 minut, głównie kompresja). Pliki robocze trafiają do folderu tymczasowego, poza OneDrive. Jeśli w `Wydania\LivePL\<wersja>` są już instalatory, budowanie odmawia (`LIVEPL_NADPISZ=1` wymusza nadpisanie), żeby nie podmienić gotowej, np. podpisanej wersji. Inny folder: `npm run instalator -- <folder>`.

- `LivePL-Instalator-<wersja>.exe` (ok. 54 kB z podpisem): instalator internetowy. W trakcie instalacji pobiera pakiet z `https://app.livepl.eu/pobierz/` i sprawdza jego sumę SHA-256, która jest wpisana w instalator. Przy braku internetu albo złym pliku pokazuje komunikat z „Ponów próbę”.
- `LivePL-Instalator-<wersja>-pelny.exe` (ok. 93 MB): ten sam instalator z pakietem w środku, działa bez internetu.
- `na-serwer\LivePL-<wersja>-<8 znaków sumy>.pakiet` (ok. 93 MB): cały LivePL (silnik i kod) w LZMA. Wgrywa się go do `app.livepl.eu/pobierz/` razem z instalatorami. Starych pakietów nie usuwaj z serwera, bo pobrane wcześniej instalatory internetowe wskazują swój. Dzięki sumie w nazwie nowy pakiet nigdy nie nadpisze starego.

**Podpis:** `npm run instalator` zawsze podpisuje certyfikatem Certum do podpisywania kodu, ze znacznikiem czasu Certum: oba instalatory oraz każdy niepodpisany `.exe`/`.dll` w programie (`LivePL.exe` i biblioteki; pliki z podpisem Microsoftu zostają bez zmian). Wcześniej musi działać i być zalogowany SimplySign Desktop (mail i kod z aplikacji SimplySign na telefonie). Budowanie sprawdza certyfikat na samym początku i bez niego od razu się przerywa, a każdy podpis od razu weryfikuje. Bez podpisu zbudujesz tylko wersję próbną do innego folderu (`npm run instalator -- <folder>`), nigdy do `Wydania`.

Potrzebne są Node, kompilator C# z Windows (jest w każdym Windows) i `xz.exe` z Git for Windows (albo ścieżka w `LIVEPL_XZ`). Z silnika wypadają tłumaczenia Chromium poza polskim i angielskim. Gdy języka systemu brakuje, Chromium bierze angielski. Budowanie samo rozpakowuje gotowy pakiet kodem instalatora i porównuje każdy plik z oryginałem, a przy różnicy przerywa się z błędem.

Stronę app.livepl.eu przygotowuje `node ../app-livepl/przygotuj.mjs ..\Wydania\LivePL\x.y.z\LivePL-Instalator-x.y.z.exe <folder>`. Jeśli obok leżą pełny instalator i pakiet (także w `na-serwer`), strona dostaje przycisk do instalatora internetowego, link „Pełny instalator” i sumy obu plików, a do `pobierz/` trafiają wszystkie trzy.

Instalator kopiuje nową wersję obok (`LivePL.nowa-…`) i dopiero na końcu podmienia folder programu. Gdy coś zablokuje plik (np. antywirus LivePL.exe), poprzednia wersja zostaje, a komunikat mówi, co kliknąć w Zabezpieczeniach Windows. Krótkie blokady przy skanowaniu nowego pliku instalator sam ponawia.

Wpis w „Aplikacjach i funkcjach” (rejestr, `HKCU\…\Uninstall\LivePL`): gdy zapis się nie uda (np. „Odmowa dostępu” przez pozostałość po wcześniejszej instalacji), instalator raz usuwa stary wpis i zapisuje go od nowa. Jeśli i to nie wyjdzie, instalacja i tak się kończy (LivePL działa), a komunikat mówi, jak dodać go do listy programów. Gdy instalacja się nie uda, instalator usuwa pozostałości (foldery `LivePL.nowa-…`/`LivePL.stara-…` i, jeśli nie został działający LivePL, jego wpis w rejestrze) i proponuje ponowne uruchomienie komputera przyciskiem „Uruchom ponownie komputer” (Windows daje 15 sekund, przerwanie: `shutdown /a`). Wpis, którego nie da się usunąć, komunikat każe usunąć w `regedit`.

PowerShell (certyfikaty, urząd LivePL, YubiKey, BitLocker) LivePL uruchamia jawnie przez `-Command`, bez zakodowanych poleceń i bez `-ExecutionPolicy Bypass`: programy antywirusowe dla firm, np. Bitdefender Endpoint Security, uznają zakodowane polecenia za zachowanie wirusa i usuwają cały program.

Test instalatora bez dotykania systemu: `--cel=<folder>` instaluje do tego folderu bez skrótów, wpisu w Windows i certyfikatu. Zmienna `LIVEPL_PAKIET_ADRES` (tylko `https://` albo `http://127.0.0.1`) każe instalatorowi internetowemu pobrać pakiet z innego adresu, a suma i tak musi się zgadzać.

## Licencja

LivePL jest otwartym oprogramowaniem na licencji MIT (plik `LICENSE`). Autor: Karol Kowalski. Copyright © 2026 Live Production Karol Kowalski. Te same dane są w `LivePL.exe` i w instalatorach (firma Live Production, prawa autorskie), we wpisie „Programy i funkcje” (wydawca) i w okienku „Licencje i podziękowania”. Programy podpisujemy certyfikatem Certum Open Source Code Signing wystawionym na Karola Kowalskiego, więc dane w programie mają się z nim zgadzać.

Plik `LICENSE` nie trafia do paczek aktualizacji, bo zainstalowane LivePL przyjmują w paczce tylko `package.json`, `programy.json`, `README.md` i foldery `src`, `ui`, `assets` (`src/aktualizacja-wgraj.js`). Treść licencji jest więc też w `ui/licencje.html`.
