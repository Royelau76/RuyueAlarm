# -*- coding: utf-8 -*-
"""第二批 4 个动画：sit_a/sit_b/sleep/sniff_grass。

映射（都是"替换"当前同名语义的姿态）：
  sit_a（带爱心）  → SIT_B     响铃页点完「知道了」的收尾坐姿
  sit_b（大笑）    → SIT_LAUGH 还差 1 个条件
  sleep           → SLEEP     暂停中（第一次有睡觉动画）
  sniff_grass     → LIE_DOWN  还差 2 个以上
旧的 shake / roll_over 帧保留在资源里但不再被引用。
"""
import glob
import os
import re

from PIL import Image

DESK = os.path.expanduser(r"~\Desktop")
DST = r"D:\My Projects\apps\RuyueAlarm\app\src\main\res\drawable-nodpi"
WIDTH = 320

JOBS = [("sit_a", "dog_sit_a"), ("sit_b", "dog_sit_b2"), ("sleep", "dog_sleep2"), ("sniff_grass", "dog_sniff")]

srcs = {}
for p in glob.glob(os.path.join(DESK, "*.gif")):
    base = os.path.basename(p).lower()
    for name, prefix in JOBS:
        if name in base:
            srcs.setdefault(name, p)

INFO = {}
for name, prefix in JOBS:
    src = srcs.get(name)
    if not src:
        print(f"  ✗ 桌面找不到 {name}.gif")
        continue
    for old in glob.glob(os.path.join(DST, f"{prefix}_*.webp")):
        os.remove(old)
    im = Image.open(src)
    n = im.n_frames
    frames, durs = [], []
    for i in range(n):
        im.seek(i)
        durs.append(im.info.get("duration", 0))
        fr = im.convert("RGBA")
        frames.append(fr.resize((WIDTH, round(fr.height * WIDTH / fr.width)), Image.LANCZOS))
    c = frames[0].getpixel((2, 2))
    key = c[3] > 200 and min(c[0], c[1], c[2]) > 240
    total = 0
    for i, fr in enumerate(frames):
        if key:
            px = fr.load()
            for y in range(fr.height):
                for x in range(fr.width):
                    r, g, b, a = px[x, y]
                    if r > 245 and g > 245 and b > 245:
                        px[x, y] = (r, g, b, 0)
        p = os.path.join(DST, f"{prefix}_{i:02d}.webp")
        fr.save(p, "WEBP", quality=92, method=6)
        total += os.path.getsize(p) / 1024
    INFO[name] = (prefix, n, durs[0] if durs and durs[0] > 0 else 150)
    print(f"  ✓ {name}: {n} 帧 · {INFO[name][2]}ms · {total:.1f}KB · 抠白底={key}")

AK = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm"
P = os.path.join(AK, "ui/design/DesignComponents.kt")
s = open(P, encoding="utf-8").read()


def frames_of(prefix, n):
    items = [f"R.drawable.{prefix}_{i:02d}" for i in range(n)]
    return ",\n            ".join(", ".join(items[i:i + 3]) for i in range(0, len(items), 3))


# 逐个替换枚举里的帧数组 + 帧时长
specs = [
    ("SLEEP", "sleep", None),        # SLEEP 原来是单帧 intArrayOf(R.drawable.dog_sleep), 3400
    ("LIE_DOWN", "sniff_grass", None),
    ("SIT_LAUGH", "sit_b", None),
    ("SIT_B", "sit_a", None),
]

# SLEEP：带参数的单行 → 多帧
if "sleep" in INFO:
    prefix, n, ms = INFO["sleep"]
    old = "SLEEP(intArrayOf(R.drawable.dog_sleep), 3400),"
    new = f"SLEEP(\n        intArrayOf(\n            {frames_of(prefix, n)}\n        ),\n        {ms},\n    ),"
    if old in s:
        s = s.replace(old, new)
        print("  ✓ SLEEP 换成帧动画")
    else:
        print("  ✗ SLEEP 锚点没找到")

# SIT_B：带参数单行
if "sit_a" in INFO:
    prefix, n, ms = INFO["sit_a"]
    old = "SIT_B(intArrayOf(R.drawable.dog_sit_b), 3000),"
    new = f"SIT_B(\n        intArrayOf(\n            {frames_of(prefix, n)}\n        ),\n        {ms},\n    ),"
    if old in s:
        s = s.replace(old, new)
        print("  ✓ SIT_B 换成 sit_a 帧")
    else:
        print("  ✗ SIT_B 锚点没找到")

# LIE_DOWN / SIT_LAUGH：多行 intArrayOf 块 → 用正则整块替换
for pose, key in (("LIE_DOWN", "sniff_grass"), ("SIT_LAUGH", "sit_b")):
    if key not in INFO:
        continue
    prefix, n, ms = INFO[key]
    block = (
        f"{pose}(\n        intArrayOf(\n            {frames_of(prefix, n)}\n        ),\n        {ms},\n    ),"
    )
    pat = re.compile(rf"{pose}\(\s*\n?\s*intArrayOf\((?:[^)]*)\)\s*,\s*\d+\s*,?\s*\)", re.S)
    s2, cnt = pat.subn(block, s, count=1)
    if cnt:
        s = s2
        print(f"  ✓ {pose} 换成 {key} 帧")
    else:
        print(f"  ✗ {pose} 锚点没找到")

open(P, "w", encoding="utf-8", newline="\n").write(s)
print("完成")
