# -*- coding: utf-8 -*-
"""chase_ball：拆帧 → 「稍后 5/10 分钟」按下时播追球动画，跑开后关页（位置 A）。"""
import glob
import os
import shutil

from PIL import Image

# ---------- 1. 找 GIF 并拆帧 ----------
DESK = os.path.expanduser(r"~\Desktop")
cands = sorted(
    (p for p in glob.glob(os.path.join(DESK, "*.gif")) if "追逐" in p or "chase" in p.lower()),
    key=os.path.getmtime, reverse=True,
)
SRC = cands[0] if cands else sorted(glob.glob(os.path.join(DESK, "*.gif")), key=os.path.getmtime)[-1]
print("源文件:", os.path.basename(SRC))

WORK = r"D:\My Projects\_gif-src"
os.makedirs(WORK, exist_ok=True)
local = os.path.join(WORK, "chase.gif")
shutil.copy2(SRC, local)

DST = r"D:\My Projects\apps\RuyueAlarm\app\src\main\res\drawable-nodpi"
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
print(f"已写 {n} 帧，合计 {total:.1f} KB")

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


run_list = ",\n    ".join(f"R.drawable.dog_run_{i:02d}" for i in range(9))
chase_list = ",\n    ".join(f"R.drawable.dog_chase_{i:02d}" for i in range(n))

# ---------- 2. DesignComponents：泛化成 DogFrameLoop ----------
patch("ui/design/DesignComponents.kt", [(
    """@Composable
fun DogRunLoop(height: Dp = 110.dp, modifier: Modifier = Modifier) {
    val frames = remember {
        listOf(%s)
    }
    val transition = rememberInfiniteTransition(label = "dogRun")""" % run_list,
    """/** 奔跑帧（响铃中）—— 用户 2026-11-09 自制 9 帧 */
val RUN_FRAMES = intArrayOf(
    %s
)

/** 追球帧（按「稍后」之后）—— 同一套造型的第二个动作 */
val CHASE_FRAMES = intArrayOf(
    %s
)

/**
 * 帧动画播放器（奔跑 / 追球共用）。
 *
 * ⚠️ 为什么是**同一个** composable 换参数，而不是 if 分支里放两个不同 composable：
 * 分支切换会改变组合槽表结构，v0.19.3 的启动闪退就是这么来的（见 docs/版本历史.md）。
 * 这里 frames / frameMillis 都只是参数，槽表结构恒定。
 */
@Composable
fun DogFrameLoop(
    frames: IntArray,
    frameMillis: Int = %d,
    height: Dp = 110.dp,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "dogLoop")""" % (run_list, chase_list, (durs[0] if durs and durs[0] > 0 else 140)),
    "DogRunLoop → DogFrameLoop + 两个帧数组",
), (
    """            animation = tween(durationMillis = 1260, easing = LinearEasing),""",
    """            animation = tween(durationMillis = frameMillis * frames.size, easing = LinearEasing),""",
    "帧时长按帧数算",
)])

# ---------- 3. RingActivity：snoozing 状态 + 传参 + 动画分流 ----------
patch("alarm/RingActivity.kt", [
    ("""                var acked by remember { mutableStateOf(false) }""",
     """                var acked by remember { mutableStateOf(false) }
                // 按下「稍后」后 → 小狗追着球跑开（位置 A）
                var snoozing by remember { mutableStateOf(false) }""",
     "加 snoozing 状态"),
    ("""                    acked = acked,
                    onDismiss = {""",
     """                    acked = acked,
                    snoozing = snoozing,
                    onDismiss = {""",
     "传 snoozing"),
    ("""                        ReminderNotifier.cancel(this, reminderId)
                        stopEverything()
                        finish()
                    },
                    onSuppressToday = {""",
     """                        ReminderNotifier.cancel(this, reminderId)
                        stopEverything()
                        // 不立刻关页：让「追球跑开」的动画播完一轮（约 1.3 秒）
                        snoozing = true
                        lifecycleScope.launch {
                            delay(1300)
                            finish()
                        }
                    },
                    onSuppressToday = {""",
     "稍后 → 先播动画再关页"),
    ("""    acked: Boolean,""",
     """    acked: Boolean,
    /** 按下「稍后」后为 true → 小狗改播「追球跑开」 */
    snoozing: Boolean,""",
     "RingScreen 签名加 snoozing"),
    ("""                    // 响铃中 → 播用户自制的 9 帧奔跑动画
                    DogRunLoop(height = 110.dp)""",
     """                    // 同一个 composable，只换帧数组：响铃=奔跑，按了稍后=追球跑开
                    DogFrameLoop(
                        frames = if (snoozing) CHASE_FRAMES else RUN_FRAMES,
                        height = 110.dp,
                    )""",
     "动画分流"),
])

# import 调整
p = os.path.join(AK, "alarm/RingActivity.kt")
s = open(p, encoding="utf-8").read()
s = s.replace(
    "import com.ruyue.alarm.ui.design.DogRunLoop",
    "import com.ruyue.alarm.ui.design.CHASE_FRAMES\n"
    "import com.ruyue.alarm.ui.design.DogFrameLoop\n"
    "import com.ruyue.alarm.ui.design.RUN_FRAMES",
)
if "import kotlinx.coroutines.delay" not in s and "delay(" in s:
    pass
open(p, "w", encoding="utf-8", newline="\n").write(s)
print("  ✓ import 调整")
print("完成")
