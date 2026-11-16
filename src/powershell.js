'use strict';

// Uruchamianie krótkich skryptów PowerShell (bez okna). Polecenie idzie zakodowane, bez kłopotów
// z cudzysłowami, a parametry przez zmienne środowiskowe. Błąd wraca jako „BLAD:kod:opis”.

const { execFile } = require('child_process');

function powershell(skrypt, env = {}, czas = 20000) {
  const calosc = "$ProgressPreference='SilentlyContinue';[Console]::OutputEncoding=[Text.Encoding]::UTF8;$ErrorActionPreference='Stop';"
    + `try { ${skrypt} } catch { $e = $_.Exception; while ($e.InnerException) { $e = $e.InnerException }; `
    + "[Console]::Out.Write('BLAD:' + ('{0:X8}' -f $e.HResult) + ':' + $e.Message); exit 1 }";
  const kod = Buffer.from(calosc, 'utf16le').toString('base64');
  return new Promise((ok, zle) => {
    execFile('powershell.exe', ['-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', kod],
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

module.exports = { powershell, anulowano };
