# Silnik tłumacza: Bergamot (Firefox Translations)

Pliki w tym folderze pochodzą od Mozilli i są bez zmian. Licencja: MPL-2.0 (plik `LICENSE`).

| Plik | Skąd | SHA-256 |
|---|---|---|
| `bergamot-translator.js` | repozytorium Firefoksa, `toolkit/components/translations/bergamot-translator/bergamot-translator.js` (v0.6.0+eea6e5a8) | `faff1ef6285b0d26f01787776fd49299dfb756ecb9688aa990c250e66797b47d` |
| `bergamot-translator.wasm.zst` | Remote Settings Mozilli, kolekcja `translations-wasm-v2`, `bergamot-translator` 4.0 (v0.6.0, 1de4a085), plik skompresowany zstd | `327fcfc7b7e9d95f6fa0844ebf899cfc05469872ef02d3aef5783182839a6255` |
| `LICENSE` | jak wyżej | `1f256ecad192880510e84ad60474eab7589218784b9a50bc7ceee34c2b91f1d5` |

Po rozpakowaniu WASM ma 4 960 506 bajtów i sumę `f38ef807636a7c994afedaab7ff8ffe0d590d21897f05139f747b80fd7bbe926`.

`src/tlumacz-silnik.js` sprawdza sumę pliku WASM przed uruchomieniem. Modele (słowniki) nie są w repozytorium: LivePL pobiera je przy pierwszym użyciu danego języka z kolekcji `translations-models-v2` (wersje 3.x) i sprawdza sumę każdego pliku.

Nowy WASM wymaga pasującego `bergamot-translator.js` z tej samej wersji Firefoksa, więc oba pliki podmienia się razem.
