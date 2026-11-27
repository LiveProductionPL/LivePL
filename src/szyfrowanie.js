'use strict';

// Czy LivePL może bezpiecznie szyfrować dane (hasła, klucz DeepL) kluczem systemu (safeStorage).
// Windows: DPAPI, macOS: pęk kluczy. Linux: pęk kluczy pulpitu (GNOME Keyring / libsecret albo KWallet);
// bez niego Electron „szyfruje” stałym hasłem (basic_text), czyli w praktyce wcale, więc wtedy nie zapisujemy.

const { safeStorage } = require('electron');

function dostepne() {
  if (!safeStorage.isEncryptionAvailable()) return false;
  if (process.platform !== 'linux') return true;
  try {
    const b = safeStorage.getSelectedStorageBackend();
    return b !== 'basic_text' && b !== 'unknown';
  } catch {
    return false;
  }
}

function blad() {
  if (process.platform === 'linux') return new Error('Linux nie ma teraz pęku kluczy (GNOME Keyring albo KWallet), więc LivePL nie zaszyfruje danych');
  if (process.platform === 'darwin') return new Error('macOS nie pozwala teraz szyfrować danych');
  return new Error('Windows nie pozwala teraz szyfrować danych');
}

module.exports = { dostepne, blad };
