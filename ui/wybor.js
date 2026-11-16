'use strict';

let wybrane = null;
const przycisk = document.getElementById('udostepnij');

function wybierz(id, el) {
  wybrane = id;
  document.querySelectorAll('.zrodlo').forEach((x) => x.classList.toggle('wybrane', x === el));
  przycisk.disabled = false;
}

function zatwierdz() {
  if (wybrane) window.wybor.wybierz(wybrane, document.getElementById('dzwiek').checked);
}

window.wybor.lista().then((dane) => {
  if (dane.host) document.getElementById('kto').textContent = `${dane.host} chce zobaczyć Twój ekran.`;
  document.getElementById('dzwiek-etykieta').hidden = !dane.dzwiek;

  const ekrany = dane.zrodla.filter((z) => z.ekran);
  const okna = dane.zrodla.filter((z) => !z.ekran && z.nazwa !== 'Udostępnij ekran');

  const wstaw = (lista, gdzie, podpis) => {
    const box = document.getElementById(gdzie);
    if (!lista.length) {
      box.innerHTML = '<p class="pusto">Brak</p>';
      return;
    }
    lista.forEach((z, i) => {
      const el = document.createElement('button');
      el.type = 'button';
      el.className = 'zrodlo';
      const img = new Image();
      img.src = z.miniatura;
      img.alt = '';
      const opis = document.createElement('span');
      opis.textContent = podpis ? (lista.length > 1 ? `Ekran ${i + 1}` : 'Cały ekran') : z.nazwa;
      el.title = opis.textContent;
      el.append(img, opis);
      el.addEventListener('click', () => wybierz(z.id, el));
      el.addEventListener('dblclick', () => { wybierz(z.id, el); zatwierdz(); });
      box.appendChild(el);
    });
  };
  wstaw(ekrany, 'ekrany', true);
  wstaw(okna, 'okna', false);
});

przycisk.addEventListener('click', zatwierdz);
document.getElementById('anuluj').addEventListener('click', () => window.wybor.anuluj());
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') window.wybor.anuluj();
  if (e.key === 'Enter') zatwierdz();
});
