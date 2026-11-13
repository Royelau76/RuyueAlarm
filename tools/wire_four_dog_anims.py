# -*- coding: utf-8 -*-
"""把 4 个新做的动画（walk_fast / beg_stand / roll_over / shake）接到各个小狗姿态上。

映射（按"这个姿态在什么状态下出现"）：
  BEG_STAND  全部就绪      → beg_stand.gif（抬手 + 爱心）
  SIT_LAUGH  还差 1 个条件  → shake.gif（坐着笑、甩水）130ms
  LIE_DOWN   还差 2 个以上  → roll_over.gif（躺着打滚）180ms
  WALK       还没有约定     → walk_fast.gif（走过来）160ms
  SLEEP/SIT_B/RUN_HAPPY  仍是单帧静态（这次没给睡觉动画）
"""
import glob
import os

from PIL import Image

DESK = os.path.expanduser(r"~\Desktop")
DST = r"D:\My Projects\apps\RuyueAlarm\app\src\main\res\drawable-nodpi"
WIDTH = 320

JOBS = [
    ("walk_fast", "dog_walk"),
    ("beg_stand", "dog_beg"),
    ("roll_over", "dog_roll"),
    ("shake", "dog_shake"),
]

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
        print(f"  ✗ 找不到 {name}.gif")
        continue
    for old in glob.glob(os.path.join(DST, f"{prefix}_*.webp")):
        os.remove(old)
    im = Image.open(src)
    n = im.n_frames
    durs = []
    frames = []
    for i in range(n):
        im.seek(i)
        durs.append(im.info.get("duration", 0))
        fr = im.convert("RGBA")
        frames.append(fr.resize((WIDTH, round(fr.height * WIDTH / fr.width)), Image.LANCZOS))
    c = frames[0].getpixel((2, 2))
    need_key = c[3] > 200 and min(c[0], c[1], c[2]) > 240
    total = 0
    for i, fr in enumerate(frames):
        if need_key:
            px = fr.load()
            for y in range(fr.height):
                for x in range(fr.width):
                    r, g, b, a = px[x, y]
                    if r > 245 and g > 245 and b > 245:
                        px[x, y] = (r, g, b, 0)
        p = os.path.join(DST, f"{prefix}_{i:02d}.webp")
        fr.save(p, "WEBP", quality=92, method=6)
        total += os.path.getsize(p) / 1024
    ms = durs[0] if durs and durs[0] > 0 else 150
    INFO[name] = (prefix, n, ms)
    print(f"  ✓ {name}: {n} 帧 · {ms}ms · {total:.1f}KB · 抠白底={need_key} · 尺寸 {frames[0].size}")

# ---------------- Kotlin ----------------
AK = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm"


def frames_of(prefix, n):
    items = [f"R.drawable.{prefix}_{i:02d}" for i in range(n)]
    return ",\n            ".join(", ".join(items[i:i + 3]) for i in range(0, len(items), 3))


P = os.path.join(AK, "ui/design/DesignComponents.kt")
s = open(P, encoding="utf-8").read()

old_enum = """enum class DogPose(val res: Int) {
    SLEEP(R.drawable.dog_sleep),
    LIE_DOWN(R.drawable.dog_lie_down),
    SIT_LAUGH(R.drawable.dog_sit_laugh),
    BEG_STAND(R.drawable.dog_beg_stand),
    RUN_HAPPY(R.drawable.dog_run_happy),
    SIT_B(R.drawable.dog_sit_b),
}"""

new_enum = """/**
 * 小狗姿态。2026-11-13 起大部分姿态是**帧动画**（用户自制 9 帧 GIF 拆的 WebP）；
 * 只有 SLEEP / SIT_B / RUN_HAPPY 还是单帧（`frames.size == 1` → 画面恒定，等于静态图）。
 *
 * ⚠️ 不要为了"静态姿态省一次循环"再写 if 分支 —— 分支会改变组合槽表结构（见 docs/版本历史.md）。
 * 单帧也走同一个 DogFrameLoop，只是画面不变。
 */
enum class DogPose(val frames: IntArray, val frameMillis: Int = 150) {
    /** 暂停中：单帧静态（还没做睡觉动画） */
    SLEEP(intArrayOf(R.drawable.dog_sleep), 3400),

    /** 还差 2 个以上条件 / 空状态：躺着打滚 */
    LIE_DOWN(
        intArrayOf(
            %s
        ),
        180,
    ),

    /** 还差 1 个条件：坐着笑（甩水） */
    SIT_LAUGH(
        intArrayOf(
            %s
        ),
        130,
    ),

    /** 全部就绪：抬手等（带爱心） */
    BEG_STAND(
        intArrayOf(
            %s
        ),
        180,
    ),

    /** 空状态：小狗走过来 */
    WALK(
        intArrayOf(
            %s
        ),
        160,
    ),

    RUN_HAPPY(intArrayOf(R.drawable.dog_run_happy)),
    SIT_B(intArrayOf(R.drawable.dog_sit_b), 3000),
}""" % (
    frames_of("dog_roll", INFO["roll_over"][1]),
    frames_of("dog_shake", INFO["shake"][1]),
    frames_of("dog_beg", INFO["beg_stand"][1]),
    frames_of("dog_walk", INFO["walk_fast"][1]),
)
if old_enum in s:
    s = s.replace(old_enum, new_enum)
    print("  ✓ DogPose 改成帧数组")
else:
    print("  ✗ DogPose 没匹配到")

old_mascot = """    Image(
        painter = painterResource(pose.res),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleY = 1f + 0.018f * t
                scaleX = 1f - 0.006f * t
                transformOrigin = TransformOrigin(0.5f, 0.93f)
            },
    )
}"""
new_mascot = """    // 呼吸（轻微缩放）叠在帧动画上：帧负责动作，缩放负责"活着"的微动
    DogFrameLoop(
        frames = pose.frames,
        frameMillis = pose.frameMillis,
        height = size,
        modifier = modifier.graphicsLayer {
            scaleY = 1f + 0.018f * t
            scaleX = 1f - 0.006f * t
            transformOrigin = TransformOrigin(0.5f, 0.93f)
        },
    )
}"""
if old_mascot in s:
    s = s.replace(old_mascot, new_mascot)
    print("  ✓ DogMascot 改用帧动画（单 composable，无分支）")
else:
    print("  ✗ DogMascot 没匹配到")

# 周期表：新增 WALK
s = s.replace(
    """    val periodMs = when (pose) {
        DogPose.SLEEP -> 3400
        DogPose.LIE_DOWN -> 3200
        DogPose.SIT_B -> 3000
        else -> 2700
    }""",
    """    val periodMs = when (pose) {
        DogPose.SLEEP -> 3400
        DogPose.LIE_DOWN -> 3200
        DogPose.SIT_B -> 3000
        DogPose.WALK -> 2800
        else -> 2700
    }""",
)
open(P, "w", encoding="utf-8", newline="\n").write(s)

# 空状态换成 WALK
P2 = os.path.join(AK, "ui/ReminderListScreen.kt")
s2 = open(P2, encoding="utf-8").read()
if "DogMascot(pose = DogPose.LIE_DOWN, size = 140.dp)" in s2:
    s2 = s2.replace("DogMascot(pose = DogPose.LIE_DOWN, size = 140.dp)",
                    "DogMascot(pose = DogPose.WALK, size = 140.dp)")
    open(P2, "w", encoding="utf-8", newline="\n").write(s2)
    print("  ✓ 空状态改用 WALK")
else:
    print("  ✗ 空状态锚点没找到")
print("完成")
