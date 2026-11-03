package com.ruyue.alarm.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ============================================================================
// 「条件就绪」设计语言（2026-10-31 设计交付，见 docs/ui/UI-迁移计划.md）
//
// 三条必须守住的东西：
//   ① 颜色 = 触发源。薄荷=时间、薰衣草=WiFi、蜜桃=地点。**贯穿所有页面，不是装饰。**
//   ② 全暗色。设计的整句话汇靠"夜底 + 发光路径"成立，亮色下不成立 —— 所以本 App
//      **不再跟随系统深浅色**，永远暗色（用户 2026-11-03 决定）。
//   ③ 字号按 Android 体系，不照抄原型的 px：原型正文 11.5px(412 画布) → 真机 14~16sp。
// ============================================================================

@Immutable
data class RuyueColors(
    /** 背景渐变的三段（linear-gradient(168deg) 的近似） */
    val bgTop: Color = Color(0xFF1B2540),
    val bgMid: Color = Color(0xFF18213A),
    val bgBottom: Color = Color(0xFF212C4C),
    /** 比底色亮两档的分层，不用描边 */
    val card: Color = Color(0xFF26335A),
    val cardHigh: Color = Color(0xFF2E3C68),
    val sheet: Color = Color(0xFF212C4A),
    /** 路径未达成段 */
    val track: Color = Color(0xFF3A4874),
    /** 三级文字 */
    val text: Color = Color(0xFFEDF0FF),
    val textSecondary: Color = Color(0xFFB9C2E0),
    val textTertiary: Color = Color(0xFFA6B1D6),
    /** 三种触发源 —— 这就是信息本身 */
    val time: Color = Color(0xFF9BE0CC),
    val wifi: Color = Color(0xFFBAC3FA),
    val place: Color = Color(0xFFF4D0A8),
    /** 亮色底上的深字 */
    val onAccent: Color = Color(0xFF17273F),
    /** 危险 / 提醒（删除、错误） */
    val danger: Color = Color(0xFFFFB4AB),
)

val LocalRuyueColors = staticCompositionLocalOf { RuyueColors() }

/** 语义色（老页面还在用：Common.kt 的 Hint / LogsScreen） */
@Immutable
data class RuyueSemanticColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val neutral: Color,
)

private val RuyueSemantic = RuyueSemanticColors(
    success = Color(0xFF7FD89B),
    warning = Color(0xFFFFCF7A),
    danger = Color(0xFFFFB4AB),
    info = Color(0xFFBAC3FA),
    neutral = Color(0xFFA6B1D6),
)

val LocalRuyueSemanticColors = staticCompositionLocalOf { RuyueSemantic }

/** Material3 色板映射：只为了 Button/Switch/TextField 这些默认组件能自动拿到对的颜色 */
private val DarkColors = darkColorScheme(
    primary = RuyueColors().time,
    onPrimary = RuyueColors().onAccent,
    primaryContainer = RuyueColors().cardHigh,
    onPrimaryContainer = RuyueColors().time,
    secondary = RuyueColors().wifi,
    onSecondary = RuyueColors().onAccent,
    secondaryContainer = RuyueColors().cardHigh,
    onSecondaryContainer = RuyueColors().wifi,
    tertiary = RuyueColors().place,
    onTertiary = RuyueColors().onAccent,
    tertiaryContainer = RuyueColors().cardHigh,
    onTertiaryContainer = RuyueColors().place,
    background = RuyueColors().bgMid,
    onBackground = RuyueColors().text,
    surface = RuyueColors().card,
    onSurface = RuyueColors().text,
    surfaceVariant = RuyueColors().cardHigh,
    onSurfaceVariant = RuyueColors().textSecondary,
    surfaceDim = RuyueColors().bgMid,
    surfaceBright = RuyueColors().cardHigh,
    surfaceContainerLowest = RuyueColors().bgMid,
    surfaceContainerLow = RuyueColors().bgMid,
    surfaceContainer = RuyueColors().card,
    surfaceContainerHigh = RuyueColors().cardHigh,
    surfaceContainerHighest = RuyueColors().cardHigh,
    outline = RuyueColors().track,
    outlineVariant = RuyueColors().track,
    error = RuyueColors().danger,
    onError = RuyueColors().onAccent,
    errorContainer = Color(0xFF5B2B2B),
    onErrorContainer = RuyueColors().danger,
    inverseSurface = RuyueColors().time,
    inverseOnSurface = RuyueColors().onAccent,
    inversePrimary = RuyueColors().onAccent,
    scrim = Color(0xCC0B1120),
)

/**
 * 排版：字号按 Android 体系重设（原型 px 已 +2~3sp 换算）。
 *
 * | 用途 | 设计稿 | 这里 |
 * |---|---|---|
 * | 页面大标题 | 22~26 | titleLarge 24sp |
 * | 卡内标题 | 14~15 | titleMedium 16sp |
 * | 正文 | 12~13 (原型) → 真机 14~16 | bodyLarge 16 / bodyMedium 15 |
 * | 辅助说明 | 11~11.5 → 12~13 | bodySmall 13 / labelSmall 12 |
 */
private val RuyueTypography = Typography(
    displayLarge = TextStyle(fontSize = 74.sp, lineHeight = 78.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

/** 卡片圆角 20~24，按钮 18（设计稿给定） */
private val RuyueShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/**
 * 全 App 主题：**永远暗色**（不再跟随系统）。
 *
 * 系统栏图标仍要显式设一次：`values/themes.xml` 已改成深色平台主题（保证首帧背景正确），
 * 这里再用 WindowInsetsController 兜一遍，防止某些机型首帧仍是深色图标压深色界面。
 */
@Composable
fun RuyueTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(
        LocalRuyueColors provides RuyueColors(),
        LocalRuyueSemanticColors provides RuyueSemantic,
    ) {
        MaterialTheme(
            colorScheme = DarkColors,
            typography = RuyueTypography,
            shapes = RuyueShapes,
            content = content,
        )
    }
}
