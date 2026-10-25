package com.mo.fkLTY.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * 主题：现代原生（Material 3 Expressive）。
 *
 * 明暗两套都做，由界面「外观」段的开关决定；默认浅色，也就是设计稿那一版。
 * 动态取色（Material You）关闭：它会把主色换成壁纸色，这套紫蓝语言就没了。
 */
@Composable
fun 流体云重定向Theme(
    darkTheme: Boolean = false,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val app = if (darkTheme) AppDark else AppLight

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = app.primary,
            onPrimary = app.onPrimary,
            primaryContainer = app.primaryContainer,
            onPrimaryContainer = app.onPrimaryContainer,
            background = app.bg,
            onBackground = app.text,
            surface = app.surface,
            onSurface = app.text,
            surfaceVariant = app.surfaceTonal,
            onSurfaceVariant = app.textSecondary,
            outline = app.outline,
            outlineVariant = app.outline,
            error = app.danger,
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = app.primary,
            onPrimary = app.onPrimary,
            primaryContainer = app.primaryContainer,
            onPrimaryContainer = app.onPrimaryContainer,
            background = app.bg,
            onBackground = app.text,
            surface = app.surface,
            onSurface = app.text,
            surfaceVariant = app.surfaceTonal,
            onSurfaceVariant = app.textSecondary,
            outline = app.outline,
            outlineVariant = app.outline,
            error = app.danger,
            onError = Color.White,
        )
    }

    CompositionLocalProvider(LocalAppColors provides app) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            content = content,
        )
    }
}

/** 外观模式：跟随系统 / 强制深色 / 强制浅色 */
enum class ThemeMode(val id: String) {
    Auto("auto"),
    Dark("dark"),
    Light("light");

    companion object {
        fun fromId(id: String?): ThemeMode =
            entries.firstOrNull { it.id == id } ?: Light
    }
}

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.Auto -> isSystemInDarkTheme()
    ThemeMode.Dark -> true
    ThemeMode.Light -> false
}
