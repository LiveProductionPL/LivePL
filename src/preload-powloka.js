'use strict';
// Pasek boczny i górny: tylko te wywołania widzi strona paska.
const { contextBridge, ipcRenderer } = require('electron');

const KANALY = ['stan', 'fokus-adres', 'szukaj-otworz', 'szukaj-zamknij', 'szukaj-wynik', 'pobrano'];

contextBridge.exposeInMainWorld('livepl', {
  wyslij: (akcja, ...argumenty) => ipcRenderer.send('powloka', akcja, ...argumenty),
  na: (kanal, fn) => {
    if (!KANALY.includes(kanal)) return;
    ipcRenderer.on(kanal, (e, dane) => fn(dane));
  }
});
