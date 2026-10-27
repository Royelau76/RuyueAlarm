package com.ruyue.alarm.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
// 品牌色
//
// 主色沿用响铃页那套"淡蓝"：深色下直接用 #8AB4F8（和响铃页同一支），
// 浅色下压深成 #2F62B5 —— 同一个色相，但在白底上要有足够对比度。
// 深色模式的背景故意对齐响铃页的 #101418，让"响铃页"和"App 其他页面"看起来是一套东西。
// ============================================================================

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F62B5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E3FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF55606F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E3F5),
    onSecondaryContainer = Color(0xFF121C28),
    tertiary = Color(0xFF1E7A63),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA8F2DA),
    onTertiaryContainer = Color(0xFF00201A),
    background = Color(0xFFF8FAFD),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8FAFD),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E3EA),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceDim = Color(0xFFD8DAE0),
    surfaceBright = Color(0xFFF8FAFD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F4F9),
    surfaceContainer = Color(0xFFEDEFF5),
    surfaceContainerHigh = Color(0xFFE7EAF1),
    surfaceContainerHighest = Color(0xFFE1E4EB),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C7CF),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF2E3135),
    inverseOnSurface = Color(0xFFF1F0F4),
    inversePrimary = Color(0xFF8AB4F8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF002F63),
    primaryContainer = Color(0xFF204070),
    onPrimaryContainer = Color(0xFFD9E3FF),
    secondary = Color(0xFFBCC7DC),
    onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF3C4858),
    onSecondaryContainer = Color(0xFFD9E3F5),
    tertiary = Color(0xFF8CD6BE),
    onTertiary = Color(0xFF00382A),
    tertiaryContainer = Color(0xFF00513E),
    onTertiaryContainer = Color(0xFFA8F2DA),
    // 背景对齐响铃页，形成统一的深色基底
    background = Color(0xFF101418),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF11161B),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CF),
    surfaceDim = Color(0xFF0B0E11),
    surfaceBright = Color(0xFF262C33),
    surfaceContainerLowest = Color(0xFF0B0E11),
    surfaceContainerLow = Color(0xFF161B21),
    surfaceContainer = Color(0xFF1A2026),
    surfaceContainerHigh = Color(0xFF232A31),
    surfaceContainerHighest = Color(0xFF2C333B),
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474E),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    inverseSurface = Color(0xFFE2E2E6),
    inverseOnSurface = Color(0xFF2E3135),
    inversePrimary = Color(0xFF2F62B5),
)

// ============================================================================
// 语义色
//
// 之前的红绿是硬编码在 UI 里的（#2E7D32 / #C62828）：在深色背景上对比度极低，
// 几乎看不清"好/坏"。语义色按明暗两套给，组件只取语义、不关心具体色值。
// ============================================================================

@Immutable
data class RuyueSemanticColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val neutral: Color,
)

private val LightSemantic = RuyueSemanticColors(
    success = Color(0xFF1B6B32),
    warning = Color(0xFF8C5A00),
    danger = Color(0xFFB3261E),
    info = Color(0xFF1A5CAD),
    neutral = Color(0xFF5F6368),
)

private val DarkSemantic = RuyueSemanticColors(
    success = Color(0xFF7FD89B),
    warning = Color(0xFFFFCF7A),
    danger = Color(0xFFFFB4AB),
    info = Color(0xFF9CC6FF),
    neutral = Color(0xFFAEB3BA),
)

val LocalRuyueSemanticColors = staticCompositionLocalOf { LightSemantic }

// ============================================================================
// 排版与形状
//
// 原来的字号是各页面自己写死 12~24sp，没有层级。这里定义一套：
//   titleLarge  → 页面大标题
//   titleMedium → 卡片/分区标题
//   bodyMedium  → 正文
//   bodySmall   → 辅助说明（Hint）
// ============================================================================

private val RuyueTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

private val RuyueShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
)

/**
 * 全 App 主题：**跟随系统深浅色**。
 *
 * 除了 Compose 配色，这里还要处理系统栏图标 —— 原来 `themes.xml` 写死了
 * `Theme.Material.Light`，系统切到深色后状态栏仍是"深色图标"，压在深色界面上等于看不见。
 * 现在：`values/themes.xml` + `values-night/themes.xml` 分两套（保证首帧背景与图标正确），
 * 这里再用 WindowInsetsController 显式设一次，防止运行中切换深浅色时图标不刷新。
 */
@Composable
fun RuyueTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalRuyueSemanticColors provides if (darkTheme) DarkSemantic else LightSemantic,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = RuyueTypography,
            shapes = RuyueShapes,
            content = content,
        )
    }
}
