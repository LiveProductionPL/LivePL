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

- **Kanały po lewej.** Same ikony (logo Poczty, Live Production, TVML), bez podpisów; nazwa jest w podpowiedzi. Liczba przy Poczcie to nieprzeczytane wiadomości. Ta sama liczba pojawia się na ikonie na pasku zadań.
- **Strona się nie wczytała?** Zamiast suchego komunikatu jest telewizorek z „przerwą w nadawaniu”, prosty opis, co się stało (brak internetu, zerwane połączenie, zły certyfikat…), rady i odliczanie do kolejnej próby. Szczegóły techniczne są pod „Szczegóły techniczne”.
- **Dostęp dla AI.** Claude i inne AI mogą korzystać z LivePL jak z dodatku w Chrome. Opis niżej.
- **Pasek wiadomości na dole.** Pokazuje powiadomienia z naszych programów (np. nowa wiadomość w poczcie), wzrost liczby nieprzeczytanych i pobrane pliki, każde z godziną. Kliknięcie przenosi do programu albo otwiera plik.
- **Adres albo szukaj (Ctrl+K)** w prawym dolnym rogu. Pole rozwija się na cały pasek, gdy coś wpisujesz.
- **Inne strony otwierają się jako karty** pod kanałami. Linki z poczty trafiają do nowej karty, a poczta zostaje na miejscu.
- **Pobrane pliki zapisują się same** w zwykłym folderze `Pobrane` (Ctrl+J go otwiera).
- **Po zamknięciu okna program działa dalej** przy zegarze, więc poczta sprawdza nowe wiadomości. Program zamykasz prawym przyciskiem na tej ikonie. Działanie w tle można wyłączyć w menu ⋮ → Ustawienia.
- **Dzień i noc** przełączają się same razem z trybem Windows: kolory, logo w rogu, ikona okna, ikona przy zegarze i ikona skrótu na pulpicie.
- **Udostępnianie ekranu** (np. na lekcjach w Sevetoo) otwiera okienko wyboru ekranu albo okna.
- **Prawy przycisk myszy:** kopiuj, wklej, poprawianie literówek, otwieranie linku w nowej karcie albo w zwykłej przeglądarce.

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

Test instalatora bez dotykania systemu: `--cel=<folder>` instaluje do tego folderu bez skrótów, wpisu w Windows i certyfikatu. Zmienna `LIVEPL_PAKIET_ADRES` (tylko `https://` albo `http://127.0.0.1`) każe instalatorowi internetowemu pobrać pakiet z innego adresu, a suma i tak musi się zgadzać.

## Licencja

LivePL jest otwartym oprogramowaniem na licencji MIT (plik `LICENSE`). Autor: Karol Kowalski. Copyright © 2026 Live Production Karol Kowalski. Te same dane są w `LivePL.exe` i w instalatorach (firma Live Production, prawa autorskie), we wpisie „Programy i funkcje” (wydawca) i w okienku „Licencje i podziękowania”. Programy podpisujemy certyfikatem Certum Open Source Code Signing wystawionym na Karola Kowalskiego, więc dane w programie mają się z nim zgadzać.

Plik `LICENSE` nie trafia do paczek aktualizacji, bo zainstalowane LivePL przyjmują w paczce tylko `package.json`, `programy.json`, `README.md` i foldery `src`, `ui`, `assets` (`src/aktualizacja-wgraj.js`). Treść licencji jest więc też w `ui/licencje.html`.
