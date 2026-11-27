'use strict';

// LivePL na Linuxie: autostart, wpis w menu programów (AppImage) i ścieżki uruchamiania.
// Wersja .deb ma wpis w menu i typ pliku .livepl-dodatek z pakietu (/usr/share), AppImage dopisuje je
// sam w folderze użytkownika (~/.local/share), bo nie ma instalatora.

const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFile } = require('child_process');

const LINUX = process.platform === 'linux';
const KATALOG = path.join(__dirname, '..');
const DOM = os.homedir();
const XDG_CONFIG = process.env.XDG_CONFIG_HOME || path.join(DOM, '.config');
const XDG_DATA = process.env.XDG_DATA_HOME || path.join(DOM, '.local', 'share');
const PLIK_AUTOSTARTU = path.join(XDG_CONFIG, 'autostart', 'livepl.desktop');
const PAKIET_DEB = '/usr/share/applications/livepl.desktop';

// Program do wpisania w autostarcie i w ustawieniach AI: plik AppImage (jego ścieżka się nie zmienia,
// a pliki w środku leżą w folderze tymczasowym), polecenie /usr/bin/livepl z pakietu .deb albo sam Electron.
function uruchamiacz() {
  if (process.env.APPIMAGE) return process.env.APPIMAGE;
  if (fs.existsSync('/usr/bin/livepl') && fs.existsSync(PAKIET_DEB)) return '/usr/bin/livepl';
  return process.execPath;
}

// zamknięcie w cudzysłów według specyfikacji plików .desktop
function cytuj(s) {
  return '"' + String(s).replace(/(["`$\\])/g, '\\$1') + '"';
}

function polecenie(argumenty) {
  const u = uruchamiacz();
  const reszta = u === process.execPath && !process.env.APPIMAGE && process.defaultApp ? [path.resolve(KATALOG), ...argumenty] : argumenty;
  return [u, ...reszta].map(cytuj).join(' ');
}

function wpisDesktop(argumenty, dodatki = '') {
  return [
    '[Desktop Entry]',
    'Type=Application',
    'Name=LivePL',
    'GenericName=Przeglądarka',
    'Comment=Poczta, strefa klienta i programy Live Production w jednym oknie',
    'Exec=' + polecenie(argumenty),
    'Icon=livepl',
    'Terminal=false',
    'StartupWMClass=LivePL',
    dodatki
  ].filter(Boolean).join('\n') + '\n';
}

function zapiszJesliInny(plik, tresc) {
  try { if (fs.readFileSync(plik, 'utf8') === tresc) return false; } catch { /* nie ma */ }
  fs.mkdirSync(path.dirname(plik), { recursive: true });
  fs.writeFileSync(plik, tresc);
  return true;
}

// ---------------------------------------------------------------- autostart (XDG, ~/.config/autostart)

function czyAutostart() {
  try {
    const t = fs.readFileSync(PLIK_AUTOSTARTU, 'utf8');
    return !/^Hidden\s*=\s*true/mi.test(t) && !/^X-GNOME-Autostart-enabled\s*=\s*false/mi.test(t);
  } catch {
    return false;
  }
}

function ustawAutostart(wl, argumenty) {
  if (!wl) {
    fs.rmSync(PLIK_AUTOSTARTU, { force: true });
    return;
  }
  zapiszJesliInny(PLIK_AUTOSTARTU, wpisDesktop(argumenty, 'X-GNOME-Autostart-enabled=true'));
}

// ---------------------------------------------------------------- AppImage: wpis w menu programów

const TYP_DODATKU = `<?xml version="1.0" encoding="UTF-8"?>
<mime-info xmlns="http://www.freedesktop.org/standards/shared-mime-info">
  <mime-type type="application/x-livepl-dodatek">
    <comment>Wtyczka LivePL</comment>
    <glob pattern="*.livepl-dodatek"/>
  </mime-type>
</mime-info>
`;

function bezBledu(polecenie, argumenty) {
  execFile(polecenie, argumenty, { timeout: 20000 }, () => { /* nie każdy Linux ma te narzędzia */ });
}

// Przy każdym starcie z pliku AppImage: wpis w menu programów i typ pliku wtyczki dla tego użytkownika.
// Zmienia pliki tylko wtedy, gdy są inne (np. po przeniesieniu pliku AppImage). Pomija, gdy jest pakiet .deb.
function integrujAppImage() {
  if (!LINUX || !process.env.APPIMAGE || fs.existsSync(PAKIET_DEB)) return;
  try {
    const wpis = wpisDesktop(['%U'], 'Categories=Network;WebBrowser;\nMimeType=application/x-livepl-dodatek;\nKeywords=poczta;przeglądarka;livepl;\nTryExec=' + process.env.APPIMAGE);
    // %U ma zostać bez cudzysłowu (to znacznik dla menu programów, nie ścieżka)
    const zmienione = zapiszJesliInny(path.join(XDG_DATA, 'applications', 'livepl.desktop'), wpis.replace('"%U"', '%U'));
    for (const r of [48, 128, 256, 512]) {
      const ikona = path.join(XDG_DATA, 'icons', 'hicolor', `${r}x${r}`, 'apps', 'livepl.png');
      const zrodlo = path.join(KATALOG, 'assets', 'linux', `livepl-${r}.png`);
      if (fs.existsSync(ikona) && fs.statSync(ikona).size === fs.statSync(zrodlo).size) continue;
      fs.mkdirSync(path.dirname(ikona), { recursive: true });
      fs.copyFileSync(zrodlo, ikona);
    }
    const typ = zapiszJesliInny(path.join(XDG_DATA, 'mime', 'packages', 'livepl.xml'), TYP_DODATKU);
    if (zmienione) bezBledu('update-desktop-database', [path.join(XDG_DATA, 'applications')]);
    if (typ) bezBledu('update-mime-database', [path.join(XDG_DATA, 'mime')]);
  } catch (e) {
    console.error('LivePL: wpis w menu programów:', e.message);
  }
}

module.exports = { LINUX, uruchamiacz, czyAutostart, ustawAutostart, integrujAppImage };
