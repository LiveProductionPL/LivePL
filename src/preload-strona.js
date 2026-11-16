'use strict';
// Wczytywany w każdej stronie. Powiadomienie strony trafia na pasek wiadomości
// LivePL, a kliknięcie w nie pokazuje okno i przełącza na kartę, z której przyszło.
const { contextBridge, ipcRenderer, webFrame } = require('electron');

contextBridge.exposeInMainWorld('__livepl', {
  pokaz: () => ipcRenderer.send('strona:pokaz'),
  powiadomienie: (tytul, tresc) => ipcRenderer.send('strona:powiadomienie', String(tytul || '').slice(0, 200), String(tresc || '').slice(0, 300))
});

// strona nowej karty LivePL (plik ui/nowa.html): dodawanie strony na pasek
if (location.protocol === 'file:' && /\/ui\/nowa\.html$/i.test(location.pathname)) {
  contextBridge.exposeInMainWorld('liveplNowa', {
    dodaj: (adres, nazwa) => ipcRenderer.invoke('nowa:dodaj', String(adres || ''), String(nazwa || ''))
  });
}

// logowanie do panelu urzędu: tylko na cert.livepl.eu (i adres testowy), resztę sprawdza LivePL
if ((location.protocol === 'https:' && location.hostname === 'cert.livepl.eu') || location.hostname === '127.0.0.1') {
  contextBridge.exposeInMainWorld('liveplCA', {
    stan: () => ipcRenderer.invoke('ca-admin:stan'),
    podpisz: (wyzwanie, odcisk) => ipcRenderer.invoke('ca-admin:podpisz', String(wyzwanie), String(odcisk || ''))
  });
}

// ---------------------------------------------------------------- hasła
// Działa w odizolowanym świecie LivePL: strona nie widzi tych funkcji ani kanałów do LivePL.
// Wykrywa logowanie (pole hasła + login), proponuje zapis i wypełnia formularz dopiero wtedy,
// gdy użytkownik wybierze konto w okienku LivePL.
(function hasla() {
  if (!/^https:$/.test(location.protocol) && !['localhost', '127.0.0.1'].includes(location.hostname)) return;

  const widoczne = (el) => {
    if (!el || el.disabled || el.readOnly) return false;
    const r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0 && getComputedStyle(el).visibility !== 'hidden';
  };
  const polaHasla = () => [...document.querySelectorAll('input[type="password"]')].filter(widoczne);
  const TYPY_LOGINU = 'input[type="email"], input[type="text"], input[type="tel"], input:not([type])';

  function poleLoginu(haslo) {
    const zakres = haslo.form || document;
    const kandydaci = [...zakres.querySelectorAll(TYPY_LOGINU)].filter(widoczne);
    const opisany = kandydaci.find(el => /username|email/i.test(el.autocomplete || '') || /login|user|mail|e-mail|nazwa/i.test((el.name || '') + ' ' + (el.id || '')));
    if (opisany) return opisany;
    // ostatnie pole tekstowe przed polem hasła
    let wynik = null;
    for (const el of kandydaci) {
      if (el.compareDocumentPosition(haslo) & Node.DOCUMENT_POSITION_FOLLOWING) wynik = el;
    }
    return wynik;
  }

  let ostatnio = '';
  function zglos() {
    const haslo = polaHasla().find(p => p.value);
    if (!haslo) return;
    const login = poleLoginu(haslo);
    const dane = { login: login ? login.value.trim() : '', haslo: haslo.value };
    const klucz = dane.login + '\u0000' + dane.haslo;
    if (klucz === ostatnio) return;
    ostatnio = klucz;
    setTimeout(() => { if (ostatnio === klucz) ostatnio = ''; }, 4000);
    ipcRenderer.send('hasla:proba', dane);
  }

  document.addEventListener('submit', zglos, true);
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && e.target instanceof HTMLInputElement && (e.target.type === 'password' || polaHasla().length)) zglos();
  }, true);
  document.addEventListener('click', (e) => {
    const b = e.target instanceof Element ? e.target.closest('button, input[type="submit"], [role="button"]') : null;
    if (b && polaHasla().some(p => p.value)) zglos();
  }, true);

  // wybór konta: po kliknięciu w pole loginu albo hasła, jeśli LivePL ma zapisane konta dla tej strony
  // (strona dostaje od LivePL tylko liczbę kont, bez loginów i haseł)
  let konta = null;
  const pokazano = new WeakSet();
  async function poFokusie(e) {
    const el = e.target;
    if (!(el instanceof HTMLInputElement) || pokazano.has(el)) return;
    const czyHaslo = el.type === 'password';
    const haslo = czyHaslo ? el : polaHasla()[0];
    if (!haslo || (!czyHaslo && poleLoginu(haslo) !== el)) return;
    if (konta === null) {
      try { konta = Number(await ipcRenderer.invoke('hasla:konta')) || 0; } catch { konta = 0; }
    }
    if (!konta || document.activeElement !== el) return;
    pokazano.add(el);
    const r = el.getBoundingClientRect();
    ipcRenderer.send('hasla:pole', { x: r.left, y: r.bottom, szer: r.width });
  }
  document.addEventListener('focusin', poFokusie, true);

  const ustaw = (pole, wartosc) => {
    if (!pole) return;
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(pole, wartosc);
    pole.dispatchEvent(new Event('input', { bubbles: true }));
    pole.dispatchEvent(new Event('change', { bubbles: true }));
  };
  ipcRenderer.on('hasla:wypelnij', (e, dane) => {
    const haslo = polaHasla()[0];
    if (!haslo || !dane) return;
    konta = null; // po zapisie nowego konta lista mogła się zmienić
    ustaw(poleLoginu(haslo), dane.login || '');
    ustaw(haslo, dane.haslo || '');
    haslo.focus();
  });
})();

webFrame.executeJavaScript(`(() => {
  const N = window.Notification;
  if (!N || N.__livepl) return;
  class Powiadomienie extends N {
    constructor(tytul, opcje) {
      super(tytul, opcje);
      try { window.__livepl.powiadomienie(tytul, opcje && opcje.body); } catch (e) {}
      this.addEventListener('click', () => { try { window.__livepl.pokaz(); } catch (e) {} });
    }
  }
  Object.defineProperty(Powiadomienie, 'permission', { get: () => N.permission });
  Powiadomienie.requestPermission = (...a) => N.requestPermission(...a);
  Powiadomienie.__livepl = true;
  window.Notification = Powiadomienie;
})();`).catch(() => {});
