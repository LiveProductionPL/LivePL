'use strict';

// Bezpieczny DNS (DNS przez HTTPS, DoH): ustawienia.dns = 'wylaczony' | 'cloudflare' | 'google'.
// Z dostawcą LivePL pyta o adresy stron jego serwer DoH (szyfrowane), a gdy ten nie odpowiada,
// wraca do zwykłego DNS z Windows (tryb „automatic”), więc strony nie przestają działać.
// Pomaga, gdy DNS operatora (np. hotspot w telefonie) pamięta stary albo błędny wpis.
// „Wyłączony” to zachowanie silnika bez zmian: DNS z Windows (silnik sam szyfruje tylko wtedy,
// gdy w Windows ustawiony jest dostawca, który to umie).

const { app } = require('electron');

const DOSTAWCY = {
  cloudflare: { nazwa: 'Cloudflare', serwery: ['https://chrome.cloudflare-dns.com/dns-query'] },
  google: { nazwa: 'Google', serwery: ['https://dns.google/dns-query{?dns}'] }
};
// domyślny wybór, gdy w ustawieniach jeszcze nic nie ma
const DOMYSLNY = 'wylaczony';

let zastosowany = null;

function tryb(ustawienia) {
  const t = ustawienia.dns;
  return t === 'wylaczony' || DOSTAWCY[t] ? t : DOMYSLNY;
}

// przy starcie (zastosuj(ustawienia)) i po zmianie w ustawieniach (zastosuj(ustawienia, ses))
function zastosuj(ustawienia, ses) {
  const t = tryb(ustawienia);
  if (t === zastosowany) return t;
  // przy starcie z wyłączonym niczego nie zmieniamy; po wyłączeniu wracamy do ustawień silnika
  if (zastosowany !== null || t !== 'wylaczony') {
    const d = DOSTAWCY[t];
    app.configureHostResolver(d ? { secureDnsMode: 'automatic', secureDnsServers: d.serwery } : {});
  }
  zastosowany = t;
  // stare odpowiedzi DNS (np. „nie ma takiej strony” od operatora) nie mogą zostać w pamięci
  if (ses) {
    try { ses.clearHostResolverCache().catch(() => {}); } catch { /* bez znaczenia */ }
    try { ses.closeAllConnections().catch(() => {}); } catch { /* bez znaczenia */ }
  }
  return t;
}

module.exports = {
  zastosuj,
  tryb,
  dostawcy: () => Object.entries(DOSTAWCY).map(([id, d]) => ({ id, nazwa: d.nazwa })),
  nazwa: (t) => (DOSTAWCY[t] ? DOSTAWCY[t].nazwa : 'wyłączony')
};
