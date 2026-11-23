#!/usr/bin/env python3
"""Czytelność karty pogody ze Startu TVML we wszystkich scenach (galeria z aplikacji, -galeria-pogody YES).

  python3 galeria-pogody.py <folder galeria>

W folderze: _tekst.png (sam tekst, maska), scena/<wariant>@<t>.png (tło z przyciemnieniem w kilku chwilach
animacji) i calosc/<wariant>.png (karta do obejrzenia). Dla każdego elementu tekstu (napis, data, temperatura,
opis, prognoza, imieniny na pasie ziemi) liczy kontrast tekstu z tłem dokładnie pod literami: dla białego tekstu
najjaśniejsze miejsca tła (95. percentyl jasności), dla ciemnych imienin na śniegu najciemniejsze, w najgorszej
chwili animacji. Cień pod tekstem w aplikacji jeszcze pomaga, tu go nie ma,
więc wynik jest ostrożny. Progi jak WCAG: 4,5 dla zwykłego tekstu, 3 dla dużej temperatury.
Wynik: raport.txt, wyniki.csv i arkusze PNG (arkusz-przeglad.png, arkusz-najgorsze.png).
"""
import csv
import glob
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

folder = sys.argv[1]
tekst = np.asarray(Image.open(os.path.join(folder, "_tekst.png")).convert("RGBA")).astype(float) / 255
maska = tekst[..., 3] > 0.5
H, W = maska.shape


def odcinki(rzut, luka):
    """Ciągłe odcinki True w rzucie, sklejone, gdy przerwa nie większa niż luka."""
    wynik, start, koniec, przerwa = [], None, None, 0
    for i, v in enumerate(rzut):
        if v:
            if start is None:
                start = i
            koniec, przerwa = i, 0
        elif start is not None:
            przerwa += 1
            if przerwa > luka:
                wynik.append((start, koniec + 1))
                start, przerwa = None, 0
    if start is not None:
        wynik.append((start, koniec + 1))
    return wynik


def podziel(y0, y1, x0, x1, poziom=0):
    """Cięcie X-Y: wiersze (przerwa ≥ 1 px), potem kolumny (przerwa > 30 px), aż do pojedynczych elementów."""
    m = maska[y0:y1, x0:x1]
    wiersze = odcinki(m.any(axis=1), 0)
    if len(wiersze) > 1:
        return [e for a, b in wiersze for e in podziel(y0 + a, y0 + b, x0, x1, poziom + 1)]
    kolumny = odcinki(m.any(axis=0), 30)
    if len(kolumny) > 1:
        return [e for a, b in kolumny for e in podziel(y0, y1, x0 + a, x0 + b, poziom + 1)]
    wiersze = odcinki(m.any(axis=1), 0)
    kolumny = odcinki(m.any(axis=0), 30)
    if not wiersze or not kolumny:
        return []
    return [(y0 + wiersze[0][0], y0 + wiersze[-1][1], x0 + kolumny[0][0], x0 + kolumny[-1][1])]


elementy = sorted(podziel(0, H, 0, W), key=lambda e: (e[0], e[2]))
# nazwy: dwa pierwsze wiersze to napis i data, potem lewa kolumna (temperatura, opis) i prawa (prognoza),
# na samym dole (pas ziemi, 22 pkt) imieniny
PAS_ZIEMI = 22
nazwane = []
lewe = 0
for i, (y0, y1, x0, x1) in enumerate(elementy):
    if y0 >= H - PAS_ZIEMI - 2:
        nazwa, prog = "imieniny", 4.5
    elif i == 0:
        nazwa, prog = "napis", 4.5
    elif i == 1:
        nazwa, prog = "data i imieniny", 4.5
    elif x0 < W / 2:
        nazwa, prog = ("temperatura", 3.0) if lewe == 0 else ("opis", 4.5)
        lewe += 1
    else:
        nazwa, prog = "prognoza", 4.5
    nazwane.append((nazwa, prog, (y0, y1, x0, x1)))


def jasnosc(rgb):
    c = rgb / 255
    lin = np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    return 0.2126 * lin[..., 0] + 0.7152 * lin[..., 1] + 0.0722 * lin[..., 2]


def kontrast(l):
    return 1.05 / (l + 0.05)


# imieniny na jasnej ziemi (zima, Święta) są ciemne (#1e293b), w pozostałych scenach jasne (#f6efe4)
L_CIEMNY = float(jasnosc(np.array([30.0, 41.0, 59.0])))
L_JASNY = float(jasnosc(np.array([246.0, 239.0, 228.0])))


def kontrast_elementu(w, nazwa, pod):
    """Kontrast tekstu z tłem pod nim; dla jasnego tekstu liczą się najjaśniejsze miejsca, dla ciemnego najciemniejsze."""
    if nazwa == "imieniny":
        if "-zima-" in w or "-swieta-" in w:
            return (float(np.percentile(pod, 5)) + 0.05) / (L_CIEMNY + 0.05)
        return (L_JASNY + 0.05) / (float(np.percentile(pod, 95)) + 0.05)
    return kontrast(float(np.percentile(pod, 95)))


warianty = sorted({os.path.basename(p).rsplit("@", 1)[0] for p in glob.glob(os.path.join(folder, "scena", "*.png"))})
wyniki = []  # (wariant, element, kontrast, próg)
for w in warianty:
    najgorsze = {}
    for plik in glob.glob(os.path.join(folder, "scena", w + "@*.png")):
        obraz = np.asarray(Image.open(plik).convert("RGB")).astype(float)
        if obraz.shape[:2] != maska.shape:
            continue
        L = jasnosc(obraz)
        for nazwa, prog, (y0, y1, x0, x1) in nazwane:
            pod = L[y0:y1, x0:x1][maska[y0:y1, x0:x1]]
            if pod.size == 0:
                continue
            k = kontrast_elementu(w, nazwa, pod)
            klucz = (nazwa, prog)
            najgorsze[klucz] = min(najgorsze.get(klucz, 99), k)
    for (nazwa, prog), k in najgorsze.items():
        wyniki.append((w, nazwa, k, prog))

with open(os.path.join(folder, "wyniki.csv"), "w", newline="", encoding="utf-8") as f:
    zapis = csv.writer(f)
    zapis.writerow(["wariant", "element", "kontrast", "prog"])
    for w, e, k, p in wyniki:
        zapis.writerow([w, e, f"{k:.2f}", p])

slabe = sorted([r for r in wyniki if r[2] < r[3]], key=lambda r: r[2] / r[3])
bardzo_slabe = [r for r in slabe if r[2] < r[3] * 0.67]
z_wariantu = {}
for w, e, k, p in wyniki:
    z_wariantu[w] = min(z_wariantu.get(w, 99), k / p)

with open(os.path.join(folder, "raport.txt"), "w", encoding="utf-8") as f:
    f.write("Czytelność karty pogody (Start TVML): kontrast białego tekstu z tłem pod literami, bez cienia\n")
    f.write(f"wariantów: {len(warianty)}, elementów: {', '.join(n for n, _, _ in nazwane)}\n")
    f.write(f"poniżej progu (4,5 / temperatura 3): {len(slabe)} z {len(wyniki)}; poniżej 2/3 progu: {len(bardzo_slabe)}\n\n")
    f.write("Najsłabsze (wariant, element, kontrast / próg):\n")
    for w, e, k, p in slabe[:60]:
        f.write(f"  {w:42s} {e:16s} {k:5.2f} / {p}\n")
    f.write("\nNajgorszy element w każdym wariancie (stosunek do progu, 1,00 = na progu):\n")
    for w in sorted(z_wariantu, key=z_wariantu.get):
        f.write(f"  {w:42s} {z_wariantu[w]:.2f}\n")
print(open(os.path.join(folder, "raport.txt"), encoding="utf-8").read()[:3000])


def czcionka(rozmiar):
    for sciezka in ("/System/Library/Fonts/Supplemental/Arial.ttf", "/Library/Fonts/Arial.ttf", "C:/Windows/Fonts/arial.ttf"):
        if os.path.exists(sciezka):
            return ImageFont.truetype(sciezka, rozmiar)
    return ImageFont.load_default()


def arkusz(nazwy, plik, tytul):
    karty = [(n, os.path.join(folder, "calosc", n + ".png")) for n in nazwy]
    karty = [(n, p) for n, p in karty if os.path.exists(p)]
    if not karty:
        return
    pierwsza = Image.open(karty[0][1])
    kw, kh = pierwsza.size
    kol, odstep, podpis = 2, 24, 34
    wiersze = (len(karty) + kol - 1) // kol
    arkusz = Image.new("RGB", (kol * kw + (kol + 1) * odstep, 70 + wiersze * (kh + podpis + odstep)), (238, 241, 245))
    d = ImageDraw.Draw(arkusz)
    d.text((odstep, 18), tytul, fill=(10, 31, 68), font=czcionka(30))
    for i, (n, p) in enumerate(karty):
        x = odstep + (i % kol) * (kw + odstep)
        y = 70 + (i // kol) * (kh + podpis + odstep)
        d.text((x, y + 4), f"{n}   (najgorszy element: {z_wariantu.get(n, 0):.2f} progu)", fill=(40, 48, 60), font=czcionka(20))
        arkusz.paste(Image.open(p).convert("RGB"), (x, y + podpis))
    arkusz.save(os.path.join(folder, plik))


przeglad = [
    "zwykly-jesien-day-clear-day", "zwykly-jesien-night-clear-night",
    "zwykly-jesien-dusk-clouds-rzadkie", "zwykly-jesien-night-rain",
    "zwykly-jesien-day-fog", "zwykly-jesien-day-wind",
    "zwykly-wiosna-day-clouds-geste", "zwykly-wiosna-night-storm",
    "zwykly-lato-day-clear-day", "zwykly-lato-dusk-clear-day",
    "zwykly-zima-day-snow", "zwykly-zima-night-snow",
    "wersja-listopad-dzien-chmury", "wersja-listopad-noc-slonce",
    "wersja-zima-dzien-mgla", "wersja-zima-noc-snieg",
    "wersja-swieta-dzien-slonce", "wersja-swieta-noc-snieg",
    "wersja-sylwester-zmierzch-przejasnienia", "wersja-sylwester-noc-slonce",
]
arkusz(przeglad, "arkusz-przeglad.png", "Karta pogody na Starcie: wybrane sceny, dzień i noc")
arkusz(sorted(z_wariantu, key=z_wariantu.get)[:12], "arkusz-najgorsze.png", "12 wariantów z najsłabszym kontrastem")
