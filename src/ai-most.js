'use strict';

// Most MCP dla AI (Claude Desktop, Claude Code, Codex, Gemini i inne).
// Aplikacja AI uruchamia go tak:  LivePL.exe <ten plik>  ze zmienną ELECTRON_RUN_AS_NODE=1,
// i rozmawia z nim przez stdin/stdout (JSON-RPC, jedna wiadomość w linii).
// Most sam obsługuje protokół MCP, a każde wywołanie narzędzia przekazuje do działającego LivePL
// przez lokalny potok Windows (named pipe). Bez włączonego „Dostępu dla AI” w LivePL nic nie zrobi.
// Nie otwiera żadnego portu sieciowego.

const fs = require('fs');
const net = require('net');
const path = require('path');
const os = require('os');
const { NARZEDZIA, INSTRUKCJE } = require('./ai-narzedzia');

const WERSJE = ['2025-06-18', '2025-03-26', '2024-11-05'];
const CZAS_NA_NARZEDZIE = 90 * 1000;

function folderDanych() {
  if (process.env.LIVEPL_DANE) return path.resolve(process.env.LIVEPL_DANE);
  return path.join(process.env.APPDATA || path.join(os.homedir(), 'AppData', 'Roaming'), 'LivePL');
}

function wersjaLivePL() {
  try { return JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'package.json'), 'utf8')).version; } catch { return '0'; }
}

function wyslij(obj) {
  process.stdout.write(JSON.stringify(obj) + '\n');
}

function blad(tekst) {
  return { content: [{ type: 'text', text: tekst }], isError: true };
}

// ---------------------------------------------------------------- połączenie z LivePL

let gniazdo = null;
let bufor = '';
let nr = 1;
const czekajace = new Map();

function polacz() {
  if (gniazdo) return Promise.resolve(gniazdo);
  let ust;
  try {
    ust = JSON.parse(fs.readFileSync(path.join(folderDanych(), 'dostep-ai.json'), 'utf8'));
  } catch {
    return Promise.reject(new Error('wylaczony'));
  }
  return new Promise((ok, nie) => {
    const s = net.connect(ust.rura);
    const czas = setTimeout(() => { s.destroy(); nie(new Error('brak-odpowiedzi')); }, 5000);
    s.setEncoding('utf8');
    s.once('connect', () => {
      clearTimeout(czas);
      s.write(JSON.stringify({ token: ust.token, klient: process.env.LIVEPL_AI_KLIENT || '' }) + '\n');
      gniazdo = s;
      ok(s);
    });
    s.on('data', (d) => {
      bufor += d;
      let i;
      while ((i = bufor.indexOf('\n')) >= 0) {
        const linia = bufor.slice(0, i);
        bufor = bufor.slice(i + 1);
        let m;
        try { m = JSON.parse(linia); } catch { continue; }
        const c = czekajace.get(m.id);
        if (c) { czekajace.delete(m.id); c(m.wynik || blad(m.blad || 'LivePL nie podał wyniku.')); }
      }
    });
    const koniec = () => {
      clearTimeout(czas);
      if (gniazdo === s) gniazdo = null;
      bufor = '';
      for (const [id, c] of czekajace) { czekajace.delete(id); c(blad('LivePL przerwał połączenie (zamknięty albo dostęp dla AI wyłączony).')); }
    };
    s.on('error', (e) => { koniec(); nie(e); });
    s.on('close', koniec);
  });
}

async function wywolaj(nazwa, argumenty) {
  let s;
  try {
    s = await polacz();
  } catch (e) {
    return blad(e.message === 'wylaczony'
      ? 'LivePL access for AI is turned off. Ask the user to enable it in LivePL: menu ⋮ → Ustawienia → Dostęp dla AI.'
      : 'LivePL is not running (or AI access was just turned off). Ask the user to open LivePL.');
  }
  return new Promise((ok) => {
    const id = nr++;
    const czas = setTimeout(() => { czekajace.delete(id); ok(blad('LivePL did not answer in time.')); }, CZAS_NA_NARZEDZIE);
    czekajace.set(id, (w) => { clearTimeout(czas); ok(w); });
    s.write(JSON.stringify({ id, narzedzie: nazwa, argumenty: argumenty || {} }) + '\n');
  });
}

// ---------------------------------------------------------------- MCP przez stdin/stdout

async function obsluz(m) {
  if (!m || typeof m !== 'object') return;
  const odpowiedz = (wynik) => { if (m.id !== undefined) wyslij({ jsonrpc: '2.0', id: m.id, result: wynik }); };
  const odmow = (kod, tekst) => { if (m.id !== undefined) wyslij({ jsonrpc: '2.0', id: m.id, error: { code: kod, message: tekst } }); };

  switch (m.method) {
    case 'initialize': {
      const chce = m.params && m.params.protocolVersion;
      process.env.LIVEPL_AI_KLIENT = String((m.params && m.params.clientInfo && m.params.clientInfo.name) || '').slice(0, 60);
      return odpowiedz({
        protocolVersion: WERSJE.includes(chce) ? chce : WERSJE[0],
        capabilities: { tools: {} },
        serverInfo: { name: 'livepl', title: 'Przeglądarka LivePL', version: wersjaLivePL() },
        instructions: INSTRUKCJE
      });
    }
    case 'ping':
      return odpowiedz({});
    case 'tools/list':
      return odpowiedz({ tools: NARZEDZIA.map(({ name, description, inputSchema }) => ({ name, description, inputSchema })) });
    case 'tools/call': {
      const nazwa = m.params && m.params.name;
      if (!NARZEDZIA.some(n => n.name === nazwa)) return odmow(-32602, `Unknown tool: ${nazwa}`);
      return odpowiedz(await wywolaj(nazwa, m.params.arguments));
    }
    case 'resources/list': return odpowiedz({ resources: [] });
    case 'prompts/list': return odpowiedz({ prompts: [] });
    default:
      if (typeof m.method === 'string' && m.method.startsWith('notifications/')) return;
      return odmow(-32601, `Method not found: ${m.method}`);
  }
}

let wejscie = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (d) => {
  wejscie += d;
  let i;
  while ((i = wejscie.indexOf('\n')) >= 0) {
    const linia = wejscie.slice(0, i).trim();
    wejscie = wejscie.slice(i + 1);
    if (!linia) continue;
    let m;
    try { m = JSON.parse(linia); } catch { wyslij({ jsonrpc: '2.0', id: null, error: { code: -32700, message: 'Parse error' } }); continue; }
    for (const x of Array.isArray(m) ? m : [m]) obsluz(x).catch((e) => {
      if (x && x.id !== undefined) wyslij({ jsonrpc: '2.0', id: x.id, error: { code: -32603, message: String(e && e.message || e) } });
    });
  }
});
process.stdin.on('end', () => process.exit(0));
