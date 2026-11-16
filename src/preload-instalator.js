'use strict';
// Okno instalatora LivePL: tylko te wywołania widzi strona instalatora.
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('instalator', {
  stan: () => ipcRenderer.invoke('instalator:stan'),
  instaluj: (opcje) => ipcRenderer.invoke('instalator:instaluj', { pulpit: !!(opcje && opcje.pulpit), ca: !!(opcje && opcje.ca) }),
  odinstaluj: (opcje) => ipcRenderer.invoke('instalator:odinstaluj', { dane: !!(opcje && opcje.dane) }),
  naPostep: (fn) => ipcRenderer.on('postep', (e, d) => fn(d)),
  uruchomLivePL: () => ipcRenderer.send('instalator:uruchom-livepl'),
  zamknij: () => ipcRenderer.send('instalator:zamknij'),
  minimalizuj: () => ipcRenderer.send('instalator:minimalizuj')
});
