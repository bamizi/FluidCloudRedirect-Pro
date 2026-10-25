package com.mo.fkLTY.ui.picker

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.mo.fkLTY.ui.theme.AppMotion
import com.mo.fkLTY.ui.theme.AppRadius
import com.mo.fkLTY.ui.theme.AppSpace
import com.mo.fkLTY.ui.theme.AppTheme
import com.mo.fkLTY.ui.theme.AppType
import com.mo.fkLTY.ui.theme.motionDuration
import com.mo.fkLTY.ui.theme.rememberReduceMotion

/* ───────────────────────── 卡片 ───────────────────────── */

/**
 * 卡片：白 / 深灰底 + 24 大圆角 + 发丝描边。
 *
 * 不用投影：Material 3 的层级靠"色调面"表达，投影在深色模式下几乎不可见，
 * 加了只会让两种模式看起来不一致。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(AppRadius.card)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.outline, shape)
            .then(if (padded) Modifier.padding(AppSpace.l) else Modifier),
        content = content,
    )
}

/** 卡片内分隔线，左侧留出文字起点 */
@Composable
fun AppDivider(modifier: Modifier = Modifier, startIndent: Dp = AppSpace.l) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(1.dp)
            .background(AppTheme.colors.outline),
    )
}

/** 分组小标题：加粗小字，放在卡片上方 */
@Composable
fun AppSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppSpace.xs, top = AppSpace.xl, bottom = AppSpace.s)
            .semantics { heading() },
        style = AppType.sectionTitle,
        color = AppTheme.colors.textSecondary,
    )
}

/* ───────────────────────── 开关 ───────────────────────── */

/**
 * 开关：48×28 轨道 + 22 圆点，开启用主色填充。
 * 外层撑到 44 高保证可点区域；`onCheckedChange` 为 null 时只作状态显示。
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = AppTheme.colors
    val reduceMotion = rememberReduceMotion()

    val knob by animateDpAsState(
        targetValue = if (checked) 23.dp else 3.dp,
        animationSpec = tween(durationMillis = motionDuration(AppMotion.quick, reduceMotion)),
        label = "appKnob",
    )
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.45f,
        animationSpec = tween(durationMillis = motionDuration(AppMotion.instant, reduceMotion)),
        label = "appSwitchAlpha",
    )

    val toggleModifier = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(56.dp, AppSpace.minTouchTarget)
            .then(toggleModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp, 28.dp)
                .graphicsLayer(alpha = alpha)
                .clip(RoundedCornerShape(AppRadius.pill))
                .background(if (checked) c.primary else c.surfaceTonal)
                .border(1.dp, if (checked) c.primary else c.outline, RoundedCornerShape(AppRadius.pill)),
        ) {
            Box(
                modifier = Modifier
                    .padding(start = knob)
                    .align(Alignment.CenterStart)
                    .size(22.dp)
                    .shadow(1.dp, RoundedCornerShape(AppRadius.pill))
                    .clip(RoundedCornerShape(AppRadius.pill))
                    .background(if (checked) Color.White else c.textTertiary),
            )
        }
    }
}

/* ───────────────────────── 图标 ───────────────────────── */

/** 系统图标 Drawable → 位图（应用图标） */
@Composable
fun AppIcon(drawable: Drawable?, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    val px = size.value.toInt().coerceAtLeast(1)
    val bitmap = remember(drawable, px) {
        runCatching { drawable?.toBitmap(px, px)?.asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(9.dp)),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(9.dp))
                .background(AppTheme.colors.surfaceTonal),
        )
    }
}

/** 矢量图标底色块：40×40 / 圆角 13，配主色前景 */
@Composable
fun AppGlyph(icon: ImageVector, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(AppRadius.chip))
            .background(c.chipTint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = c.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/* ───────────────────────── 行 ───────────────────────── */

/**
 * 通用行。
 *
 * - 开关行：整行 toggleable + Role.Switch，开关本身只作显示
 * - 单选行：selectable + Role.RadioButton + selected
 * - 所有行合并语义，读屏一次念完标题、副标题与状态
 */
@Composable
fun AppRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    role: Role? = null,
    selected: Boolean? = null,
    enabled: Boolean = true,
    disabledLabel: Boolean = false,
    showDivider: Boolean = false,
    dividerIndent: Dp = AppSpace.l,
) {
    val c = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Column {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = AppSpace.minTouchTarget)
                .background(if (pressed && enabled) c.surfaceTonal else Color.Transparent)
                .then(clickModifier)
                .semantics(mergeDescendants = true) {
                    if (selected != null) this.selected = selected
                }
                .padding(horizontal = AppSpace.l, vertical = AppSpace.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(AppSpace.m))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AppType.rowTitle,
                    color = if (disabledLabel) c.textTertiary else c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = AppType.rowSub,
                        color = c.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(AppSpace.s))
                trailing()
            }
        }
        if (showDivider) AppDivider(startIndent = dividerIndent)
    }
}

/** 规则行：图标 + 标题/说明 + 开关，整行可点 */
@Composable
fun AppRuleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
) {
    AppRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics(mergeDescendants = true) {
                stateDescription = if (checked) "已开启" else "已关闭"
            },
        leading = { AppGlyph(icon) },
        trailing = { AppSwitch(checked = checked, onCheckedChange = null) },
        showDivider = showDivider,
        dividerIndent = 68.dp,
    )
}

/** 单选行：选中时尾部主色对勾 */
@Composable
fun AppChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Drawable? = null,
    enabled: Boolean = true,
    showDivider: Boolean = false,
) {
    val c = AppTheme.colors
    AppRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick,
        ),
        leading = if (icon != null) {
            { AppIcon(drawable = icon) }
        } else {
            null
        },
        trailing = if (selected) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            null
        },
        enabled = enabled,
        disabledLabel = !enabled,
        showDivider = showDivider,
    )
}

/* ───────────────────────── 分段控件 ───────────────────────── */

/** 分段控件：浅色底槽 + 选中项白色浮起（Material 3 segmented button） */
@Composable
fun AppSegment(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surfaceTonal)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (active) {
                            Modifier
                                .shadow(2.dp, RoundedCornerShape(14.dp))
                                .background(c.surface)
                        } else {
                            Modifier
                        },
                    )
                    .selectable(
                        selected = active,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    )
                    .semantics(mergeDescendants = true) { this.selected = active },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = AppType.segment,
                    color = if (active) c.primary else c.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/* ───────────────────────── 按钮 / 胶囊 ───────────────────────── */

/** 主按钮：主色实心，高 44，按下缩放 0.98 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color? = null,
    contentColor: Color? = null,
) {
    val c = AppTheme.colors
    val reduceMotion = rememberReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = tween(durationMillis = motionDuration(AppMotion.instant, reduceMotion)),
        label = "appBtnScale",
    )
    val bg = container ?: c.primary
    val fg = contentColor ?: c.onPrimary

    Box(
        modifier = modifier
            .heightIn(min = AppSpace.minTouchTarget)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(AppRadius.pill))
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = AppSpace.xl, vertical = AppSpace.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = AppType.button, color = fg)
    }
}

/* ───────────────────────── 输入框 ───────────────────────── */

/**
 * 通用输入框：标签在上、浅底圆角、主色光标。
 * 留空是合法状态（表示"这一项不改"），所以不做非空校验，也不自动回填默认值。
 */
@Composable
fun AppTextField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = AppType.rowSub, color = c.textSecondary)
        Spacer(Modifier.height(AppSpace.xs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppSpace.minTouchTarget)
                .clip(RoundedCornerShape(AppRadius.chip))
                .background(c.surfaceTonal)
                .border(1.dp, c.outline, RoundedCornerShape(AppRadius.chip))
                .padding(horizontal = AppSpace.m, vertical = AppSpace.m),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = AppType.rowTitle.copy(color = c.text),
                cursorBrush = SolidColor(c.primary),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(text = placeholder, style = AppType.rowTitle, color = c.textTertiary)
                    }
                    inner()
                },
            )
        }
    }
}

/** 小胶囊：用于「＋ 添加替换」「查看日志」这类次级入口 */
@Composable
fun AppChip(    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = AppSpace.minTouchTarget)
            .clip(RoundedCornerShape(AppRadius.pill))
            .background(c.surfaceTonal)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = AppSpace.l, vertical = AppSpace.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = AppType.chip, color = c.primary)
    }
}
