// LivePL-Instalator.exe: mały program, który rozpakowuje LivePL do folderu tymczasowego
// (pokazując okienko w barwach LivePL), uruchamia LivePL w trybie instalatora, czeka, aż instalator
// się zamknie, i sprząta folder tymczasowy.
// Dwie odmiany z tego samego kodu (narzedzia/zrob-instalator.mjs):
//  - internetowa (ok. 1 MB): pakiet LivePL pobiera z app.livepl.eu w trakcie instalacji,
//  - pełna: pakiet ma w sobie (zasób „livepl.pakiet”), działa bez internetu.
// W obu suma SHA-256 pakietu jest wpisana w program (PakietInfo.cs), więc inny plik się nie zainstaluje.
// Pakiet to LZMA (Lzma.cs) z kontenerem plików i filtrem E8 (Pakiet.cs, opis formatu w narzedzia/pakiet.mjs).
// Kompiluje go kompilator C# z Windows (bez dodatkowych narzędzi).

using System;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Linq;
using System.Net;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Windows.Forms;

[assembly: AssemblyTitle("Instalator LivePL")]
[assembly: AssemblyDescription("Instalator przeglądarki LivePL")]
[assembly: AssemblyCompany("Live Production")]
[assembly: AssemblyProduct("LivePL")]
[assembly: AssemblyCopyright("Copyright © 2026 Live Production Karol Kowalski (licencja MIT)")]

static class Program
{
    [STAThread]
    static int Main(string[] args)
    {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        var okno = new Okno(args);
        Application.Run(okno);
        // zamknięte w trakcie (np. Alt+F4 przy pobieraniu): sprzątamy to, co zdążyło powstać
        okno.Sprzataj();
        return okno.Kod;
    }
}

class Okno : Form
{
    readonly string[] argumenty;
    readonly string folder = Path.Combine(Path.GetTempPath(), "LivePL-instalator-" + Guid.NewGuid().ToString("N").Substring(0, 8));
    // pobrany pakiet leży obok folderu (nie w nim), bo instalator kopiuje cały folder do Programs\LivePL
    string Pobrany { get { return folder + ".pakiet"; } }
    readonly Image logo;
    float postep;
    string napis = "Przygotowuję instalator LivePL…";
    public int Kod;

    static readonly Color Tlo = Color.FromArgb(13, 15, 18);
    static readonly Color Czerwony = Color.FromArgb(196, 43, 35);
    static readonly Color Slaby = Color.FromArgb(139, 149, 161);

    // skala ekranu (100% = 1, 150% = 1,5): okno jest świadome DPI, więc wszystko liczymy sami
    float skala = 1f;
    float S(float v) { return v * skala; }

    [System.Runtime.InteropServices.DllImport("dwmapi.dll")]
    static extern int DwmSetWindowAttribute(IntPtr okno, int atrybut, ref int wartosc, int rozmiar);
    [System.Runtime.InteropServices.DllImport("user32.dll")]
    static extern bool AllowSetForegroundWindow(int proces);
    [System.Runtime.InteropServices.DllImport("user32.dll")]
    static extern uint GetDpiForWindow(IntPtr okno);

    public Okno(string[] args)
    {
        argumenty = args;
        Text = "Instalator LivePL";
        FormBorderStyle = FormBorderStyle.None;
        // położenie liczymy sami w Load: dopiero wtedy znamy skalę ekranu i docelowy rozmiar
        // (wcześniej Windows środkował okienko dla małego rozmiaru, a potem rosło w prawo i w dół)
        StartPosition = FormStartPosition.Manual;
        AutoScaleMode = AutoScaleMode.None;
        Load += (o, e) => Wysrodkuj();
        BackColor = Tlo;
        DoubleBuffered = true;
        ShowInTaskbar = true;
        // na wierzchu, dopóki rozpakowujemy: inaczej Windows potrafi schować okienko pod innymi oknami
        TopMost = true;
        try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }
        var s = Assembly.GetExecutingAssembly().GetManifestResourceStream("logo.png");
        if (s != null) logo = Image.FromStream(s);
        // zaokrąglone rogi w Windows 11 (w starszych Windows nic się nie dzieje)
        HandleCreated += (o, e) => { try { int r = 2; DwmSetWindowAttribute(Handle, 33, ref r, 4); } catch { } };
        Shown += (o, e) => { Activate(); new Thread(Rozpakuj) { IsBackground = true }.Start(); };
    }

    // rozmiar według skali ekranu, a potem środek ekranu, na którym jest kursor
    void Wysrodkuj()
    {
        var ekran = Screen.FromPoint(Cursor.Position).WorkingArea;
        // najpierw na tamten ekran, żeby odczytać jego skalę (monitory mogą mieć różne)
        Location = new Point(ekran.Left + ekran.Width / 2, ekran.Top + ekran.Height / 2);
        try { skala = Math.Max(1f, GetDpiForWindow(Handle) / 96f); }
        catch { using (var g = CreateGraphics()) skala = Math.Max(1f, g.DpiX / 96f); } // starsze Windows
        ClientSize = new Size((int)S(560), (int)S(260));
        Location = new Point(ekran.Left + (ekran.Width - Width) / 2, ekran.Top + (ekran.Height - Height) / 2);
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics;
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.InterpolationMode = InterpolationMode.HighQualityBicubic;
        g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.AntiAliasGridFit;
        float W = ClientSize.Width, H = ClientSize.Height;

        // czerwona wstęga u dołu, jak w logo i w oknie instalatora
        var wstega = new[] { new PointF(0, S(222)), new PointF(W, S(184)), new PointF(W, S(232)), new PointF(0, S(270)) };
        using (var pedzel = new LinearGradientBrush(new PointF(0, 0), new PointF(W, 0), Color.FromArgb(143, 29, 23), Color.FromArgb(227, 70, 58)))
            g.FillPolygon(pedzel, wstega);
        var pasekJasny = new[] { new PointF(0, S(222)), new PointF(W, S(184)), new PointF(W, S(192)), new PointF(0, S(230)) };
        using (var jasny = new SolidBrush(Color.FromArgb(40, 255, 255, 255))) g.FillPolygon(jasny, pasekJasny);

        if (logo != null) g.DrawImage(logo, S(30), S(26), S(84), S(84));
        using (var duzy = new Font("Segoe UI", S(40), FontStyle.Bold | FontStyle.Italic, GraphicsUnit.Pixel))
        using (var sredni = new Font("Segoe UI", S(15), FontStyle.Regular, GraphicsUnit.Pixel))
        using (var tekst = new Font("Segoe UI Semibold", S(15), FontStyle.Regular, GraphicsUnit.Pixel))
        using (var bialy = new SolidBrush(Color.FromArgb(230, 233, 238)))
        using (var czerwony = new SolidBrush(Color.FromArgb(227, 70, 58)))
        using (var slaby = new SolidBrush(Slaby))
        {
            g.DrawString("Live", duzy, bialy, S(126), S(30));
            var szer = g.MeasureString("Live", duzy).Width;
            g.DrawString("PL", duzy, czerwony, S(126) + szer - S(14), S(30));
            g.DrawString("Przeglądarka Live Production", sredni, slaby, S(130), S(80));

            g.DrawString(napis, tekst, bialy, S(32), S(130));
            var procent = (int)Math.Round(postep * 100) + "%";
            var sz = g.MeasureString(procent, tekst).Width;
            g.DrawString(procent, tekst, slaby, W - S(32) - sz, S(130));
        }

        // pasek postępu z zaokrąglonymi końcami
        float x = S(34), y = S(160), w = W - S(68), h = S(8);
        using (var tlo = new SolidBrush(Color.FromArgb(34, 39, 47))) Zaokraglony(g, tlo, x, y, w, h);
        using (var pasek = new LinearGradientBrush(new PointF(x, 0), new PointF(x + w, 0), Color.FromArgb(143, 29, 23), Color.FromArgb(227, 70, 58)))
            Zaokraglony(g, pasek, x, y, Math.Max(h, w * postep), h);
        using (var ramka = new Pen(Color.FromArgb(42, 48, 56))) g.DrawRectangle(ramka, 0, 0, W - 1, H - 1);
    }

    static void Zaokraglony(Graphics g, Brush pedzel, float x, float y, float w, float h)
    {
        using (var sciezka = new GraphicsPath())
        {
            sciezka.AddArc(x, y, h, h, 90, 180);
            sciezka.AddArc(x + w - h, y, h, h, 270, 180);
            sciezka.CloseFigure();
            g.FillPath(pedzel, sciezka);
        }
    }

    void Ustaw(float p, string tekst)
    {
        try { BeginInvoke((Action)(() => { postep = p; if (tekst != null) napis = tekst; Invalidate(); })); } catch { }
    }

    static string Hex(byte[] b)
    {
        var s = new StringBuilder(b.Length * 2);
        foreach (var x in b) s.Append(x.ToString("x2"));
        return s.ToString();
    }

    static string MB(long b) { return (b / 1048576.0).ToString("0", System.Globalization.CultureInfo.InvariantCulture); }

    // pakiet zapisany w instalatorze (odmiana pełna) albo null (odmiana internetowa)
    static Stream Wbudowany()
    {
        return Assembly.GetExecutingAssembly().GetManifestResourceStream("livepl.pakiet");
    }

    static void SprawdzSume(Stream s)
    {
        using (var sha = SHA256.Create())
        {
            var suma = Hex(sha.ComputeHash(s));
            if (s.Length != PakietInfo.Rozmiar || suma != PakietInfo.Sha256)
                throw new InvalidDataException("Pakiet LivePL jest uszkodzony albo podmieniony (suma SHA-256 się nie zgadza).");
        }
        s.Position = 0;
    }

    // odmiana internetowa: pobranie pakietu z app.livepl.eu z postępem i sumą liczoną w locie
    void PobierzRaz()
    {
        // test: LIVEPL_PAKIET_ADRES (tylko https albo http://127.0.0.1); suma i tak musi się zgadzać
        var adres = Environment.GetEnvironmentVariable("LIVEPL_PAKIET_ADRES");
        if (string.IsNullOrEmpty(adres) || !(adres.StartsWith("https://") || adres.StartsWith("http://127.0.0.1"))) adres = PakietInfo.Adres;
        try { ServicePointManager.SecurityProtocol |= (SecurityProtocolType)3072; } catch { } // TLS 1.2
        Ustaw(0f, "Łączę się z serwerem LivePL…");
        var zadanie = (HttpWebRequest)WebRequest.Create(adres);
        zadanie.UserAgent = "LivePL-Instalator/" + PakietInfo.Wersja;
        zadanie.Timeout = 30000;
        zadanie.ReadWriteTimeout = 30000;
        using (var odp = (HttpWebResponse)zadanie.GetResponse())
        using (var wej = odp.GetResponseStream())
        using (var plik = new FileStream(Pobrany, FileMode.Create, FileAccess.Write, FileShare.None, 1 << 16))
        using (var sha = SHA256.Create())
        {
            var bufor = new byte[1 << 16];
            long pobrane = 0;
            int n, krok = 0;
            while ((n = wej.Read(bufor, 0, bufor.Length)) > 0)
            {
                pobrane += n;
                if (pobrane > PakietInfo.Rozmiar) throw new InvalidDataException("Serwer wysłał więcej danych, niż ma pakiet LivePL.");
                plik.Write(bufor, 0, n);
                sha.TransformBlock(bufor, 0, n, null, 0);
                if (++krok % 16 == 0) Ustaw((float)pobrane / PakietInfo.Rozmiar, "Pobieram LivePL… " + MB(pobrane) + " z " + MB(PakietInfo.Rozmiar) + " MB");
            }
            sha.TransformFinalBlock(bufor, 0, 0);
            if (pobrane != PakietInfo.Rozmiar) throw new IOException("Pobieranie przerwało się przed końcem (" + MB(pobrane) + " z " + MB(PakietInfo.Rozmiar) + " MB).");
            if (Hex(sha.Hash) != PakietInfo.Sha256) throw new InvalidDataException("Pobrany pakiet jest uszkodzony albo podmieniony (suma SHA-256 się nie zgadza).");
        }
        Ustaw(1f, "Pobrano LivePL");
    }

    // przy błędzie pobierania: „Ponów” albo „Anuluj” (wtedy wyjątek OperationCanceledException)
    void Pobierz()
    {
        for (;;)
        {
            try { PobierzRaz(); return; }
            catch (Exception ex)
            {
                try { if (File.Exists(Pobrany)) File.Delete(Pobrany); } catch { }
                var odp = DialogResult.Cancel;
                Invoke((Action)(() => odp = MessageBox.Show(this,
                    "Nie udało się pobrać LivePL.\n\n" + ex.Message +
                    "\n\nSprawdź połączenie z internetem i kliknij „Ponów próbę”. Na komputerze bez internetu użyj pełnego instalatora z app.livepl.eu.",
                    "Instalator LivePL", MessageBoxButtons.RetryCancel, MessageBoxIcon.Warning)));
                if (odp != DialogResult.Retry) throw new OperationCanceledException();
            }
        }
    }

    // rozpakowanie tym samym kodem, który sprawdza pakiet przy budowaniu (Pakiet.cs)
    void RozpakujPakiet(Stream skompresowany)
    {
        Pakiet.Rozpakuj(skompresowany, folder, PakietInfo.Rozpakowany, (p) => Ustaw(p, "Rozpakowuję LivePL…"));
    }

    void Rozpakuj()
    {
        try
        {
            var wbudowany = Wbudowany();
            if (wbudowany != null)
            {
                using (wbudowany)
                {
                    SprawdzSume(wbudowany);
                    RozpakujPakiet(wbudowany);
                }
            }
            else
            {
                Pobierz();
                using (var plik = new FileStream(Pobrany, FileMode.Open, FileAccess.Read, FileShare.Read, 1 << 16))
                    RozpakujPakiet(plik);
                try { File.Delete(Pobrany); } catch { }
            }
            Ustaw(1f, "Uruchamiam instalator…");
            var dalej = string.Join(" ", argumenty.Select(a => "\"" + a.Replace("\"", "") + "\""));
            // okno instalatora może wyjść na pierwszy plan (Windows pozwala na to tylko za naszą zgodą)
            try { AllowSetForegroundWindow(-1); } catch { }
            var proces = Process.Start(new ProcessStartInfo(Path.Combine(folder, "LivePL.exe"), "--instalator " + dalej)
            {
                UseShellExecute = false,
                WorkingDirectory = folder
            });
            // okienko znika, gdy pojawi się okno instalatora; czekamy w tle na jego zamknięcie
            Thread.Sleep(1500);
            BeginInvoke((Action)(() => Hide()));
            proces.WaitForExit();
            Kod = proces.ExitCode;
        }
        catch (OperationCanceledException)
        {
            Kod = 1;
        }
        catch (Exception ex)
        {
            Kod = 1;
            try { Invoke((Action)(() => MessageBox.Show(this, "Nie udało się uruchomić instalatora LivePL.\n\n" + ex.Message, "Instalator LivePL", MessageBoxButtons.OK, MessageBoxIcon.Error))); } catch { }
        }
        finally
        {
            Sprzataj();
            try { BeginInvoke((Action)(() => Close())); } catch { }
        }
    }

    public void Sprzataj()
    {
        try { if (File.Exists(Pobrany)) File.Delete(Pobrany); } catch { }
        for (int i = 0; i < 20; i++)
        {
            try { if (Directory.Exists(folder)) Directory.Delete(folder, true); return; }
            catch { Thread.Sleep(500); }
        }
    }
}
