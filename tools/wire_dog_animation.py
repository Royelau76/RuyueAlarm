# -*- coding: utf-8 -*-
"""① 把 9 帧奔跑动画拆成 WebP 资源；② 加 DogRunLoop 并换掉响铃页的小狗。"""
import os
from PIL import Image

# ---------------- ① 拆帧 ----------------
SRC = r"D:\My Projects\_gif-src\run.gif"
DST = r"D:\My Projects\apps\RuyueAlarm\app\src\main\res\drawable-nodpi"
WIDTH = 320

im = Image.open(SRC)
n = im.n_frames
os.makedirs(DST, exist_ok=True)
frames = []
for i in range(n):
    im.seek(i)
    fr = im.convert("RGBA")
    frames.append(fr.resize((WIDTH, round(fr.height * WIDTH / fr.width)), Image.LANCZOS))

corner = frames[0].getpixel((2, 2))
need_key = corner[3] > 200 and min(corner[0], corner[1], corner[2]) > 240
print(f"帧数 {n} · 四角像素 {corner} · 需要抠白底: {need_key}")

total = 0
for i, fr in enumerate(frames):
    if need_key:
        px = fr.load()
        for y in range(fr.height):
            for x in range(fr.width):
                r, g, b, a = px[x, y]
                if r > 245 and g > 245 and b > 245:
                    px[x, y] = (r, g, b, 0)
    path = os.path.join(DST, f"dog_run_{i:02d}.webp")
    fr.save(path, "WEBP", quality=92, method=6)
    total += os.path.getsize(path) / 1024
print(f"已写 {n} 帧，合计 {total:.1f} KB")

# ---------------- ② Kotlin ----------------
AK = r"D:\My Projects\apps\RuyueAlarm\app\src\main\java\com\ruyue\alarm"


def patch(rel, pairs):
    p = os.path.join(AK, rel)
    s = open(p, encoding="utf-8").read()
    for old, new, label in pairs:
        if old in s:
            s = s.replace(old, new)
            print("  ✓", label)
        else:
            print("  ✗ 没匹配到:", label)
    open(p, "w", encoding="utf-8", newline="\n").write(s)


frame_names = ", ".join(f"R.drawable.dog_run_{i:02d}" for i in range(n))
patch("ui/design/DesignComponents.kt", [(
    """// ---------------- 状态胶囊 ----------------""",
    """/**
 * 响铃页的「奔跑前来」动画 —— 用户 2026-11-09 自制，9 帧 / 140ms 一帧 / 一轮 1.26 秒。
 *
 * 为什么不直接放 GIF：
 *  · Compose 的 painterResource 不会播 GIF
 *  · AnimatedImageDrawable 要 API 28+，而本项目 minSdk 26
 *  · 拆成 9 张 320px WebP 合计约 150KB，比原 GIF（223KB）还小，也不用引入 Coil
 *
 * 用 rememberInfiniteTransition 驱动帧号（而不是 LaunchedEffect + delay），
 * 这样 Compose 会跟着屏幕刷新节奏走，不会掉帧。
 */
@Composable
fun DogRunLoop(height: Dp = 110.dp, modifier: Modifier = Modifier) {
    val frames = remember {
        listOf(%s)
    }
    val transition = rememberInfiniteTransition(label = "dogRun")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1260, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "frameIndex",
    )
    val index = (progress * frames.size).toInt().coerceIn(0, frames.lastIndex)
    Image(
        painter = painterResource(frames[index]),
        contentDescription = null,
        modifier = modifier
            .height(height)
            .aspectRatio(512f / 361f),
    )
}

// ---------------- 状态胶囊 ----------------""" % frame_names,
    "加 DogRunLoop",
)])

# import：LinearEasing / aspectRatio
p = os.path.join(AK, "ui/design/DesignComponents.kt")
s = open(p, encoding="utf-8").read()
for imp in ["import androidx.compose.animation.core.LinearEasing",
            "import androidx.compose.foundation.layout.aspectRatio"]:
    if imp not in s:
        s = s.replace("import androidx.compose.animation.core.FastOutSlowInEasing",
                      imp + "\nimport androidx.compose.animation.core.FastOutSlowInEasing", 1)
open(p, "w", encoding="utf-8", newline="\n").write(s)
print("  ✓ 补 import")

# 响铃页换成动画
patch("alarm/RingActivity.kt", [(
    """                DogMascot(
                    pose = if (acked) DogPose.SIT_B else DogPose.RUN_HAPPY,
                    size = 120.dp,
                    jump = !acked,
                )""",
    """                if (acked) {
                    // 点完「知道了」→ 切回静态的「坐好看你」（设计稿的"被回应"感）
                    DogMascot(pose = DogPose.SIT_B, size = 120.dp)
                } else {
                    // 响铃中 → 播用户自制的 9 帧奔跑动画
                    DogRunLoop(height = 110.dp)
                }""",
    "响铃页换动画",
)])

p = os.path.join(AK, "alarm/RingActivity.kt")
s = open(p, encoding="utf-8").read()
if "import com.ruyue.alarm.ui.design.DogRunLoop" not in s:
    s = s.replace("import com.ruyue.alarm.ui.design.DogMascot",
                  "import com.ruyue.alarm.ui.design.DogMascot\nimport com.ruyue.alarm.ui.design.DogRunLoop")
open(p, "w", encoding="utf-8", newline="\n").write(s)
print("  ✓ 补 DogRunLoop import")
print("完成")
