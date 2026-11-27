# Ikona LivePL na Maca (assets/mac/LivePL.icns) z logo 1024 px (assets/mac/wstega-1024.png, to samo co na iPhonie).
# Kształt jak w programach macOS: białe zaokrąglone pole 824 px na przezroczystym tle 1024 px, z lekkim cieniem.
# Uruchom (potrzebny Pillow):  python narzedzia/mac/ikona.py
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

KATALOG = Path(__file__).resolve().parents[2] / 'assets' / 'mac'
ROZMIAR, POLE, ZAOKRAGLENIE = 1024, 824, 185
logo = Image.open(KATALOG / 'wstega-1024.png').convert('RGBA').resize((POLE, POLE), Image.LANCZOS)

maska = Image.new('L', (POLE, POLE), 0)
ImageDraw.Draw(maska).rounded_rectangle((0, 0, POLE - 1, POLE - 1), ZAOKRAGLENIE, fill=255)

ikona = Image.new('RGBA', (ROZMIAR, ROZMIAR), (0, 0, 0, 0))
x = (ROZMIAR - POLE) // 2
# cień pod polem (jak w ikonach macOS: miękki, trochę niżej)
cien = Image.new('RGBA', (ROZMIAR, ROZMIAR), (0, 0, 0, 0))
cien.paste((0, 0, 0, 70), (x, x + 12), maska)
ikona = Image.alpha_composite(ikona, cien.filter(ImageFilter.GaussianBlur(14)))
pole = Image.new('RGBA', (POLE, POLE), (0, 0, 0, 0))
pole.paste(logo, (0, 0), maska)
ikona.alpha_composite(pole, (x, x))

ikona.save(KATALOG / 'ikona-1024.png')
ikona.save(KATALOG / 'LivePL.icns', sizes=[(16, 16), (32, 32), (64, 64), (128, 128), (256, 256), (512, 512), (1024, 1024)])
print('Gotowe:', KATALOG / 'LivePL.icns')
