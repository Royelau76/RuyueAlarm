# -*- coding: utf-8 -*-
"""换成新的 chase_ball GIF（旧的画面有问题）。

流程：取桌面上最新的 gif → 清掉旧的 dog_chase_* → 重新拆帧 →
按实际帧数重写 CHASE_FRAMES 数组 → 打印帧数/时长供核对。
"""
import glob
import os
import re
import shutil

from PIL import Image

DESK = os.path.expanduser(r"~\Desktop")
gifs = sorted(glob.glob(os.path.join(DESK, "*.gif")), key=os.path.getmtime, reverse=True)
print("桌面 gif（新→旧）：")
for g in gifs[:4]:
    print("   ", os.path.basename(g), os.path.getsize(g) // 1024, "KB")
SRC = gifs[0]
print("选用:", os.path.basename(SRC))

WORK = r"D:\My Projects\_gif-src"
os.makedirs(WORK, exist_ok=True)
local = os.path.join(WORK, "chase2.gif")
shutil.copy2(SRC, local)

DST = r"D:\My Projects\apps\RuyueAlarm\app\src\main\res\drawable-nodpi"
for old in glob.glob(os.path.join(DST, "dog_chase_*.webp")):
    os.remove(old)
print("已清掉旧帧")

im = Image.open(local)
n = im.n_frames
durs = []
for i in range(n):
    im.seek(i)
    durs.append(im.info.get("duration", 0))
print(f"帧数 {n} · 尺寸 {im.size} · 每帧 {durs}")

WIDTH = 320
frames = []
for i in range(n):
    im.seek(i)
    fr = im.convert("RGBA")
    frames.append(fr.resize((WIDTH, round(fr.height * WIDTH / fr.width)), Image.LANCZOS))

c = frames[0].getpixel((2, 2))
need_key = c[3] > 200 and min(c[0], c[1], c[2]) > 240
print("四角像素:", c, "需要抠白底:", need_key)

total = 0
for i, fr in enumerate(frames):
    if need_key:
        px = fr.load()
        for y in range(fr.height):
            for x in range(fr.width):
                r, g, b, a = px[x, y]
                if r > 245 and g > 245 and b > 245:
                    px[x, y] = (r, g, b, 0)
    p = os.path.join(DST, f"dog_chase_{i:02d}.webp")
    fr.save(p, "WEBP", quality=92, method=6)
    total += os.path.getsize(p) / 1024
print(f"已写 {n} 帧，合计 {total:.1f} KB（尺寸 {frames[0].size}）")

# 按实际帧数重写 CHASE_FRAMES
P = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm\ui\design\DesignComponents.kt"
s = open(P, encoding="utf-8").read()
items = [f"R.drawable.dog_chase_{i:02d}" for i in range(n)]
lines = ",\n    ".join(
    ", ".join(items[i:i + 3]) for i in range(0, len(items), 3)
)
new_block = f"val CHASE_FRAMES = intArrayOf(\n    {lines},\n)"
s2 = re.sub(r"val CHASE_FRAMES = intArrayOf\(.*?\)", new_block, s, flags=re.S)
if s2 != s:
    open(P, "w", encoding="utf-8", newline="\n").write(s2)
    print("✓ CHASE_FRAMES 已按实际帧数重写")
else:
    print("✗ CHASE_FRAMES 没替换成功")

# 帧时长（写进下一行的默认值：RingActivity 里显式传的 180 也一起改成实际值）
frame_ms = durs[0] if durs and durs[0] > 0 else 180
RA = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm\alarm\RingActivity.kt"
s = open(RA, encoding="utf-8").read()
s = re.sub(r"frameMillis = if \(snoozing\) \d+ else 140", f"frameMillis = if (snoozing) {frame_ms} else 140", s)
open(RA, "w", encoding="utf-8", newline="\n").write(s)
print(f"✓ 追球帧时长设为 {frame_ms}ms")
