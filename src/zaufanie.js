'use strict';

// Zaufanie Windows do urzędu certyfikacji LivePL.
// LivePL ufa naszemu urzędowi sam, ale Windows, Edge, Chrome, Outlook i inne programy dopiero wtedy,
// gdy LivePL Root CA R1 jest w „Zaufanych głównych urzędach certyfikacji”. Instaluje to użytkownik:
// dla siebie (Windows pokazuje swoje okno z odciskiem do potwierdzenia) albo dla całego komputera
// (zgoda administratora). LivePL CA 1 trafia do „Pośrednich urzędów”, żeby łańcuch składał się od razu.

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { powershell, anulowano } = require('./powershell');

const FOLDER = path.join(__dirname, '..', 'assets', 'ca');
const PLIK_ROOT = path.join(FOLDER, 'livepl-root-ca-r1.crt');
const PLIK_CA1 = path.join(FOLDER, 'posrednie', 'livepl-ca-1.crt');

function odciski() {
  const root = new crypto.X509Certificate(fs.readFileSync(PLIK_ROOT));
  const ca1 = new crypto.X509Certificate(fs.readFileSync(PLIK_CA1));
  return {
    root: root.fingerprint.replace(/:/g, ''),
    ca1: ca1.fingerprint.replace(/:/g, ''),
    rootSha256: root.fingerprint256,
    rootDo: new Date(root.validTo).getTime()
  };
}

let ostatni = null;

async function stan() {
  if (process.platform !== 'win32') return null;
  const o = odciski();
  const json = await powershell(
    '[pscustomobject]@{ ' +
    "uzytkownik = [bool](Get-ChildItem Cert:\\CurrentUser\\Root | Where-Object Thumbprint -eq $env:LIVEPL_ROOT); " +
    "komputer = Test-Path ('Cert:\\LocalMachine\\Root\\' + $env:LIVEPL_ROOT); " +
    "posredni = (Test-Path ('Cert:\\CurrentUser\\CA\\' + $env:LIVEPL_CA1)) -or (Test-Path ('Cert:\\LocalMachine\\CA\\' + $env:LIVEPL_CA1)) " +
    '} | ConvertTo-Json -Compress',
    { LIVEPL_ROOT: o.root, LIVEPL_CA1: o.ca1 }
  );
  const s = JSON.parse(json);
  ostatni = {
    sprawdzono: Date.now(),
    zaufany: !!(s.uzytkownik || s.komputer),
    komputer: !!s.komputer,
    uzytkownik: !!s.uzytkownik && !s.komputer,
    posredni: !!s.posredni,
    odcisk: o.rootSha256,
    waznyDo: o.rootDo
  };
  return ostatni;
}

const ostatniStan = () => ostatni;

// dla zalogowanego użytkownika: Windows sam pyta „Czy chcesz zainstalować ten certyfikat?”
async function zainstalujDlaMnie() {
  try {
    await powershell(
      'Import-Certificate -FilePath $env:LIVEPL_PLIK_CA1 -CertStoreLocation Cert:\\CurrentUser\\CA | Out-Null; ' +
      'Import-Certificate -FilePath $env:LIVEPL_PLIK_ROOT -CertStoreLocation Cert:\\CurrentUser\\Root | Out-Null',
      { LIVEPL_PLIK_ROOT: PLIK_ROOT, LIVEPL_PLIK_CA1: PLIK_CA1 },
      180000
    );
  } catch (e) {
    throw new Error(anulowano(e) ? 'anulowano' : e.message);
  }
  return stan();
}

// dla całego komputera: jedno okno zgody administratora (UAC), certutil w obu magazynach
async function zainstalujDlaKomputera() {
  try {
    await powershell(
      "$a = '/c certutil -addstore -f Root \"' + $env:LIVEPL_PLIK_ROOT + '\" && certutil -addstore -f CA \"' + $env:LIVEPL_PLIK_CA1 + '\"'; " +
      '$p = Start-Process -FilePath cmd.exe -ArgumentList $a -Verb RunAs -Wait -PassThru -WindowStyle Hidden; ' +
      "if ($p.ExitCode -ne 0) { throw ('certutil zakończył się kodem ' + $p.ExitCode) }",
      { LIVEPL_PLIK_ROOT: PLIK_ROOT, LIVEPL_PLIK_CA1: PLIK_CA1 },
      180000
    );
  } catch (e) {
    throw new Error(anulowano(e) ? 'anulowano' : e.message);
  }
  return stan();
}

module.exports = { stan, ostatniStan, zainstalujDlaMnie, zainstalujDlaKomputera };
