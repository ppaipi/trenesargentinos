"""Genera trenar-icono-512.png (ícono para Play Store) con las mismas formas
que trenar-icono.svg / res/drawable/ic_launcher_*.xml. Uso: python3 generar_icono.py"""
from PIL import Image, ImageDraw

LADO = 512
ESC = 4  # se dibuja más grande y se achica: bordes suaves
s = LADO * ESC / 108  # unidades del lienzo de 108 → píxeles


def color(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


img = Image.new("RGB", (LADO * ESC, LADO * ESC))
a, b = color("#89F7FE"), color("#3D7FE0")
d = ImageDraw.Draw(img)
n = LADO * ESC
for k in range(2 * n):  # degradado diagonal
    t = k / (2 * n - 1)
    c = tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))
    d.line([(k, 0), (0, k)], fill=c)


def caja(x, y, w, h):
    return [x * s, y * s, (x + w) * s, (y + h) * s]


d.rounded_rectangle(caja(33, 24, 42, 52), radius=10 * s, fill="#FFFFFF")
d.rounded_rectangle(caja(38.5, 30, 31, 17), radius=3.5 * s, fill="#0B2547")
d.rectangle(caja(33, 52, 42, 5), fill="#74ACDF")
for cx in (43, 65):
    d.ellipse([(cx - 4) * s, 62 * s, (cx + 4) * s, 70 * s], fill="#F6B40E")
for x1, x2 in ((42, 36.5), (66, 71.5)):
    d.line([(x1 * s, 77 * s), (x2 * s, 86 * s)], fill="#FFFFFF", width=round(4.2 * s))
    for x, y in ((x1, 77), (x2, 86)):  # puntas redondeadas
        r = 2.1 * s
        d.ellipse([x * s - r, y * s - r, x * s + r, y * s + r], fill="#FFFFFF")

img.resize((LADO, LADO), Image.LANCZOS).save("trenar-icono-512.png")
