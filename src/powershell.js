'use strict';

// Uruchamianie krótkich skryptów PowerShell (bez okna). Parametry idą przez zmienne środowiskowe.
// Błąd wraca jako „BLAD:kod:opis”.
//
// Skrypt idzie jawnie w -Command, a nie zakodowany (-EncodedCommand), i bez -ExecutionPolicy Bypass:
// programy antywirusowe dla firm (np. Bitdefender Endpoint Security, 23.11.2026) uznają zakodowane polecenia
// PowerShell za zachowanie wirusa i usuwają cały LivePL. -Command nie podlega zasadom wykonywania skryptów,
// więc Bypass nie był potrzebny. Node sam cytuje argument według reguł Windows, a powershell.exe odczytuje
// go z powrotem bez zmian (cudzysłowy i ukośniki też, sprawdzone).

const { execFile } = require('child_process');

// argumenty powershell.exe dla jawnego skryptu (także dla stałego procesu w yubikey.js)
function argumenty(skrypt) {
  return ['-NoLogo', '-NoProfile', '-NonInteractive', '-Command', skrypt];
}

function powershell(skrypt, env = {}, czas = 20000) {
  const calosc = "$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8;$ErrorActionPreference='Stop';"
    + `try { ${skrypt} } catch { $e = $_.Exception; while ($e.InnerException) { $e = $e.InnerException }; `
    + "[Console]::Out.Write('BLAD:' + ('{0:X8}' -f $e.HResult) + ':' + $e.Message); exit 1 }";
  return new Promise((ok, zle) => {
    execFile('powershell.exe', argumenty(calosc),
      { env: { ...process.env, ...env }, windowsHide: true, timeout: czas, maxBuffer: 4 << 20 },
      (blad, wyjscie) => {
        const tekst = String(wyjscie || '').trim();
        if (tekst.startsWith('BLAD:')) {
          const [, kodBledu, ...opis] = tekst.split(':');
          const e = new Error(opis.join(':').trim());
          e.kod = kodBledu;
          zle(e);
        } else if (blad) {
          const e = new Error(blad.killed ? 'przekroczono czas' : blad.message);
          e.przekroczono = !!blad.killed;
          zle(e);
        } else {
          ok(tekst);
        }
      });
  });
}

// anulowanie przez użytkownika (okno Windows, UAC, PIN)
const anulowano = (e) => /^(8010006E|800704C7)$/.test(String(e.kod || '')) || /anulowan|cancel/i.test(String(e.message || ''));

module.exports = { powershell, anulowano, argumenty };
