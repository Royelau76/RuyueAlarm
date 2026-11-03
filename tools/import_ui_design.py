"""Step 0：把 UI 设计交付物里**必要的那部分**整理进仓库。

原则：
  · 文档 + 截图进 `docs/ui/`（会被 review、会被人翻）
  · 小狗姿态进 `app/src/main/res/drawable-nodpi/`（代码要用的素材）
  · 8.7MB 的 WebP 动画、20MB 原始 zip **不入库**（README 也说了动画应该用代码做）

体积控制：
  · 小狗真实清晰度上限只有 220px 物理像素（README 第三节），所以输出 256px 就够，
    再大只是插值。PNG → WebP(q92, 带 alpha)，单张约 30~60KB。
  · design-board 原图 3360×4982 / 3.7MB → 缩到 1200 宽做预览（约 1/6 体积）。
  · 10 张手机截图保持 864×1824 原尺寸（它们是规格依据，不能糊）。

跑法：<bundled python> tools/import_ui_design.py
"""
import os
import shutil

from PIL import Image

SRC = r"D:\My Projects\_ui-design\交付-如约UI"
REPO = r"D:\My Projects\apps\RuyueAlarm"
DOCS = os.path.join(REPO, "docs", "ui")
SCREENS = os.path.join(DOCS, "screens")
RES = os.path.join(REPO, "app", "src", "main", "res", "drawable-nodpi")

# 设计里真正用到的 6 个姿态（README 第五节的状态表）
POSES = ["sleep", "lie_down", "sit_laugh", "beg_stand", "run_happy", "sit_b"]

for d in (DOCS, SCREENS, RES):
    os.makedirs(d, exist_ok=True)

total = 0
print("=== 文档 ===")
for name in ("README-给工程师.md", "spec.md"):
    src = os.path.join(SRC, name)
    dst = os.path.join(DOCS, name)
    shutil.copy2(src, dst)
    kb = os.path.getsize(dst) / 1024
    total += kb
    print(f"  {name:28s} {kb:7.1f} KB")

print("=== 十张界面截图（原尺寸 864×1824）===")
shots_src = os.path.join(SRC, "shots", "phones")
for name in sorted(os.listdir(shots_src)):
    if not name.endswith(".png"):
        continue
    dst = os.path.join(SCREENS, name)
    shutil.copy2(os.path.join(shots_src, name), dst)
    kb = os.path.getsize(dst) / 1024
    total += kb
    print(f"  {name:28s} {kb:7.1f} KB")

print("=== 设计交付板（缩到 1200 宽做预览）===")
board_src = os.path.join(SRC, "design-board.png")
board_dst = os.path.join(DOCS, "design-board-预览.png")
with Image.open(board_src) as im:
    w, h = im.size
    nh = round(h * 1200 / w)
    im.convert("RGB").resize((1200, nh), Image.LANCZOS).save(board_dst, optimize=True)
kb = os.path.getsize(board_dst) / 1024
total += kb
print(f"  design-board-预览.png        {kb:7.1f} KB  （{w}×{h} → 1200×{nh}）")

print("=== 小狗姿态 → drawable-nodpi（256px WebP）===")
for pose in POSES:
    src = os.path.join(SRC, "dog", "final", f"{pose}.png")
    if not os.path.exists(src):
        print(f"  ✗ 缺少 {pose}.png")
        continue
    dst = os.path.join(RES, f"dog_{pose}.webp")
    with Image.open(src) as im:
        im = im.convert("RGBA")
        w, h = im.size
        nh = round(h * 256 / w)
        im.resize((256, nh), Image.LANCZOS).save(dst, "WEBP", quality=92, method=6)
    kb = os.path.getsize(dst) / 1024
    total += kb
    print(f"  dog_{pose}.webp{'':14s} {kb:7.1f} KB  ({w}×{h} → 256×{nh})")

print(f"\n总计入库约 {total / 1024:.2f} MB（未含 .gitignore 掉的原始 zip 20MB 与 8.7MB 动画）")
