'use strict';

// Instalator i odinstalowanie LivePL: ten sam program w trybie „--instalator” albo „--odinstaluj”.
// Instalacja tylko dla bieżącego użytkownika, bez uprawnień administratora:
//   pliki      %LOCALAPPDATA%\Programs\LivePL (tu działają też automatyczne aktualizacje)
//   skróty     menu Start i (opcjonalnie) pulpit
//   wpis       HKCU\...\Uninstall\LivePL, czyli „Aplikacje i funkcje” w Ustawieniach Windows
// Dane użytkownika (hasła, ustawienia) zostają w %APPDATA%\LivePL; odinstalowanie usuwa je tylko na życzenie.
//
// Do testów:  --cel=<folder>  instaluje gdzie indziej, a skróty kładzie w <folder>\_skroty, bez wpisu w rejestrze.

const { app, BrowserWindow, ipcMain, nativeTheme, shell } = require('electron');
const { execFile, spawn } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const zaufanie = require('./zaufanie');

const KLUCZ_ODINSTALOWANIA = 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\LivePL';
const WERSJA = (() => {
  try { return JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'package.json'), 'utf8')).version; } catch { return ''; }
})();

function argument(nazwa) {
  const a = process.argv.find(x => x.startsWith('--' + nazwa + '='));
  return a ? a.slice(nazwa.length + 3) : null;
}

const TEST_CEL = argument('cel');
const CEL = TEST_CEL ? path.resolve(TEST_CEL) : path.join(process.env.LOCALAPPDATA || path.join(os.homedir(), 'AppData', 'Local'), 'Programs', 'LivePL');
const EXE = path.join(CEL, 'LivePL.exe');
const ZRODLO = path.dirname(process.execPath); // rozpakowana paczka (tryb instalatora) albo folder programu (odinstalowanie)
const SKROTY = TEST_CEL
  ? { start: path.join(CEL, '_skroty', 'Start'), pulpit: path.join(CEL, '_skroty', 'Pulpit') }
  : { start: path.join(app.getPath('appData'), 'Microsoft', 'Windows', 'Start Menu', 'Programs'), pulpit: app.getPath('desktop') };

let okno = null;

function wykonaj(plik, argumenty, env = {}) {
  return new Promise((ok, zle) => execFile(plik, argumenty, { windowsHide: true, env: { ...process.env, ...env } },
    (blad, wyjscie) => (blad ? zle(blad) : ok(String(wyjscie || '')))));
}

function wyslij(kanal, dane) {
  if (okno && !okno.isDestroyed()) okno.webContents.send(kanal, dane);
}

// ---------------------------------------------------------------- stan

function zainstalowanaWersja() {
  try {
    return JSON.parse(fs.readFileSync(path.join(CEL, 'resources', 'app', 'package.json'), 'utf8')).version || '?';
  } catch {
    return null;
  }
}

// LivePL uruchomiony z folderu instalacji (inne kopie, np. z folderu projektu, nas nie obchodzą).
// Odinstalowanie działa z tego samego folderu, więc własne procesy (główny i pomocnicze, które zna
// Electron) pomijamy. Lista przez Get-Process, nie WMI: przeciążone WMI potrafi odrzucać zapytania.
async function dzialajace() {
  const wlasne = new Set([process.pid, ...app.getAppMetrics().map(m => m.pid)]);
  try {
    const w = await wykonaj('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
      "Get-Process -Name LivePL -ErrorAction SilentlyContinue | Where-Object { $_.Path -and $_.Path.StartsWith($env:LIVEPL_CEL + '\\', 'OrdinalIgnoreCase') } | ForEach-Object { $_.Id }"],
    { LIVEPL_CEL: CEL });
    return w.split(/\s+/).map(Number).filter(p => p && !wlasne.has(p));
  } catch {
    return [];
  }
}

async function zamknijDzialajace() {
  for (const pid of await dzialajace()) {
    try { process.kill(pid); } catch { /* już zamknięty */ }
  }
  for (let i = 0; i < 20 && (await dzialajace()).length; i++) await new Promise(r => setTimeout(r, 250));
}

// ---------------------------------------------------------------- kopiowanie z postępem

function lista(folder, baza = folder, wynik = []) {
  for (const w of fs.readdirSync(folder, { withFileTypes: true })) {
    const pelna = path.join(folder, w.name);
    if (w.isDirectory()) lista(pelna, baza, wynik);
    else wynik.push({ sciezka: path.relative(baza, pelna), rozmiar: fs.statSync(pelna).size });
  }
  return wynik;
}

async function kopiuj(postep) {
  const pliki = lista(ZRODLO);
  const razem = pliki.reduce((s, p) => s + p.rozmiar, 0) || 1;
  let skopiowano = 0;
  for (const [i, p] of pliki.entries()) {
    const cel = path.join(CEL, p.sciezka);
    fs.mkdirSync(path.dirname(cel), { recursive: true });
    await fs.promises.copyFile(path.join(ZRODLO, p.sciezka), cel);
    skopiowano += p.rozmiar;
    if (i % 3 === 0 || i === pliki.length - 1) postep(skopiowano / razem, `Kopiuję pliki: ${i + 1} z ${pliki.length}`);
  }
  return razem;
}

// ---------------------------------------------------------------- skróty i wpis w Windows

function skrot(folder) {
  fs.mkdirSync(folder, { recursive: true });
  // „create” tworzy albo nadpisuje („replace” działa tylko na istniejący skrót)
  return shell.writeShortcutLink(path.join(folder, 'LivePL.lnk'), 'create', {
    target: EXE,
    cwd: CEL,
    description: 'LivePL: przeglądarka Live Production',
    icon: EXE,
    iconIndex: 0,
    appUserModelId: 'pl.liveproduction.livepl'
  });
}

async function zarejestruj(rozmiar) {
  if (TEST_CEL) return;
  const wpisy = [
    ['DisplayName', 'REG_SZ', 'LivePL'],
    ['DisplayVersion', 'REG_SZ', WERSJA],
    ['Publisher', 'REG_SZ', 'Live Production'],
    ['DisplayIcon', 'REG_SZ', `"${EXE}",0`],
    ['InstallLocation', 'REG_SZ', CEL],
    ['UninstallString', 'REG_SZ', `"${EXE}" --odinstaluj`],
    ['URLInfoAbout', 'REG_SZ', 'https://liveproduction.pl/'],
    ['NoModify', 'REG_DWORD', '1'],
    ['NoRepair', 'REG_DWORD', '1'],
    ['EstimatedSize', 'REG_DWORD', String(Math.round(rozmiar / 1024))]
  ];
  for (const [nazwa, typ, wartosc] of wpisy) {
    await wykonaj('reg.exe', ['add', KLUCZ_ODINSTALOWANIA, '/v', nazwa, '/t', typ, '/d', wartosc, '/f']);
  }
}

// ---------------------------------------------------------------- instalacja

async function instaluj(opcje) {
  const postep = (ulamek, opis) => wyslij('postep', { ulamek: Math.max(0, Math.min(1, ulamek)), opis });
  try {
    postep(0, 'Zamykam LivePL, jeśli działa…');
    await zamknijDzialajace();
    postep(0.01, 'Przygotowuję folder…');
    if (fs.existsSync(CEL)) {
      // stara wersja znika w całości, żeby nie zostały po niej pliki (dane użytkownika są gdzie indziej)
      try {
        fs.rmSync(CEL, { recursive: true, force: true, maxRetries: 5, retryDelay: 300 });
      } catch {
        throw new Error('Nie mogę zastąpić poprzedniej wersji. Zamknij LivePL i spróbuj jeszcze raz.');
      }
    }
    const rozmiar = await kopiuj((u, opis) => postep(0.02 + u * 0.9, opis));
    postep(0.94, 'Tworzę skróty…');
    skrot(SKROTY.start);
    if (opcje && opcje.pulpit) skrot(SKROTY.pulpit);
    postep(0.97, 'Rejestruję LivePL w Windows…');
    await zarejestruj(rozmiar);
    // urząd LivePL tylko dla tego użytkownika: Windows sam pokazuje okno z odciskiem do potwierdzenia.
    // Brak zgody nie psuje instalacji, LivePL zaproponuje to jeszcze raz po starcie.
    let ca = null;
    if (opcje && opcje.ca) {
      if (TEST_CEL) {
        ca = 'test'; // testy nie ruszają magazynu certyfikatów Windows
      } else {
        postep(0.98, 'Dodaję urząd LivePL do Windows. Potwierdź „Tak” w oknie Windows…');
        try {
          const s = await zaufanie.zainstalujDlaMnie();
          ca = s && s.zaufany ? 'ok' : 'blad';
        } catch (e) {
          ca = e.message === 'anulowano' ? 'anulowano' : 'blad';
        }
      }
    }
    postep(1, 'Gotowe');
    return { ok: true, cel: CEL, ca };
  } catch (e) {
    return { ok: false, blad: e.message || String(e) };
  }
}

async function odinstaluj(opcje) {
  const postep = (ulamek, opis) => wyslij('postep', { ulamek, opis });
  try {
    postep(0.1, 'Zamykam LivePL…');
    await zamknijDzialajace();
    postep(0.4, 'Usuwam skróty…');
    for (const f of [SKROTY.start, SKROTY.pulpit]) {
      try {
        const lnk = path.join(f, 'LivePL.lnk');
        if (fs.existsSync(lnk) && path.resolve(shell.readShortcutLink(lnk).target).toLowerCase() === EXE.toLowerCase()) fs.rmSync(lnk);
      } catch { /* skrót już usunięty albo inny */ }
    }
    postep(0.6, 'Usuwam wpis z Windows…');
    if (!TEST_CEL) await wykonaj('reg.exe', ['delete', KLUCZ_ODINSTALOWANIA, '/f']).catch(() => {});
    if (opcje && opcje.dane) {
      postep(0.8, 'Usuwam Twoje dane LivePL…');
      fs.rmSync(path.join(app.getPath('appData'), 'LivePL'), { recursive: true, force: true });
    }
    postep(1, 'Gotowe');
    return { ok: true };
  } catch (e) {
    return { ok: false, blad: e.message || String(e) };
  }
}

// pliki programu usuwamy po zamknięciu okna (działający LivePL.exe nie może skasować sam siebie)
// cmd.exe, bo odłączony PowerShell bez konsoli potrafi w ogóle nie wystartować. Polecenie idzie dosłownie
// (windowsVerbatimArguments), żeby Node nie dokładał swoich cudzysłowów. Do 20 prób co sekundę:
// procesy pomocnicze Electron zwalniają pliki chwilę po zamknięciu okna.
function usunPoZamknieciu() {
  if (!fs.existsSync(EXE) || /["%]/.test(CEL)) return; // tylko folder, w którym naprawdę jest LivePL
  const polecenie = `for /l %i in (1,1,20) do @(if exist "${CEL}" (ping -n 2 127.0.0.1 >nul & rmdir /s /q "${CEL}"))`;
  spawn('cmd.exe', ['/d', '/c', polecenie], {
    detached: true, stdio: 'ignore', windowsHide: true, windowsVerbatimArguments: true, cwd: os.tmpdir()
  }).unref();
}

// ---------------------------------------------------------------- okno

function uruchom(tryb) {
  app.setName('LivePL');
  app.setPath('userData', path.join(os.tmpdir(), 'LivePL-instalator-dane'));
  app.setAppUserModelId('pl.liveproduction.livepl.instalator');
  let odinstalowano = false;

  ipcMain.handle('instalator:stan', async () => {
    const [dziala, ca] = await Promise.all([dzialajace(), zaufanie.stan().catch(() => null)]);
    return { tryb, wersja: WERSJA, zainstalowana: zainstalowanaWersja(), cel: CEL, test: !!TEST_CEL, dziala: dziala.length > 0, ca };
  });
  ipcMain.handle('instalator:instaluj', (e, opcje) => instaluj(opcje));
  ipcMain.handle('instalator:odinstaluj', async (e, opcje) => {
    const w = await odinstaluj(opcje);
    odinstalowano = w.ok;
    return w;
  });
  ipcMain.on('instalator:uruchom-livepl', () => {
    if (fs.existsSync(EXE)) spawn(EXE, [], { detached: true, stdio: 'ignore', cwd: CEL }).unref();
    app.quit();
  });
  ipcMain.on('instalator:zamknij', () => app.quit());
  ipcMain.on('instalator:minimalizuj', () => { if (okno) okno.minimize(); });

  app.on('window-all-closed', () => app.quit());
  app.on('will-quit', () => { if (odinstalowano) usunPoZamknieciu(); });

  app.whenReady().then(() => {
    const ciemny = nativeTheme.shouldUseDarkColors;
    okno = new BrowserWindow({
      width: 820,
      height: 520,
      center: true,
      resizable: false,
      maximizable: false,
      frame: false,
      show: false,
      title: tryb === 'odinstaluj' ? 'Odinstaluj LivePL' : 'Instalator LivePL',
      backgroundColor: ciemny ? '#0d0f12' : '#e9ecef',
      icon: path.join(__dirname, '..', 'assets', 'icon.ico'),
      webPreferences: { preload: path.join(__dirname, 'preload-instalator.js'), contextIsolation: true, sandbox: true }
    });
    okno.setMenu(null);
    okno.loadFile(path.join(__dirname, '..', 'ui', 'instalator.html'));
    okno.once('ready-to-show', () => {
      okno.show();
      okno.focus();
      app.focus({ steal: true });
    });
    okno.webContents.on('will-navigate', (e) => e.preventDefault());
    okno.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  });
}

module.exports = { uruchom };
