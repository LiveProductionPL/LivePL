// Dekoder LZMA („LZMA alone”, .lzma) dla instalatora LivePL, napisany według publicznej specyfikacji
// formatu (LZMA SDK, lzma-specification). Czyta strumień skompresowany i oddaje bajty jak zwykły Stream,
// więc instalator rozpakowuje pakiet w locie, bez pliku pośredniego.
// Obsługuje znany rozmiar w nagłówku i rozmiar nieznany ze znacznikiem końca (tak zapisuje xz --format=lzma).

using System;
using System.IO;
using System.Runtime.CompilerServices;

sealed class LzmaStream : Stream
{
    // ---------------------------------------------------------------- wejście i dekoder zakresu
    readonly Stream wej;
    readonly byte[] bufWej = new byte[1 << 16];
    int bufPoz, bufIle;
    uint range, code;

    [MethodImpl(MethodImplOptions.AggressiveInlining)]
    byte CzytajBajt()
    {
        if (bufPoz == bufIle)
        {
            bufIle = wej.Read(bufWej, 0, bufWej.Length);
            bufPoz = 0;
            if (bufIle <= 0) throw new InvalidDataException("Pakiet jest ucięty");
        }
        return bufWej[bufPoz++];
    }

    [MethodImpl(MethodImplOptions.AggressiveInlining)]
    void Normalizuj()
    {
        if (range < (1u << 24)) { range <<= 8; code = (code << 8) | CzytajBajt(); }
    }

    [MethodImpl(MethodImplOptions.AggressiveInlining)]
    int Bit(ushort[] p, int i)
    {
        uint v = p[i];
        uint bound = (range >> 11) * v;
        int bit;
        if (code < bound) { v += ((1u << 11) - v) >> 5; range = bound; bit = 0; }
        else { v -= v >> 5; code -= bound; range -= bound; bit = 1; }
        p[i] = (ushort)v;
        Normalizuj();
        return bit;
    }

    uint BityBezposrednie(int ile)
    {
        uint wynik = 0;
        do
        {
            range >>= 1;
            code -= range;
            uint t = 0u - (code >> 31);
            code += range & t;
            if (code == range) throw new InvalidDataException("Pakiet jest uszkodzony");
            Normalizuj();
            wynik = (wynik << 1) + (t + 1);
        } while (--ile > 0);
        return wynik;
    }

    int Drzewo(ushort[] p, int baza, int bity)
    {
        int m = 1;
        for (int i = 0; i < bity; i++) m = (m << 1) + Bit(p, baza + m);
        return m - (1 << bity);
    }

    int DrzewoOdwrotne(ushort[] p, int baza, int bity)
    {
        int m = 1, wynik = 0;
        for (int i = 0; i < bity; i++)
        {
            int b = Bit(p, baza + m);
            m = (m << 1) + b;
            wynik |= b << i;
        }
        return wynik;
    }

    // ---------------------------------------------------------------- okno (słownik)
    readonly byte[] okno;
    readonly uint rozmiarOkna;
    uint pozOkna;
    bool pelne;
    long wszystkich;      // ile bajtów zdekodowano
    long oddanych;        // ile bajtów oddano przez Read

    [MethodImpl(MethodImplOptions.AggressiveInlining)]
    void Wstaw(byte b)
    {
        okno[pozOkna++] = b;
        wszystkich++;
        if (pozOkna == rozmiarOkna) { pozOkna = 0; pelne = true; }
    }

    [MethodImpl(MethodImplOptions.AggressiveInlining)]
    byte Wez(uint odl)
    {
        return okno[odl <= pozOkna ? pozOkna - odl : rozmiarOkna - odl + pozOkna];
    }

    // ---------------------------------------------------------------- model LZMA
    readonly int lc, lp, pb;
    readonly long rozmiar;   // -1 = nieznany (koniec po znaczniku)
    readonly ushort[] literaly;
    readonly ushort[] czyDopasowanie = new ushort[12 << 4];
    readonly ushort[] czyPowt = new ushort[12];
    readonly ushort[] czyPowtG0 = new ushort[12];
    readonly ushort[] czyPowtG1 = new ushort[12];
    readonly ushort[] czyPowtG2 = new ushort[12];
    readonly ushort[] czyPowt0Dlugie = new ushort[12 << 4];
    readonly ushort[] slotyOdl = new ushort[4 << 6];
    readonly ushort[] odlegl = new ushort[1 + 114];
    readonly ushort[] wyrown = new ushort[1 << 4];
    readonly Dlugosci dlug = new Dlugosci();
    readonly Dlugosci dlugPowt = new Dlugosci();

    sealed class Dlugosci
    {
        public readonly ushort[] wybor = new ushort[2];
        public readonly ushort[] niskie = new ushort[16 << 3];
        public readonly ushort[] srednie = new ushort[16 << 3];
        public readonly ushort[] wysokie = new ushort[1 << 8];
        public Dlugosci() { Zeruj(wybor); Zeruj(niskie); Zeruj(srednie); Zeruj(wysokie); }
    }

    static void Zeruj(ushort[] p) { for (int i = 0; i < p.Length; i++) p[i] = 1024; }

    int Dlugosc(Dlugosci d, int stanPoz)
    {
        if (Bit(d.wybor, 0) == 0) return Drzewo(d.niskie, stanPoz << 3, 3);
        if (Bit(d.wybor, 1) == 0) return 8 + Drzewo(d.srednie, stanPoz << 3, 3);
        return 16 + Drzewo(d.wysokie, 0, 8);
    }

    uint Odleglosc(int dl)
    {
        int stanDl = dl > 3 ? 3 : dl;
        int slot = Drzewo(slotyOdl, stanDl << 6, 6);
        if (slot < 4) return (uint)slot;
        int bity = (slot >> 1) - 1;
        uint odl = (uint)((2 | (slot & 1)) << bity);
        if (slot < 14) odl += (uint)DrzewoOdwrotne(odlegl, (int)odl - slot, bity);
        else
        {
            odl += BityBezposrednie(bity - 4) << 4;
            odl += (uint)DrzewoOdwrotne(wyrown, 0, 4);
        }
        return odl;
    }

    int stan;
    uint rep0, rep1, rep2, rep3;
    int doSkopiowania;       // reszta dopasowania, które nie zmieściło się w poprzednim kroku
    bool koniec;

    public LzmaStream(Stream wejscie)
    {
        wej = wejscie;
        var nag = new byte[13];
        for (int i = 0; i < 13; i++)
        {
            int b = wej.ReadByte();
            if (b < 0) throw new InvalidDataException("Pakiet jest ucięty");
            nag[i] = (byte)b;
        }
        int d = nag[0];
        if (d >= 9 * 5 * 5) throw new InvalidDataException("To nie jest pakiet LZMA");
        lc = d % 9; d /= 9; lp = d % 5; pb = d / 5;
        uint slownik = BitConverter.ToUInt32(nag, 1);
        if (slownik < 4096) slownik = 4096;
        if (slownik > (1u << 28)) throw new InvalidDataException("Za duży słownik w pakiecie");
        rozmiarOkna = slownik;
        okno = new byte[rozmiarOkna];
        ulong r = BitConverter.ToUInt64(nag, 5);
        rozmiar = r == ulong.MaxValue ? -1 : (long)r;

        literaly = new ushort[0x300 << (lc + lp)];
        foreach (var p in new[] { literaly, czyDopasowanie, czyPowt, czyPowtG0, czyPowtG1, czyPowtG2, czyPowt0Dlugie, slotyOdl, odlegl, wyrown }) Zeruj(p);

        range = 0xFFFFFFFF;
        code = 0;
        byte pierwszy = CzytajBajt();
        for (int i = 0; i < 4; i++) code = (code << 8) | CzytajBajt();
        if (pierwszy != 0 || code == range) throw new InvalidDataException("Pakiet jest uszkodzony");
        if (rozmiar == 0) koniec = true;
    }

    // rozmiar po rozpakowaniu, jeśli zapisany w nagłówku (inaczej -1)
    public long Rozmiar { get { return rozmiar; } }
    public long Zdekodowano { get { return wszystkich; } }

    // jeden krok: literał albo dopasowanie (w całości albo do granicy „ile wolno”)
    void Krok(long limit)
    {
        if (doSkopiowania > 0)
        {
            while (doSkopiowania > 0 && wszystkich < limit) { Wstaw(Wez(rep0 + 1)); doSkopiowania--; }
            return;
        }
        if (rozmiar >= 0 && wszystkich >= rozmiar) { koniec = true; return; }

        int stanPoz = (int)(wszystkich & ((1 << pb) - 1));
        if (Bit(czyDopasowanie, (stan << 4) + stanPoz) == 0)
        {
            byte poprzedni = (pelne || pozOkna > 0) ? Wez(1) : (byte)0;
            int baza = 0x300 * (int)((((ulong)wszystkich & ((1ul << lp) - 1)) << lc) + ((ulong)poprzedni >> (8 - lc)));
            int s = 1;
            if (stan >= 7)
            {
                int dop = Wez(rep0 + 1);
                do
                {
                    int bitDop = (dop >> 7) & 1;
                    dop <<= 1;
                    int b = Bit(literaly, baza + ((1 + bitDop) << 8) + s);
                    s = (s << 1) | b;
                    if (bitDop != b) break;
                } while (s < 0x100);
            }
            while (s < 0x100) s = (s << 1) | Bit(literaly, baza + s);
            Wstaw((byte)(s - 0x100));
            stan = stan < 4 ? 0 : stan < 10 ? stan - 3 : stan - 6;
            return;
        }

        int dl;
        if (Bit(czyPowt, stan) != 0)
        {
            if (!pelne && pozOkna == 0) throw new InvalidDataException("Pakiet jest uszkodzony");
            if (Bit(czyPowtG0, stan) == 0)
            {
                if (Bit(czyPowt0Dlugie, (stan << 4) + stanPoz) == 0)
                {
                    stan = stan < 7 ? 9 : 11;
                    Wstaw(Wez(rep0 + 1));
                    return;
                }
            }
            else
            {
                uint odl;
                if (Bit(czyPowtG1, stan) == 0) odl = rep1;
                else
                {
                    if (Bit(czyPowtG2, stan) == 0) odl = rep2;
                    else { odl = rep3; rep3 = rep2; }
                    rep2 = rep1;
                }
                rep1 = rep0;
                rep0 = odl;
            }
            dl = Dlugosc(dlugPowt, stanPoz);
            stan = stan < 7 ? 8 : 11;
        }
        else
        {
            rep3 = rep2; rep2 = rep1; rep1 = rep0;
            dl = Dlugosc(dlug, stanPoz);
            stan = stan < 7 ? 7 : 10;
            rep0 = Odleglosc(dl);
            if (rep0 == 0xFFFFFFFF)
            {
                // znacznik końca strumienia
                if (code != 0) throw new InvalidDataException("Pakiet jest uszkodzony");
                if (rozmiar >= 0 && wszystkich != rozmiar) throw new InvalidDataException("Pakiet jest ucięty");
                koniec = true;
                return;
            }
            if (rep0 >= rozmiarOkna || (!pelne && rep0 >= pozOkna)) throw new InvalidDataException("Pakiet jest uszkodzony");
        }
        dl += 2;
        if (rozmiar >= 0 && rozmiar - wszystkich < dl) throw new InvalidDataException("Pakiet jest uszkodzony");
        doSkopiowania = dl;
        while (doSkopiowania > 0 && wszystkich < limit) { Wstaw(Wez(rep0 + 1)); doSkopiowania--; }
    }

    public override int Read(byte[] bufor, int przes, int ile)
    {
        if (ile <= 0) return 0;
        // nie więcej niż pół okna naraz, żeby nie nadpisać bajtów, których jeszcze nie oddaliśmy
        int mozna = (int)Math.Min(ile, rozmiarOkna / 2);
        long cel = oddanych + mozna;
        while (!koniec && wszystkich < cel) Krok(cel);
        int gotowe = (int)(wszystkich - oddanych);
        if (gotowe <= 0) return 0;
        // bajty [oddanych, wszystkich) leżą w oknie tuż przed pozOkna
        long start = (pozOkna - (long)gotowe) % rozmiarOkna;
        if (start < 0) start += rozmiarOkna;
        int pierwsza = (int)Math.Min(gotowe, rozmiarOkna - start);
        Buffer.BlockCopy(okno, (int)start, bufor, przes, pierwsza);
        if (gotowe > pierwsza) Buffer.BlockCopy(okno, 0, bufor, przes + pierwsza, gotowe - pierwsza);
        oddanych += gotowe;
        return gotowe;
    }

    public override bool CanRead { get { return true; } }
    public override bool CanSeek { get { return false; } }
    public override bool CanWrite { get { return false; } }
    public override long Length { get { throw new NotSupportedException(); } }
    public override long Position { get { return oddanych; } set { throw new NotSupportedException(); } }
    public override void Flush() { }
    public override long Seek(long o, SeekOrigin s) { throw new NotSupportedException(); }
    public override void SetLength(long v) { throw new NotSupportedException(); }
    public override void Write(byte[] b, int o, int c) { throw new NotSupportedException(); }
}
