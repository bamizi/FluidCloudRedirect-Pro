package com.mo.fkLTY.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 语义令牌（单一来源）。
 *
 * 命名用中性的 `App*`：这套是"现代原生"取向，将来再换视觉语言时，
 * 令牌名不用跟着改，只换这里的值。
 */
@Immutable
class AppColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceTonal: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val outline: Color,
    val success: Color,
    val danger: Color,
    val chipTint: Color,
    val heroStart: Color,
    val heroMid: Color,
    val heroEnd: Color,
)

internal val AppLight = AppColors(
    isDark = false,
    bg = Palette.bgLight,
    surface = Palette.surfaceLight,
    surfaceTonal = Palette.surfaceTonalLight,
    primary = Palette.primaryLight,
    onPrimary = Palette.onPrimaryLight,
    primaryContainer = Palette.primaryContainerLight,
    onPrimaryContainer = Palette.onPrimaryContainerLight,
    text = Palette.textLight,
    textSecondary = Palette.textSecondaryLight,
    textTertiary = Palette.textTertiaryLight,
    outline = Palette.outlineLight,
    success = Palette.successLight,
    danger = Palette.dangerLight,
    chipTint = Palette.chipTintLight,
    heroStart = Palette.heroLightA,
    heroMid = Palette.heroLightB,
    heroEnd = Palette.heroLightC,
)

internal val AppDark = AppColors(
    isDark = true,
    bg = Palette.bgDark,
    surface = Palette.surfaceDark,
    surfaceTonal = Palette.surfaceTonalDark,
    primary = Palette.primaryDark,
    onPrimary = Palette.onPrimaryDark,
    primaryContainer = Palette.primaryContainerDark,
    onPrimaryContainer = Palette.onPrimaryContainerDark,
    text = Palette.textDark,
    textSecondary = Palette.textSecondaryDark,
    textTertiary = Palette.textTertiaryDark,
    outline = Palette.outlineDark,
    success = Palette.successDark,
    danger = Palette.dangerDark,
    chipTint = Palette.chipTintDark,
    heroStart = Palette.heroDarkA,
    heroMid = Palette.heroDarkB,
    heroEnd = Palette.heroDarkC,
)

val LocalAppColors = staticCompositionLocalOf { AppLight }

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}

/** 8pt 栅格（取 4 的倍数），页面横向 20 */
object AppSpace {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp

    /** 页面左右边距 */
    val screenInset: Dp = 20.dp

    /** 最小可点区域 */
    val minTouchTarget: Dp = 44.dp
}

/** 圆角：卡片 24、强调卡 28、图标块 13、胶囊 999 */
object AppRadius {
    val card: Dp = 24.dp
    val hero: Dp = 28.dp
    val chip: Dp = 13.dp
    val pill: Dp = 999.dp
}

/**
 * 排版：系统无衬线，按 iOS/Android 通行的"标题粗、正文常规、辅助小一号"组织。
 * ≥20sp 用 SemiBold，<20sp 用 Medium，避免小字过粗影响识别。
 */
object AppType {
    val pageTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = -0.4.sp,
    )
    val pageSubtitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )
    val heroEyebrow = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    )
    val heroValue = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = -0.2.sp,
    )
    val heroMeta = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    val rowTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = -0.1.sp,
    )
    val rowSub = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
    )
    val sectionTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp,
    )
    val segment = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    )
    val button = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    )
    val chip = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
    )
    val mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

    /** 卡片预览里的标题：贴近真机卡片的字重与字号 */
    val cardTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    )
}

/** 动效时长阶梯（毫秒） */
object AppMotion {
    const val instant = 100
    const val quick = 200
    const val regular = 300
}

/**
 * 无障碍：系统把动画时长缩放设为 0 时返回 true，所有动效退化为瞬时。
 * 这是 Android 上「减弱动态效果」的真实落点。
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        scale == 0f
    }
}

fun motionDuration(baseMillis: Int, reduceMotion: Boolean): Int =
    if (reduceMotion) 0 else baseMillis
