# -*- coding: utf-8 -*-
"""把用户做的奔跑 GIF（9 帧 / 140ms / 512x361）用到响铃页。

为什么拆帧而不是直接放 GIF：
  · Compose 的 painterResource 不会播 GIF
  · AnimatedImageDrawable 要 API 28+，我们 minSdk 26
  · 9 帧 WebP 合计约 150KB，比 223KB 的 GIF 还小，且不需要引入 Coil 之类的依赖

同时处理背景：GIF 是 P 模式，先看它有没有真透明；如果是白底就做一次
"接近纯白 → 透明"的抠图（只吃 R/G/B>245，不会伤到米色的狗毛）。
"""
import os
from PIL import Image

SRC = r"D:\My Projects\_gif-src\run.gif"
DST = r"D:\My Projects\apps\RuyueAlarm\app\src\main\res\drawable-nodpi"
WIDTH = 320  # 响铃页显示 120~150dp，@2x 足够

im = Image.open(SRC)
n = im.n_frames
os.makedirs(DST, exist_ok=True)

frames = []
for i in range(n):
    im.seek(i)
    fr = im.convert("RGBA")
    fr = fr.resize((WIDTH, round(fr.height * WIDTH / fr.width)), Image.LANCZOS)
    frames.append(fr)

# 透明度检查：四角像素
corners = [frames[0].getpixel((2, 2)), frames[0].getpixel((WIDTH - 3, 2)),
           frames[0].getpixel((2, frames[0].height - 3))]
print("四角像素:", corners)
need_key = any(c[3] > 200 and c[0] > 240 and c[1] > 240 and c[2] > 240 for c in corners)
print("需要抠白底:", need_key)

total = 0
for i, fr in enumerate(frames):
    px = fr.load()
    w, h = fr.size
    if need_key:
        for y in range(h):
            for x in range(w):
                r, g, b, a = px[x, y]
                if r > 245 and g > 245 and b > 245:
                    px[x, y] = (r, g, b, 0)
    path = os.path.join(DST, f"dog_run_{i:02d}.webp")
    fr.save(path, "WEBP", quality=92, method=6)
    kb = os.path.getsize(path) / 1024
    total += kb
    if i in (0, n - 1) or i % 3 == 0:
        print(f"  dog_run_{i:02d}.webp  {kb:.1f} KB  {fr.size}")
print(f"共 {n} 帧，合计 {total:.1f} KB（原 GIF {os.path.getsize(SRC)/1024:.1f} KB）")
