package com.mo.fkLTY.ui.theme

import androidx.compose.material3.Typography

/**
 * Material Typography 映射到本项目的字号体系。
 *
 * 自绘组件直接引用 `AppType`；这里映射是为了让偶尔用到的 Material 文本
 * （对话框、进度指示等）不跳回 Material 默认值，保持整屏只有一套节奏。
 */
val Typography = Typography(
    displaySmall = AppType.pageTitle,
    headlineSmall = AppType.heroValue,
    titleLarge = AppType.pageTitle,
    titleMedium = AppType.rowTitle,
    titleSmall = AppType.sectionTitle,
    bodyLarge = AppType.rowTitle,
    bodyMedium = AppType.rowSub,
    bodySmall = AppType.rowSub,
    labelLarge = AppType.button,
    labelMedium = AppType.chip,
    labelSmall = AppType.mono,
)
