"""Genera los íconos de TrenAR a partir de trenar-logo-original.png (ilustración
hecha con ChatGPT, cuadrado redondeado sobre blanco).

- trenar-icono-512.png: ícono de Play Store (cuadrado lleno; Play redondea solo).
- res/mipmap-xxxhdpi/ic_launcher_arte.png: capa del ícono adaptativo (432 px =
  108 dp). Android muestra solo el centro y lo recorta (círculo, squircle...), así
  que el dibujo va achicado al 60 % y los bordes se extienden repitiendo la orilla.

Uso: python3 -I generar_icono.py
"""
from PIL import Image

ORIGINAL = "trenar-logo-original.png"
RES = "../app/src/main/res/mipmap-xxxhdpi/ic_launcher_arte.png"

im = Image.open(ORIGINAL).convert("RGB")
w, h = im.size
px = im.load()
blanco = lambda p: min(p) > 240
x0 = next(x for x in range(w) if not blanco(px[x, h // 2]))
x1 = next(x for x in range(w - 1, 0, -1) if not blanco(px[x, h // 2]))
y0 = next(y for y in range(h) if not blanco(px[w // 2, y]))
y1 = next(y for y in range(h - 1, 0, -1) if not blanco(px[w // 2, y]))
m = round((x1 - x0) * 0.08)  # margen para esquivar las esquinas redondeadas
arte = im.crop((x0 + m, y0 + m, x1 - m, y1 - m))
arte.resize((512, 512), Image.LANCZOS).save("trenar-icono-512.png")

LADO, ESCALA = 432, 0.60
s = round(LADO * ESCALA)
o = (LADO - s) // 2
a = arte.resize((s, s), Image.LANCZOS)
capa = Image.new("RGB", (LADO, LADO))
fin = o + s
# Orillas estiradas hacia afuera (la ilustración es plana: queda prolija).
capa.paste(a.crop((0, 0, s, 1)).resize((s, o)), (o, 0))
capa.paste(a.crop((0, s - 1, s, s)).resize((s, LADO - fin)), (o, fin))
capa.paste(a.crop((0, 0, 1, s)).resize((o, s)), (0, o))
capa.paste(a.crop((s - 1, 0, s, s)).resize((LADO - fin, s)), (fin, o))
for (cx, cy), (bx, by, bw, bh) in {
    (0, 0): (0, 0, o, o), (s - 1, 0): (fin, 0, LADO - fin, o),
    (0, s - 1): (0, fin, o, LADO - fin), (s - 1, s - 1): (fin, fin, LADO - fin, LADO - fin),
}.items():
    capa.paste(Image.new("RGB", (bw, bh), a.getpixel((cx, cy))), (bx, by))
capa.paste(a, (o, o))
capa.save(RES)
