package com.ruyue.alarm.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ruyue.alarm.R
import com.ruyue.alarm.ui.theme.LocalRuyueColors
import com.ruyue.alarm.ui.theme.RuyueColors

// ============================================================================
// 「条件就绪」的共用组件。见 docs/ui/UI-迁移计划.md
//
// 这一版的核心信息载体是**路径组件**：段数 = 启用条件数，达成的段用触发源色，
// 未达成留暗轨，白点 = 此刻。列表行里退化成节点序列（小圆点 + 短线）。
// ============================================================================

enum class ConditionKind { TIME, WIFI, PLACE }

/** 路径上的一步：某个触发条件，此刻达成与否 */
data class ConditionStep(
    val kind: ConditionKind,
    val label: String,
    val met: Boolean,
)

/** 纯函数：Canvas（DrawScope）里不能调 @Composable，所以颜色要提前算好传进去 */
fun kindColor(kind: ConditionKind, c: RuyueColors): Color = when (kind) {
    ConditionKind.TIME -> c.time
    ConditionKind.WIFI -> c.wifi
    ConditionKind.PLACE -> c.place
}

/** 全 App 背景：夜底渐变（近似设计的 linear-gradient(168deg)） */
@Composable
fun RuyueBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = LocalRuyueColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(c.bgTop, c.bgMid, c.bgBottom),
                        start = Offset(0f, 0f),
                        // 168deg 的近似：从左上到右下略偏
                        end = Offset(size.width * 0.42f, size.height),
                    )
                )
            },
        content = content,
    )
}

// ---------------- 小狗 ----------------

enum class DogPose(val res: Int) {
    SLEEP(R.drawable.dog_sleep),
    LIE_DOWN(R.drawable.dog_lie_down),
    SIT_LAUGH(R.drawable.dog_sit_laugh),
    BEG_STAND(R.drawable.dog_beg_stand),
    RUN_HAPPY(R.drawable.dog_run_happy),
    SIT_B(R.drawable.dog_sit_b),
}

/**
 * 姿态不是随机配图，由「还差几个条件」决定 —— 活跃度对应急迫度，不用读字也知道急不急。
 */
fun poseFor(pending: Int, paused: Boolean): DogPose = when {
    paused -> DogPose.SLEEP
    pending >= 2 -> DogPose.LIE_DOWN
    pending == 1 -> DogPose.SIT_LAUGH
    else -> DogPose.BEG_STAND
}

/**
 * 小狗：静态 PNG + **代码做的呼吸**。
 *
 * 为什么不用设计交付里的 8.7MB WebP 动图：体积不划算，且安卓对动图 WebP 要兼容判断。
 * 按 README 给的做法：`scaleY 1→1.018→1`、`scaleX 1→0.994→1`（反向），
 * pivotY 在脚底（画布 476/512 ≈ 93%），周期按姿态取 2.6~3.4s。
 *
 * 尺寸上限：素材真实清晰度只有约 220px 物理像素 → 最大别超过 140dp。
 */
@Composable
fun DogMascot(
    pose: DogPose,
    size: Dp,
    modifier: Modifier = Modifier,
    /** 跳动：响铃页专用（设计：1.1s × 2 次，跳两下停一下） */
    jump: Boolean = false,
) {
    val periodMs = when (pose) {
        DogPose.SLEEP -> 3400
        DogPose.LIE_DOWN -> 3200
        DogPose.SIT_B -> 3000
        else -> 2700
    }
    val transition = rememberInfiniteTransition(label = "dog")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )
    // 跳两下的竖直位移：0 → 1 → 0 → 0 → 1 → 0（2200ms 一轮）
    val hop by if (jump) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 2200
                    0f at 0
                    1f at 380
                    0f at 760
                    0f at 1240
                    1f at 1620
                    0f at 2000
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "hop",
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    Image(
        painter = painterResource(pose.res),
        contentDescription = null,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleY = 1f + 0.018f * t
                scaleX = 1f - 0.006f * t
                translationY = -10.dp.toPx() * hop
                transformOrigin = TransformOrigin(0.5f, 0.93f)
            },
    )
}

// ---------------- 状态胶囊 ----------------

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
        )
    }
}

// ---------------- 路径组件（主卡用）----------------

/**
 * 路径：段数 = 启用条件数；达成的段用触发源色，未达成留暗轨 `#3A4874`；
 * 白点 = 此刻，停在最后一个达成点上。下方是各节点的标签。
 */
@Composable
fun PathRail(
    steps: List<ConditionStep>,
    modifier: Modifier = Modifier,
    curveHeight: Dp = 76.dp,
) {
    val c = LocalRuyueColors.current
    if (steps.isEmpty()) return
    // 先算好每段的颜色（Canvas 里不能调 @Composable）
    val stepColors = steps.map { kindColor(it.kind, c) }

    Column(modifier) {
        // 只有一个条件时，"段"没有第二个端点可画 —— 退化成一条直轨 + 达成部分填色。
        // （v0.18.0 第一版这里啥都没画，只剩一个白点，看起来像坏了）
        if (steps.size == 1) {
            SingleConditionRail(step = steps.first(), color = stepColors.first())
            return@Column
        }
        androidx.compose.foundation.Canvas(
            Modifier
                .fillMaxWidth()
                .height(curveHeight),
        ) {
            val n = steps.size
            val midY = size.height * 0.56f
            val amp = size.height * 0.34f
            val left = size.width * 0.04f
            val right = size.width * 0.96f
            fun xAt(i: Int): Float =
                if (n == 1) (left + right) / 2f else left + (right - left) * i / (n - 1).toFloat()

            val stroke = 5.dp.toPx()

            // 逐段画：达成段上色，未达成段留暗轨。相邻段交替向上/向下弯，形成设计的波浪
            for (i in 0 until n - 1) {
                val x0 = xAt(i)
                val x1 = xAt(i + 1)
                val dx = x1 - x0
                val up = i % 2 == 0
                val dy = if (up) -amp else amp
                val path = Path().apply {
                    moveTo(x0, midY)
                    cubicTo(x0 + dx * 0.42f, midY + dy, x1 - dx * 0.42f, midY + dy, x1, midY)
                }
                drawPath(
                    path = path,
                    color = if (steps[i].met) stepColors[i] else c.track,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            // 节点：达成=实心色点，未达成=暗轨小点；末点画成空心圈（"终点"感）
            for (i in 0 until n) {
                val x = xAt(i)
                val met = steps[i].met
                if (i == n - 1 && !met) {
                    drawCircle(c.track, radius = 5.dp.toPx(), center = Offset(x, midY), style = Stroke(width = 2.dp.toPx()))
                } else {
                    drawCircle(
                        color = if (met) stepColors[i] else c.track,
                        radius = (if (met) 4.5f else 3f).dp.toPx(),
                        center = Offset(x, midY),
                    )
                }
            }

            // 「此刻」白点：停在最后一个达成点上
            val metCount = steps.count { it.met }
            val nowIndex = metCount.coerceIn(0, n - 1)
            val nowX = xAt(nowIndex)
            drawCircle(c.text.copy(alpha = 0.18f), radius = 13.dp.toPx(), center = Offset(nowX, midY))
            drawCircle(c.text, radius = 7.dp.toPx(), center = Offset(nowX, midY))
        }

        if (steps.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                steps.forEachIndexed { index, step ->
                    Text(
                        text = step.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (step.met) stepColors[index] else c.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = when (index) {
                            0 -> TextAlign.Start
                            steps.lastIndex -> TextAlign.End
                            else -> TextAlign.Center
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Text(
                text = steps.first().label,
                style = MaterialTheme.typography.labelMedium,
                color = if (steps.first().met) stepColors[0] else c.textTertiary,
            )
        }
    }
}

/**
 * 单条件时的路径：一条直轨，达成就整条上色，白点（此刻）停在达成的那一头。
 * 段数依然等于条件数（1 段），只是没有第二个节点可以弯曲。
 */
@Composable
private fun SingleConditionRail(step: ConditionStep, color: Color) {
    val c = LocalRuyueColors.current
    androidx.compose.foundation.Canvas(
        Modifier
            .fillMaxWidth()
            .height(44.dp),
    ) {
        val y = size.height * 0.5f
        val bar = 6.dp.toPx()
        drawLine(c.track, Offset(0f, y), Offset(size.width, y), bar, StrokeCap.Round)
        if (step.met) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), bar, StrokeCap.Round)
        }
        val inset = 10.dp.toPx()
        val dotX = if (step.met) size.width - inset else inset
        drawCircle(c.text.copy(alpha = 0.18f), radius = 13.dp.toPx(), center = Offset(dotX, y))
        drawCircle(c.text, radius = 7.dp.toPx(), center = Offset(dotX, y))
    }
    Text(
        text = step.label,
        style = MaterialTheme.typography.labelMedium,
        color = if (step.met) color else c.textTertiary,
    )
}

/**
 * 行内退化版：节点序列（小圆点 + 短线）—— 一行就能看出几个条件、几个已满足。
 * 宽度 < 200dp 时只留点和线（调用方按需传 compact）。
 */
@Composable
fun NodeRail(
    steps: List<ConditionStep>,
    modifier: Modifier = Modifier,
) {
    val c = LocalRuyueColors.current
    val stepColors = steps.map { kindColor(it.kind, c) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { index, step ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(14.dp)
                        .height(2.dp)
                        .background(
                            if (step.met) stepColors[index].copy(alpha = 0.7f) else c.track,
                            RoundedCornerShape(1.dp),
                        ),
                )
            }
            Box(
                Modifier
                    .size(if (step.met) 9.dp else 7.dp)
                    .background(
                        color = if (step.met) stepColors[index] else Color.Transparent,
                        shape = RoundedCornerShape(50),
                    )
                    .then(
                        if (step.met) Modifier else Modifier.drawBehind {
                            drawCircle(c.track, radius = size.minDimension / 2f, style = Stroke(1.5.dp.toPx()))
                        }
                    ),
            )
        }
    }
}
