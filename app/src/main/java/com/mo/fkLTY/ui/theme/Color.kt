package com.mo.fkLTY.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 原始色值层：只允许本文件出现裸色值。
 *
 * 现代原生（Material 3 Expressive）取向：紫蓝主色、淡紫底、白色大圆角卡片。
 * 明暗两套分别调过 —— 深色下主色要提亮（#A79CF7），否则紫在近黑底上会闷；
 * 浅色下主色要压深（#6C5CE7），否则白字压在浅紫上对比度不够。
 */
internal object Palette {

    // ---- 浅色 ----
    val bgLight = Color(0xFFF6F5FB)
    val surfaceLight = Color(0xFFFFFFFF)
    val surfaceTonalLight = Color(0xFFF1EFFA)
    val primaryLight = Color(0xFF6C5CE7)
    val onPrimaryLight = Color(0xFFFFFFFF)
    val primaryContainerLight = Color(0xFFE7E3FF)
    val onPrimaryContainerLight = Color(0xFF2B2170)
    val textLight = Color(0xFF1B1B21)
    val textSecondaryLight = Color(0xFF5A566E)
    val textTertiaryLight = Color(0xFF8B87A0)
    val outlineLight = Color(0xFFE9E6F3)
    val successLight = Color(0xFF2E8B57)
    val dangerLight = Color(0xFFC62828)

    // ---- 深色 ----
    val bgDark = Color(0xFF131118)
    val surfaceDark = Color(0xFF1C1A24)
    val surfaceTonalDark = Color(0xFF262233)
    val primaryDark = Color(0xFFA79CF7)
    val onPrimaryDark = Color(0xFF241B66)
    val primaryContainerDark = Color(0xFF322A5E)
    val onPrimaryContainerDark = Color(0xFFDDD8FF)
    val textDark = Color(0xFFECEAF4)
    val textSecondaryDark = Color(0xFFA9A4BC)
    val textTertiaryDark = Color(0xFF868199)
    val outlineDark = Color(0xFF2E2A3B)
    val successDark = Color(0xFF6FD08C)
    val dangerDark = Color(0xFFFF8A8A)

    // ---- 强调卡渐变（明暗各一组三色）----
    val heroLightA = Color(0xFF6C5CE7)
    val heroLightB = Color(0xFF8B7BF0)
    val heroLightC = Color(0xFFA79CF7)
    val heroDarkA = Color(0xFF4B3FB0)
    val heroDarkB = Color(0xFF5D4FD0)
    val heroDarkC = Color(0xFF6C5CE7)

    // ---- 图标底色（配同色系前景）----
    val chipTintLight = Color(0xFFEEEBFF)
    val chipTintDark = Color(0xFF2A2540)

}
