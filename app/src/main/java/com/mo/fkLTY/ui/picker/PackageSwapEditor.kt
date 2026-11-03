package com.mo.fkLTY.ui.picker

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mo.fkLTY.engine.CardOverrides
import com.mo.fkLTY.engine.CardText
import com.mo.fkLTY.engine.PackageSwap
import com.mo.fkLTY.engine.RedirectPrefs
import com.mo.fkLTY.ui.theme.AppMotion
import com.mo.fkLTY.ui.theme.AppRadius
import com.mo.fkLTY.ui.theme.AppSpace
import com.mo.fkLTY.ui.theme.AppTheme
import com.mo.fkLTY.ui.theme.AppType
import com.mo.fkLTY.ui.theme.motionDuration
import com.mo.fkLTY.ui.theme.rememberReduceMotion

/**
 * 应用替换编辑器（现代原生）。
 *
 * 解决的问题没变：复制口令这类卡片里没有网址，metis 已经把目标包名写死，
 * 只能按"包名 → 包名"替换。典型用法：com.quark.browser → com.quark.clouddrive。
 *
 * 交互：每个替换是一行（右侧开关启停）；点文字区弹确认框再删除，
 * 破坏性操作不挂在单击上；新增是卡片末行的「＋ 添加应用替换」。
 */
@Composable
fun PackageSwapEditor(
    swaps: List<PackageSwap>,
    apps: List<PickerViewModel.BrowserEntry>,
    /** 每条规则自己的文案；没有条目 = 用全局默认 */
    overrides: Map<String, CardText>,
    /** 全局默认文案，对话框里作为"留空时用什么"的提示 */
    globalText: CardText,
    onSaveOverride: (String, CardText) -> Unit,
    onSave: (List<PackageSwap>) -> Unit,
) {
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PackageSwap?>(null) }
    var pendingText by remember { mutableStateOf<PackageSwap?>(null) }
    val reduceMotion = rememberReduceMotion()

    // 增删替换时卡片高度平滑收放，而不是"跳"一下
    AppCard(
        modifier = Modifier.animateContentSize(
            tween(motionDuration(AppMotion.regular, reduceMotion)),
        ),
        padded = false,
    ) {
        if (swaps.isEmpty()) {
            Text(
                text = "还没有替换规则。点下面的按钮添加一条。",
                style = AppType.rowSub,
                color = AppTheme.colors.textTertiary,
                modifier = Modifier.padding(horizontal = AppSpace.l, vertical = AppSpace.l),
            )
            AppDivider()
        }

        swaps.forEach { swap ->
            SwapRow(
                title = "${labelOf(apps, swap.from)} → ${labelOf(apps, swap.to)}",
                subtitle = "${swap.from} → ${swap.to}",
                checked = swap.enabled,
                customized = overrides.containsKey(swap.from),
                onCheckedChange = { enabled ->
                    onSave(swaps.map { if (it.from == swap.from) it.copy(enabled = enabled) else it })
                },
                onEditText = { pendingText = swap },
                onDelete = { pendingDelete = swap },
                // 每条后面都有分隔线：最后一条下面还接着「＋ 添加」，不画线会连成一片
                showDivider = true,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppSpace.minTouchTarget)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = { showEditor = true },
                )
                .padding(horizontal = AppSpace.l, vertical = AppSpace.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "＋  添加应用替换",
                style = AppType.rowTitle,
                color = AppTheme.colors.primary,
            )
        }
    }

    if (showEditor) {
        SwapEditDialog(
            apps = apps,
            onDismiss = { showEditor = false },
            onConfirm = { swap ->
                onSave(swaps.filterNot { it.from == swap.from } + swap)
                showEditor = false
            },
        )
    }

    pendingDelete?.let { target ->
        AppAlertDialog(
            title = "删除这条替换？",
            message = "${labelOf(apps, target.from)} → ${labelOf(apps, target.to)}",
            confirmText = "删除",
            onDismiss = { pendingDelete = null },
            onConfirm = {
                onSave(swaps.filterNot { it.from == target.from })
                pendingDelete = null
            },
        )
    }

    pendingText?.let { target ->
        CardOverrideDialog(
            from = target.from,
            to = target.to,
            // 没单独设过就从"什么都没填"开始——预填全局值会让用户一点保存就把默认固化成本条
            initial = overrides[target.from] ?: CardOverrides.EMPTY,
            global = globalText,
            label = labelOf(apps, target.to),
            icon = apps.firstOrNull { it.pkg == target.to }?.icon,
            onDismiss = { pendingText = null },
            onSave = {
                onSaveOverride(target.from, it)
                pendingText = null
            },
        )
    }
}

private fun labelOf(apps: List<PickerViewModel.BrowserEntry>, pkg: String): String =
    apps.firstOrNull { it.pkg == pkg }?.label ?: pkg

/* ───────────────────────── 单行 ───────────────────────── */

@Composable
private fun SwapRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    /** 这条规则是否已单独设过文案（决定「文案」按钮是否高亮） */
    customized: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onEditText: () -> Unit,
    onDelete: () -> Unit,
    showDivider: Boolean,
) {
    val c = AppTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppSpace.minTouchTarget)
                .padding(horizontal = AppSpace.l, vertical = AppSpace.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(AppRadius.chip))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onDelete,
                    )
                    .padding(vertical = 2.dp),
            ) {
                Text(
                    text = title,
                    style = AppType.rowTitle,
                    color = if (checked) c.text else c.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = AppType.rowSub,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(AppSpace.s))
            // 每条规则自己的文案入口：设过的高亮，一眼看出哪几条是定制过的
            Box(
                modifier = Modifier
                    .heightIn(min = AppSpace.minTouchTarget)
                    .clip(RoundedCornerShape(AppRadius.pill))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onEditText,
                    )
                    .padding(horizontal = AppSpace.m),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (customized) "文案·已设" else "文案",
                    style = AppType.chip,
                    color = if (customized) c.primary else c.textSecondary,
                    maxLines = 1,
                )
            }
            AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (showDivider) AppDivider()
    }
}

/* ───────────────────────── 对话框 ───────────────────────── */

@Composable
private fun AppDialogShell(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
    // 必须是 RowScope：底部两个操作要用 Modifier.weight 均分宽度
    actions: @Composable RowScope.() -> Unit,
) {
    val c = AppTheme.colors
    val reduceMotion = rememberReduceMotion()
    // 弹窗进入：轻缩放 + 淡入。退场不做（Dialog 卸载即刻，做退场要延迟 dismiss，得不偿失）
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val appear by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(motionDuration(AppMotion.quick, reduceMotion)),
        label = "dialogAppear",
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = AppSpace.xxl)
                .graphicsLayer {
                    alpha = appear
                    val scale = 0.94f + 0.06f * appear
                    scaleX = scale
                    scaleY = scale
                }
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(AppRadius.card))
                .background(c.surface)
                .border(1.dp, c.outline, RoundedCornerShape(AppRadius.card)),
        ) {
            Text(
                text = title,
                style = AppType.rowTitle,
                color = c.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpace.xl, vertical = AppSpace.l),
            )
            AppDivider(startIndent = 0.dp)
            content()
            AppDivider(startIndent = 0.dp)
            Row(modifier = Modifier.heightIn(min = AppSpace.minTouchTarget)) {
                actions()
            }
        }
    }
}

@Composable
private fun DialogAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val c = AppTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = AppSpace.minTouchTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = AppSpace.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AppType.button,
            color = when {
                !enabled -> c.textTertiary
                danger -> c.danger
                else -> c.primary
            },
        )
    }
}

@Composable
private fun SwapEditDialog(
    apps: List<PickerViewModel.BrowserEntry>,
    onDismiss: () -> Unit,
    onConfirm: (PackageSwap) -> Unit,
) {
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(0) }
    val valid = from.contains('.') && to.contains('.') && from != to

    AppDialogShell(
        title = "添加应用替换",
        onDismiss = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpace.l)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpace.m),
            ) {
                Text(
                    text = "流体云想打开 A 时，改为打开 B。",
                    style = AppType.rowSub,
                    color = AppTheme.colors.textSecondary,
                )
                AppField(
                    label = "A · 原本要打开的 App",
                    value = from,
                    placeholder = "com.quark.browser",
                    onValueChange = { from = it.trim() },
                    onPick = { picking = 1 },
                )
                AppField(
                    label = "B · 改成打开的 App",
                    value = to,
                    placeholder = "com.quark.clouddrive",
                    onValueChange = { to = it.trim() },
                    onPick = { picking = 2 },
                )
                Text(
                    text = "填入夸克 → 夸克网盘",
                    style = AppType.chip,
                    color = AppTheme.colors.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppRadius.chip))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = {
                                from = "com.quark.browser"
                                to = "com.quark.clouddrive"
                            },
                        )
                        .padding(vertical = AppSpace.s),
                )
            }
        },
        actions = {
            DialogAction(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(AppSpace.minTouchTarget)
                    .background(AppTheme.colors.outline),
            )
            DialogAction(
                text = "保存",
                enabled = valid,
                onClick = { onConfirm(PackageSwap(from, to)) },
                modifier = Modifier.weight(1f),
            )
        },
    )

    if (picking != 0) {
        AppPickerDialog(
            title = if (picking == 1) "选择 A" else "选择 B",
            apps = apps,
            onDismiss = { picking = 0 },
            onPick = { pkg ->
                if (picking == 1) from = pkg else to = pkg
                picking = 0
            },
        )
    }
}

/** 输入框：标签在上，浅底圆角，右侧「选择」跳到应用列表 */
@Composable
private fun AppField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onPick: () -> Unit,
) {
    val c = AppTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = AppType.rowSub, color = c.textSecondary)
        Spacer(Modifier.height(AppSpace.xs))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppSpace.minTouchTarget)
                .clip(RoundedCornerShape(AppRadius.chip))
                .background(c.surfaceTonal)
                .border(1.dp, c.outline, RoundedCornerShape(AppRadius.chip))
                .padding(horizontal = AppSpace.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = AppType.mono.copy(color = c.text),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            Text(text = placeholder, style = AppType.mono, color = c.textTertiary)
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.width(AppSpace.s))
            Box(
                modifier = Modifier
                    .heightIn(min = AppSpace.minTouchTarget)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onPick,
                    )
                    .padding(horizontal = AppSpace.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "选择", style = AppType.chip, color = c.primary)
            }
        }
    }
}

@Composable
private fun AppPickerDialog(
    title: String,
    apps: List<PickerViewModel.BrowserEntry>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val c = AppTheme.colors
    AppDialogShell(
        title = title,
        onDismiss = onDismiss,
        content = {
            LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                items(apps, key = { it.pkg }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = AppSpace.minTouchTarget)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = { onPick(app.pkg) },
                            )
                            .padding(horizontal = AppSpace.l, vertical = AppSpace.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(drawable = app.icon)
                        Spacer(Modifier.width(AppSpace.m))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = app.label,
                                style = AppType.rowTitle,
                                color = c.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = app.pkg,
                                style = AppType.rowSub,
                                color = c.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    AppDivider()
                }
            }
        },
        actions = {
            DialogAction(text = "关闭", onClick = onDismiss, modifier = Modifier.weight(1f))
        },
    )
}

/** 破坏性操作确认：确认项用红色，动词具体 */
@Composable
private fun AppAlertDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val c = AppTheme.colors
    AppDialogShell(
        title = title,
        onDismiss = onDismiss,
        content = {
            Text(
                text = message,
                style = AppType.rowSub,
                color = c.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpace.xl, vertical = AppSpace.l),
            )
        },
        actions = {
            DialogAction(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(AppSpace.minTouchTarget)
                    .background(c.outline),
            )
            DialogAction(
                text = confirmText,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                danger = true,
            )
        },
    )
}

/* ───────────────────────── 单条规则的文案 ───────────────────────── */

/**
 * 某一条替换规则的卡片文字。
 *
 * 与全局「默认文案」的关系：这里**留空的项用默认文案**，填了才覆盖。
 * 所以一开始不预填全局值（预填＝用户一点保存就把默认固化成这条），
 * 每栏右侧的灰色提示会直接告诉他"留空会用哪个值"。
 */
@Composable
private fun CardOverrideDialog(
    from: String,
    to: String,
    initial: CardText,
    global: CardText,
    label: String,
    icon: Drawable?,
    onDismiss: () -> Unit,
    onSave: (CardText) -> Unit,
) {
    val c = AppTheme.colors
    var title by remember { mutableStateOf(initial.title) }
    var subtitle by remember { mutableStateOf(initial.subtitle) }
    var button by remember { mutableStateOf(initial.button) }
    var caps by remember { mutableStateOf(initial.caps) }

    val draft = CardText(title = title, subtitle = subtitle, button = button, caps = caps)
    // 留空回落默认，用于预览与右侧提示
    val effective = draft.orElse(global).resolve(label)
    val base = global.resolve(label)

    AppDialogShell(
        title = "这条规则的卡片文字",
        onDismiss = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpace.l)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpace.m),
            ) {
                Text(
                    text = "只对「$from → $to」这一条生效。留空表示沿用上面的默认文案，不会清空文字。",
                    style = AppType.rowSub,
                    color = c.textSecondary,
                )

                AppTextField(
                    label = "标题",
                    value = title,
                    placeholder = base.title.ifBlank { RedirectPrefs.DEFAULT_CARD_TITLE },
                    hint = if (title.isBlank()) "默认：${base.title}" else "→ ${effective.title}",
                    onValueChange = { title = it },
                )
                AppTextField(
                    label = "副标题",
                    value = subtitle,
                    placeholder = base.subtitle.ifBlank { RedirectPrefs.DEFAULT_CARD_SUBTITLE },
                    hint = if (subtitle.isBlank()) "默认：${base.subtitle}" else "→ ${effective.subtitle}",
                    onValueChange = { subtitle = it },
                )
                AppTextField(
                    label = "按钮",
                    value = button,
                    placeholder = base.button.ifBlank { RedirectPrefs.DEFAULT_CARD_BUTTON },
                    hint = if (button.isBlank()) "默认：${base.button}" else "→ ${effective.button}",
                    onValueChange = { button = it },
                )
                AppTextField(
                    label = "胶囊（状态栏气泡）",
                    value = caps,
                    placeholder = base.caps.ifBlank { RedirectPrefs.DEFAULT_CARD_CAPS },
                    hint = if (caps.isBlank()) "默认：${base.caps}" else "→ ${effective.caps}",
                    onValueChange = { caps = it },
                )

                Text(text = "预览", style = AppType.sectionTitle, color = c.textSecondary)
                CardPreview(text = effective, icon = icon)
            }
        },
        actions = {
            DialogAction(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(AppSpace.minTouchTarget)
                    .background(c.outline),
            )
            // 清空覆盖 = 这条重新跟默认文案一致
            DialogAction(
                text = "用默认",
                onClick = { onSave(CardOverrides.EMPTY) },
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(AppSpace.minTouchTarget)
                    .background(c.outline),
            )
            DialogAction(
                text = "保存",
                onClick = { onSave(draft) },
                modifier = Modifier.weight(1f),
            )
        },
    )
}

/** 留空的项取 [fallback] 的对应项 */
private fun CardText.orElse(fallback: CardText): CardText = CardText(
    title = title.ifBlank { fallback.title },
    subtitle = subtitle.ifBlank { fallback.subtitle },
    button = button.ifBlank { fallback.button },
    caps = caps.ifBlank { fallback.caps },
)
