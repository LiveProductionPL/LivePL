// Sprawdzenie pakietu przy budowaniu: rozpakowuje go tym samym kodem co instalator (Pakiet.cs, Lzma.cs)
// do folderu tymczasowego i porównuje każdy plik bajt w bajt z folderem, z którego pakiet powstał.
// Bez tego błąd kompresji, filtra albo dekodera wyszedłby dopiero u użytkownika.
// sprawdz.exe <pakiet> <folder-źródłowy> <folder-tymczasowy> <rozmiar-kontenera>  -> kod 0, gdy zgodne
using System;
using System.Diagnostics;
using System.IO;
using System.Linq;

static class Sprawdz
{
    static int Main(string[] a)
    {
        try
        {
            string pakiet = a[0], zrodlo = Path.GetFullPath(a[1]), tymczasowy = Path.GetFullPath(a[2]);
            long kontener = long.Parse(a[3]);
            if (Directory.Exists(tymczasowy)) Directory.Delete(tymczasowy, true);
            var czas = Stopwatch.StartNew();
            int pliki;
            using (var wej = new FileStream(pakiet, FileMode.Open, FileAccess.Read, FileShare.Read, 1 << 16))
                pliki = Pakiet.Rozpakuj(wej, tymczasowy, kontener, null);
            var rozpakowanie = czas.Elapsed.TotalSeconds;

            Func<string, string[]> lista = (f) => Directory.GetFiles(f, "*", SearchOption.AllDirectories)
                .Select(p => p.Substring(f.TrimEnd('\\').Length + 1)).OrderBy(p => p, StringComparer.OrdinalIgnoreCase).ToArray();
            var a1 = lista(zrodlo);
            var a2 = lista(tymczasowy);
            if (!a1.SequenceEqual(a2, StringComparer.OrdinalIgnoreCase))
            {
                Console.Error.WriteLine("inne pliki: brakuje " + string.Join(", ", a1.Except(a2)) + "; nadmiarowe " + string.Join(", ", a2.Except(a1)));
                return 1;
            }
            foreach (var p in a1)
            {
                var x = File.ReadAllBytes(Path.Combine(zrodlo, p));
                var y = File.ReadAllBytes(Path.Combine(tymczasowy, p));
                if (x.Length != y.Length || !x.SequenceEqual(y)) { Console.Error.WriteLine("różny plik: " + p); return 1; }
            }
            Directory.Delete(tymczasowy, true);
            Console.WriteLine("pakiet zgodny: " + pliki + " plików, rozpakowanie " + rozpakowanie.ToString("0.0") + " s");
            return 0;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine("błąd: " + ex.Message);
            return 1;
        }
    }
}
