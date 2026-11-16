'use strict';

// Okno instalatora LivePL: powitanie → postęp → gotowe (albo odinstalowanie).

const $ = (s) => document.querySelector(s);
const I = window.instalator;

function ikona(nazwa) {
  return '<svg class="ik" viewBox="0 0 24 24" aria-hidden="true">' + (window.IKONY[nazwa] || '') + '</svg>';
}

Object.assign(window.IKONY, {
  ok: '<path d="m5.5 12.5 4.2 4.2 8.8-9.4"/>',
  krzyzyk: '<path d="m7 7 10 10M17 7 7 17"/>'
});

$('#minimalizuj').innerHTML = ikona('minus');
$('#zamknij').innerHTML = ikona('zamknij');
document.querySelectorAll('[data-ik]').forEach((el) => { el.innerHTML = ikona(el.dataset.ik); });
$('#gotowe-ik').innerHTML = ikona('ok');
$('#blad-ik').innerHTML = ikona('krzyzyk');

let S = null;
let trwa = false;

function pokaz(id) {
  document.querySelectorAll('.ekran').forEach((e) => { e.hidden = e.id !== id; });
  const glowny = document.querySelector('#' + id + ' .przycisk.glowny');
  if (glowny) setTimeout(() => glowny.focus(), 30);
}

// porównanie wersji 1.2.3
function porownaj(a, b) {
  const x = String(a).split('.').map(Number);
  const y = String(b).split('.').map(Number);
  for (let i = 0; i < 3; i++) if ((x[i] || 0) !== (y[i] || 0)) return (x[i] || 0) - (y[i] || 0);
  return 0;
}

async function start() {
  S = await I.stan();
  $('#wersja').textContent = 'wersja ' + S.wersja;
  if (S.tryb === 'odinstaluj') {
    $('#tytul-okna').textContent = 'Odinstaluj LivePL';
    if (S.zainstalowana) $('#odinstaluj-wstep').textContent = `LivePL ${S.zainstalowana} zniknie z tego komputera razem ze skrótami w menu Start i na pulpicie.`;
    $('#odinstaluj-dziala').hidden = !S.dziala;
    pokaz('ekran-odinstaluj');
    return;
  }
  if (S.zainstalowana) {
    const r = porownaj(S.wersja, S.zainstalowana);
    $('#start-tytul').textContent = r > 0 ? `Zaktualizuj LivePL do ${S.wersja}` : r === 0 ? 'Zainstaluj LivePL ponownie' : `Zainstaluj LivePL ${S.wersja}`;
    $('#start-wstep').textContent = r > 0
      ? `Na tym komputerze jest LivePL ${S.zainstalowana}. Twoje hasła, ustawienia i dodane strony zostaną.`
      : r === 0 ? `Ta wersja (${S.wersja}) już jest zainstalowana. Instalacja odświeży pliki programu; Twoje dane zostaną.`
        : `Na tym komputerze jest nowsza wersja (${S.zainstalowana}). Zainstalujesz starszą ${S.wersja}; Twoje dane zostaną.`;
    $('#start-instaluj').textContent = r > 0 ? 'Zaktualizuj' : 'Zainstaluj';
  }
  // urząd LivePL: opcja tylko wtedy, gdy Windows jeszcze mu nie ufa
  $('#opcja-ca-etykieta').hidden = !S.ca || S.ca.zaufany;
  $('#start-dziala').hidden = !S.dziala;
  // ścieżka w podpowiedzi: pełna zajmowała kilka linijek
  $('#start-gdzie').textContent = 'Tylko dla Ciebie, bez uprawnień administratora' + (S.test ? ' (test)' : '');
  $('#start-gdzie').title = S.cel;
  pokaz('ekran-start');
}

I.naPostep((p) => {
  const proc = Math.round(p.ulamek * 100);
  $('#postep-pasek').style.width = proc + '%';
  $('#postep-procent').textContent = proc + '%';
  if (p.opis) $('#postep-opis').textContent = p.opis;
});

async function instaluj() {
  if (trwa) return;
  trwa = true;
  const pulpit = $('#opcja-pulpit').checked;
  const ca = !$('#opcja-ca-etykieta').hidden && $('#opcja-ca').checked;
  $('#postep-tytul').textContent = $('#start-instaluj').textContent === 'Zaktualizuj' ? 'Aktualizuję LivePL…' : 'Instaluję LivePL…';
  pokaz('ekran-postep');
  const w = await I.instaluj({ pulpit, ca });
  trwa = false;
  if (!w.ok) {
    $('#blad-opis').textContent = w.blad;
    $('#blad-ponow').onclick = instaluj;
    pokaz('ekran-blad');
    return;
  }
  $('#gotowe-tytul').textContent = 'LivePL jest gotowy';
  const pozniej = ' Zrobisz to później w LivePL: ⋮ → Ustawienia → Urząd LivePL w Windows.';
  const oCa = {
    ok: ' Windows ufa teraz urzędowi LivePL.',
    anulowano: ' Urzędu LivePL nie dodano do Windows.' + pozniej,
    blad: ' Nie udało się dodać urzędu LivePL do Windows.' + pozniej,
    test: ' (Test: urzędu LivePL nie dodawano do Windows.)'
  }[w.ca] || '';
  $('#gotowe-opis').textContent = 'Znajdziesz go w menu Start' + (pulpit ? ' i na pulpicie.' : '.') + oCa + ' Miłej pracy!';
  $('#gotowe-uruchom').hidden = false;
  pokaz('ekran-gotowe');
}

async function odinstaluj() {
  if (trwa) return;
  trwa = true;
  const dane = $('#opcja-dane').checked;
  $('#postep-tytul').textContent = 'Odinstalowuję LivePL…';
  pokaz('ekran-postep');
  const w = await I.odinstaluj({ dane });
  trwa = false;
  if (!w.ok) {
    $('#blad-opis').textContent = w.blad;
    $('#blad-ponow').onclick = odinstaluj;
    pokaz('ekran-blad');
    return;
  }
  $('#gotowe-tytul').textContent = 'LivePL został odinstalowany';
  $('#gotowe-opis').textContent = 'Pliki programu znikną po zamknięciu tego okna.' + (dane ? ' Twoje dane LivePL zostały usunięte.' : ' Twoje dane zostały, na wypadek ponownej instalacji.');
  $('#gotowe-uruchom').hidden = true;
  pokaz('ekran-gotowe');
}

$('#start-instaluj').addEventListener('click', instaluj);
$('#odinstaluj-tak').addEventListener('click', odinstaluj);
$('#gotowe-uruchom').addEventListener('click', () => I.uruchomLivePL());
for (const id of ['#zamknij', '#start-anuluj', '#odinstaluj-anuluj', '#gotowe-zamknij', '#blad-zamknij']) {
  $(id).addEventListener('click', () => { if (!trwa) I.zamknij(); });
}
$('#minimalizuj').addEventListener('click', () => I.minimalizuj());
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && !trwa) I.zamknij();
});

start();
