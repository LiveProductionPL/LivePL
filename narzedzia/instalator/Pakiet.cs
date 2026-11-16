// Rozpakowanie pakietu LivePL (format opisany w narzedzia/pakiet.mjs): LZMA (Lzma.cs), kontener plików
// i filtr E8 dla programów (.exe, .dll). Ten sam kod uruchamia instalator i sprawdzarka przy budowaniu
// (Sprawdz.cs), więc to, co sprawdzamy przy budowaniu, jest dokładnie tym, co robi instalator.

using System;
using System.IO;
using System.Text;

static class Pakiet
{
    const int FILTR_E8 = 1;

    // kontener: "LPK2", potem [uint16 długość][ścieżka UTF-8][uint8 flagi][int64 rozmiar][dane]..., na końcu uint16 0
    public static int Rozpakuj(Stream skompresowany, string folder, long rozpakowany, Action<float> postep)
    {
        Directory.CreateDirectory(folder);
        var pelny = Path.GetFullPath(folder).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        int pliki = 0;
        using (var lz = new LzmaStream(skompresowany))
        using (var r = new BinaryReader(lz))
        {
            if (Encoding.ASCII.GetString(r.ReadBytes(4)) != "LPK2") throw new InvalidDataException("To nie jest pakiet LivePL");
            var bufor = new byte[1 << 20];
            for (;;)
            {
                int dl = r.ReadUInt16();
                if (dl == 0) break;
                var nazwa = Encoding.UTF8.GetString(r.ReadBytes(dl));
                int flagi = r.ReadByte();
                long rozmiar = r.ReadInt64();
                var cel = Path.GetFullPath(Path.Combine(folder, nazwa.Replace('/', Path.DirectorySeparatorChar)));
                if (!cel.StartsWith(pelny, StringComparison.OrdinalIgnoreCase) || rozmiar < 0 || (flagi & ~FILTR_E8) != 0)
                    throw new InvalidDataException("Niedozwolony wpis w pakiecie");
                Directory.CreateDirectory(Path.GetDirectoryName(cel));
                using (var plik = new FileStream(cel, FileMode.Create, FileAccess.Write, FileShare.None, 1 << 16))
                {
                    var filtr = (flagi & FILTR_E8) != 0 ? new OdwrotnyE8(plik, rozmiar) : null;
                    long zostalo = rozmiar;
                    while (zostalo > 0)
                    {
                        int n = r.Read(bufor, 0, (int)Math.Min(bufor.Length, zostalo));
                        if (n <= 0) throw new InvalidDataException("Pakiet jest ucięty");
                        if (filtr != null) filtr.Zapisz(bufor, n); else plik.Write(bufor, 0, n);
                        zostalo -= n;
                        if (postep != null) postep((float)lz.Zdekodowano / rozpakowany);
                    }
                    if (filtr != null) filtr.Zakoncz();
                }
                pliki++;
            }
        }
        return pliki;
    }

    // Odwrotność filtra E8 z pakiet.mjs: po bajcie E8 (wywołanie funkcji w kodzie x86/x64) cztery
    // następne bajty to adres bezwzględny; zamieniamy go z powrotem na względny (adres - (pozycja E8 + 5)).
    // Bajty E8 nie są zmieniane, a 4 bajty po nich są pomijane w szukaniu, więc kodowanie i dekodowanie
    // trafiają w te same miejsca. E8 w ostatnich 4 bajtach pliku zostaje bez zmian.
    // Działa na całych kawałkach (bez kopiowania bajt po bajcie): poprawia argumenty w miejscu i zapisuje
    // kawałek; tylko E8, którego argument nie zmieścił się w kawałku, czeka (do 4 bajtów) na następny.
    sealed class OdwrotnyE8
    {
        readonly Stream wyj;
        readonly long rozmiar;
        byte[] praca = new byte[0];
        int reszta;          // ile bajtów z poprzedniego kawałka czeka na początku „praca” (E8 + część argumentu)
        long poz;            // pozycja w pliku bajtu praca[0]

        public OdwrotnyE8(Stream wyjscie, long rozmiarPliku) { wyj = wyjscie; rozmiar = rozmiarPliku; }

        public void Zapisz(byte[] dane, int ile)
        {
            int dl = reszta + ile;
            if (praca.Length < dl)
            {
                var nowa = new byte[dl];
                Buffer.BlockCopy(praca, 0, nowa, 0, reszta);
                praca = nowa;
            }
            Buffer.BlockCopy(dane, 0, praca, reszta, ile);
            int i = 0;
            for (;;)
            {
                i = Array.IndexOf(praca, (byte)0xE8, i, dl - i);
                if (i < 0) { i = dl; break; }
                if (poz + i + 5 > rozmiar) { i = dl; break; }   // E8 w ostatnich 4 bajtach pliku: bez zmian
                if (i + 5 > dl) break;                           // argument kończy się w następnym kawałku
                uint v = (uint)(praca[i + 1] | praca[i + 2] << 8 | praca[i + 3] << 16 | praca[i + 4] << 24);
                v = unchecked(v - (uint)(poz + i + 5));
                praca[i + 1] = (byte)v; praca[i + 2] = (byte)(v >> 8); praca[i + 3] = (byte)(v >> 16); praca[i + 4] = (byte)(v >> 24);
                i += 5;
            }
            // [0, i) jest gotowe; [i, dl) to E8 z niepełnym argumentem
            wyj.Write(praca, 0, i);
            reszta = dl - i;
            if (reszta > 0) Buffer.BlockCopy(praca, i, praca, 0, reszta);
            poz += i;
        }

        public void Zakoncz()
        {
            if (reszta > 0) throw new InvalidDataException("Pakiet jest uszkodzony (filtr E8)");
        }
    }
}
