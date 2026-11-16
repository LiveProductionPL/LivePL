'use strict';
// Panel LivePL (menu, ustawienia, pobrane pliki): tylko te wywołania widzi strona panelu.
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('panel', {
  na: (fn) => ipcRenderer.on('panel:dane', (e, dane) => fn(dane)),
  akcja: (nazwa, arg) => ipcRenderer.send('panel:akcja', nazwa, arg),
  wysokosc: (h) => ipcRenderer.send('panel:wysokosc', h)
});
