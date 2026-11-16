// Pakiet LivePL dla instalatora: wszystkie pliki programu (silnik i kod) w jednym strumieniu
// skompresowanym LZMA. Rozpakowuje go narzedzia/instalator/Pakiet.cs (instalator) bez dodatkowych bibliotek.
//
// Format przed kompresją (liczby little-endian):
//   "LPK2"
//   dla każdego pliku: uint16 długość ścieżki w bajtach, ścieżka UTF-8 z „/”, uint8 flagi, int64 rozmiar, dane
//   koniec: uint16 0
// Flaga 1 = filtr E8 (pliki .exe i .dll): po każdym bajcie E8 (wywołanie funkcji w kodzie x86/x64) cztery
// następne bajty, adres względny, zamieniamy na bezwzględny (+ pozycja E8 + 5), a potem je pomijamy.
// Te same wywołania mają wtedy te same bajty i LZMA ściska je lepiej (ok. 7% na LivePL.exe).
// Po kompresji: „LZMA alone” (.lzma) z xz z Git for Windows: słownik 64 MB, rozmiar nieznany + znacznik końca.
// (7-Zip umie .lzma tylko odczytać; jego własne archiwum 7z dałoby z filtrem BCJ2 mniej, ale ten format
// byłby dla instalatora za złożony.)
import { readdirSync, statSync, openSync, writeSync, closeSync, readFileSync, existsSync, rmSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { join, relative, sep, delimiter } from 'node:path';

const FILTR_E8 = 1;

function zbierz(folder, baza = folder, wynik = []) {
  for (const nazwa of readdirSync(folder).sort()) {
    const pelna = join(folder, nazwa);
    if (statSync(pelna).isDirectory()) zbierz(pelna, baza, wynik);
    else wynik.push(relative(baza, pelna).split(sep).join('/'));
  }
  return wynik;
}

// filtr E8 (kodowanie); odwrotność jest w Pakiet.cs i w odwrocE8 poniżej (do sprawdzenia przy budowaniu)
export function filtrE8(dane, kodowanie = true) {
  const d = Buffer.from(dane);
  for (let i = 0; i + 5 <= d.length;) {
    if (d[i] === 0xE8) {
      const v = d.readUInt32LE(i + 1);
      d.writeUInt32LE(kodowanie ? (v + i + 5) >>> 0 : (v - (i + 5)) >>> 0, i + 1);
      i += 5;
    } else i++;
  }
  return d;
}

export function znajdzXz() {
  const git = [process.env.ProgramFiles || 'C:\\Program Files', process.env.LOCALAPPDATA ? join(process.env.LOCALAPPDATA, 'Programs') : null]
    .filter(Boolean)
    .flatMap((b) => [join(b, 'Git', 'mingw64', 'bin', 'xz.exe'), join(b, 'Git', 'usr', 'bin', 'xz.exe')]);
  const zPath = (process.env.PATH || '').split(delimiter).map((d) => join(d, 'xz.exe'));
  const z = [process.env.LIVEPL_XZ, ...git, ...zPath].filter(Boolean).find((m) => existsSync(m));
  if (!z) throw new Error('Do zbudowania pakietu potrzebny jest xz.exe (jest w Git for Windows). Podaj ścieżkę w LIVEPL_XZ.');
  return z;
}

// folder -> kontener (bez kompresji) -> pakiet LZMA; zwraca liczbę plików i rozmiary
export function zrobPakiet(folder, cel) {
  const kontener = cel + '.kontener';
  const pliki = zbierz(folder);
  const f = openSync(kontener, 'w');
  let razem = 0;
  let rozmiarKontenera = 0;
  const zapisz = (b) => { writeSync(f, b); rozmiarKontenera += b.length; };
  try {
    zapisz(Buffer.from('LPK2', 'ascii'));
    for (const p of pliki) {
      const sciezka = Buffer.from(p, 'utf8');
      if (sciezka.length === 0 || sciezka.length > 65535 || p.split('/').includes('..')) throw new Error('Niedozwolona ścieżka: ' + p);
      let dane = readFileSync(join(folder, ...p.split('/')));
      const flagi = /\.(exe|dll)$/i.test(p) ? FILTR_E8 : 0;
      if (flagi & FILTR_E8) {
        const po = filtrE8(dane, true);
        if (!filtrE8(po, false).equals(dane)) throw new Error('Filtr E8 nie jest odwracalny dla ' + p);
        dane = po;
      }
      const naglowek = Buffer.alloc(2 + sciezka.length + 1 + 8);
      naglowek.writeUInt16LE(sciezka.length, 0);
      sciezka.copy(naglowek, 2);
      naglowek.writeUInt8(flagi, 2 + sciezka.length);
      naglowek.writeBigInt64LE(BigInt(dane.length), 3 + sciezka.length);
      zapisz(naglowek);
      zapisz(dane);
      razem += dane.length;
    }
    zapisz(Buffer.from([0, 0]));
  } finally {
    closeSync(f);
  }
  rmSync(cel, { force: true });
  const wyj = openSync(cel, 'w');
  try {
    execFileSync(znajdzXz(), ['--format=lzma', '--lzma1=preset=9e,dict=64MiB', '-T1', '-k', '-c', kontener], { stdio: ['ignore', wyj, 'inherit'] });
  } finally {
    closeSync(wyj);
    rmSync(kontener, { force: true });
  }
  return { pliki: pliki.length, rozpakowany: razem, kontener: rozmiarKontenera, rozmiar: statSync(cel).size };
}
