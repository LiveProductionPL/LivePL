'use strict';

// Czy YubiKey jest włożony (urządzenie USB firmy Yubico, VID 1050). Jeden lekki proces PowerShell
// w tle: sprawdza raz, potem budzi się tylko przy podłączeniu albo odłączeniu urządzenia
// (Win32_DeviceChangeEvent) i wypisuje „1” albo „0”, gdy coś się zmieniło. Bez uprawnień administratora.

const { spawn } = require('child_process');

const SKRYPT = [
  "$ErrorActionPreference = 'SilentlyContinue'",
  "$ProgressPreference = 'SilentlyContinue'",
  "function Jest { [bool](Get-CimInstance Win32_PnPEntity -Filter \"DeviceID LIKE 'USB\\\\VID_1050%'\" | Select-Object -First 1) }",
  '$poprzedni = $null',
  'Register-CimIndicationEvent -ClassName Win32_DeviceChangeEvent -SourceIdentifier livepl_usb | Out-Null',
  'while ($true) {',
  '  $j = Jest',
  "  if ($j -ne $poprzedni) { [Console]::Out.WriteLine($(if ($j) { '1' } else { '0' })); [Console]::Out.Flush(); $poprzedni = $j }",
  '  $e = Wait-Event -SourceIdentifier livepl_usb -Timeout 60',
  '  if ($e) { Start-Sleep -Milliseconds 800; Get-Event -SourceIdentifier livepl_usb | Remove-Event }',
  '}'
].join('\n');

let proces = null;
let koniec = false;
let obecny = false;
let ponowienia = 0;

function obserwuj(sluchacz) {
  if (process.platform !== 'win32' || proces) return;
  const kod = Buffer.from(SKRYPT, 'utf16le').toString('base64');
  proces = spawn('powershell.exe', ['-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', kod], { windowsHide: true });
  let bufor = '';
  proces.stdout.on('data', (d) => {
    bufor += d.toString();
    let i;
    while ((i = bufor.indexOf('\n')) >= 0) {
      const linia = bufor.slice(0, i).trim();
      bufor = bufor.slice(i + 1);
      if (linia !== '1' && linia !== '0') continue;
      const jest = linia === '1';
      if (jest !== obecny) {
        obecny = jest;
        try { sluchacz(jest); } catch { /* bez znaczenia */ }
      }
    }
  });
  proces.on('exit', () => {
    proces = null;
    // proces padł (np. po uśpieniu komputera): uruchamiamy go ponownie, ale nie w nieskończoność
    if (!koniec && ponowienia++ < 5) setTimeout(() => obserwuj(sluchacz), 5000);
  });
  proces.on('error', () => { /* brak PowerShella: YubiKey po prostu nie zostanie wykryty */ });
}

function zatrzymaj() {
  koniec = true;
  if (proces) {
    try { proces.kill(); } catch { /* już zamknięty */ }
    proces = null;
  }
}

module.exports = { obserwuj, zatrzymaj, obecny: () => obecny };
