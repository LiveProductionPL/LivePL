'use strict';
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('wybor', {
  lista: () => ipcRenderer.invoke('wybor:lista'),
  wybierz: (id, dzwiek) => ipcRenderer.send('wybor:wynik', { id, dzwiek: !!dzwiek }),
  anuluj: () => ipcRenderer.send('wybor:wynik', null)
});
