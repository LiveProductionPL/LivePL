'use strict';

// Narzędzia, które LivePL udostępnia AI (Claude i inne) przez MCP.
// Ten plik czyta zarówno most MCP (ai-most.js, zwykły Node), jak i LivePL (dostep-ai.js).
// Opisy są po angielsku, bo czytają je modele AI; użytkownik ich nie widzi.

const KARTA = { type: 'string', description: 'Tab id from list_tabs (e.g. "poczta" or "k3"). Default: the tab the user is looking at.' };
const REF = { type: 'string', description: 'Element reference from read_page or find, e.g. "ref_12".' };

const NARZEDZIA = [
  {
    name: 'list_tabs',
    poziom: 'czytanie',
    description: 'List LivePL programs (channels on the left bar, e.g. mail) and open tabs, with ids, titles and addresses. Call this first.',
    inputSchema: { type: 'object', properties: {} }
  },
  {
    name: 'get_page_text',
    poziom: 'czytanie',
    description: 'Get the readable text of a page (main content first). Best for reading articles, e-mails and lists.',
    inputSchema: { type: 'object', properties: { tab_id: KARTA, max_chars: { type: 'integer', description: 'Default 20000.' } } }
  },
  {
    name: 'read_page',
    poziom: 'czytanie',
    description: 'List page elements with refs (ref_N) for click/type. filter "interactive" (default) returns links, buttons and fields; "all" also headings, images and text.',
    inputSchema: {
      type: 'object',
      properties: { tab_id: KARTA, filter: { type: 'string', enum: ['interactive', 'all'] }, max_chars: { type: 'integer', description: 'Default 20000.' } }
    }
  },
  {
    name: 'find',
    poziom: 'czytanie',
    description: 'Find elements whose role, name or text contains the query (case-insensitive). Returns up to 20 refs.',
    inputSchema: { type: 'object', properties: { tab_id: KARTA, query: { type: 'string' } }, required: ['query'] }
  },
  {
    name: 'screenshot',
    poziom: 'czytanie',
    description: 'Take a screenshot of a tab. Pixel coordinates in the image can be used with click (x, y). The tab must be visible: use switch_tab first if needed.',
    inputSchema: { type: 'object', properties: { tab_id: KARTA } }
  },
  {
    name: 'switch_tab',
    poziom: 'czytanie',
    description: 'Show a tab or program to the user (opens the program if it is not open yet).',
    inputSchema: { type: 'object', properties: { tab_id: KARTA }, required: ['tab_id'] }
  },
  {
    name: 'wait',
    poziom: 'czytanie',
    description: 'Wait up to `seconds` (max 10) for the page to finish loading.',
    inputSchema: { type: 'object', properties: { tab_id: KARTA, seconds: { type: 'number' } } }
  },
  {
    name: 'open_tab',
    poziom: 'pelny',
    description: 'Open an address in a new tab (http/https). Returns the new tab id.',
    inputSchema: { type: 'object', properties: { url: { type: 'string' }, background: { type: 'boolean', description: 'Do not show the tab to the user.' } }, required: ['url'] }
  },
  {
    name: 'navigate',
    poziom: 'pelny',
    description: 'Go to an address in a tab, or use "back", "forward" or "reload".',
    inputSchema: { type: 'object', properties: { tab_id: KARTA, url: { type: 'string' } }, required: ['url'] }
  },
  {
    name: 'close_tab',
    poziom: 'pelny',
    description: 'Close a tab (programs on the left bar cannot be closed).',
    inputSchema: { type: 'object', properties: { tab_id: KARTA }, required: ['tab_id'] }
  },
  {
    name: 'click',
    poziom: 'pelny',
    description: 'Click an element by ref, or a point (x, y) from the last screenshot. The tab is shown to the user while the AI acts.',
    inputSchema: {
      type: 'object',
      properties: { tab_id: KARTA, ref: REF, x: { type: 'number' }, y: { type: 'number' }, double: { type: 'boolean' } }
    }
  },
  {
    name: 'type',
    poziom: 'pelny',
    description: 'Type text into a field (by ref, or the focused field). LivePL never lets the AI type into password fields.',
    inputSchema: {
      type: 'object',
      properties: {
        tab_id: KARTA, ref: REF, text: { type: 'string' },
        clear: { type: 'boolean', description: 'Replace the current content of the field.' },
        submit: { type: 'boolean', description: 'Press Enter after typing.' }
      },
      required: ['text']
    }
  },
  {
    name: 'press_key',
    poziom: 'pelny',
    description: 'Press a key, e.g. "Enter", "Tab", "Escape", "Backspace", "ArrowDown", "PageDown", "a". Optional modifiers: ctrl, shift, alt.',
    inputSchema: {
      type: 'object',
      properties: { tab_id: KARTA, key: { type: 'string' }, modifiers: { type: 'array', items: { type: 'string', enum: ['ctrl', 'shift', 'alt'] } } },
      required: ['key']
    }
  },
  {
    name: 'scroll',
    poziom: 'czytanie',
    description: 'Scroll the page (or the scrollable area under ref / x,y). amount = screens/10, default 5 (half a screen).',
    inputSchema: {
      type: 'object',
      properties: {
        tab_id: KARTA, direction: { type: 'string', enum: ['up', 'down', 'left', 'right'] },
        amount: { type: 'number' }, ref: REF, x: { type: 'number' }, y: { type: 'number' }
      },
      required: ['direction']
    }
  }
];

const INSTRUKCJE = 'LivePL is the user\'s own browser (Live Production). Start with list_tabs. ' +
  'Use get_page_text or read_page to read pages; click/type need refs from read_page or find. ' +
  'The user sees every action (a red "AI" lamp) and can stop you at any time. ' +
  'Some programs (server panel, certificate authority) are blocked for AI, and LivePL never lets you type passwords: ask the user to do that.';

module.exports = { NARZEDZIA, INSTRUKCJE };
