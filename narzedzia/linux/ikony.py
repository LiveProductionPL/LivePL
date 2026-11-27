# Ikony LivePL na Linuxa (assets/linux) z tych samych obrazów co na Maca i w Windows.
#  - livepl-<rozmiar>.png: ikona programu (menu programów, dok, okno), białe zaokrąglone pole jak na Macu
#  - zasobnik-jasna.png / zasobnik-ciemna.png: wstęga przy zegarze (czerwona na jasny pasek, biała na ciemny)
# Uruchom (potrzebny Pillow):  python narzedzia/linux/ikony.py
from pathlib import Path
from PIL import Image

ASSETS = Path(__file__).resolve().parents[2] / 'assets'
CEL = ASSETS / 'linux'
CEL.mkdir(exist_ok=True)

ikona = Image.open(ASSETS / 'mac' / 'ikona-1024.png').convert('RGBA')
for r in (16, 24, 32, 48, 64, 128, 256, 512):
    ikona.resize((r, r), Image.LANCZOS).save(CEL / f'livepl-{r}.png', optimize=True)

for nazwa, zrodlo in (('zasobnik-jasna', 'wstega-jasna.png'), ('zasobnik-ciemna', 'wstega-ciemna.png')):
    Image.open(ASSETS / zrodlo).convert('RGBA').resize((64, 64), Image.LANCZOS).save(CEL / f'{nazwa}.png', optimize=True)

print('Gotowe:', CEL)
