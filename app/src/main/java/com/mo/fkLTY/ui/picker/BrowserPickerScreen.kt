package com.mo.fkLTY.ui.picker

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mo.fkLTY.R
import com.mo.fkLTY.engine.CardText
import com.mo.fkLTY.engine.PackageSwap
import com.mo.fkLTY.engine.RedirectPrefs
import com.mo.fkLTY.ui.theme.AppMotion
import com.mo.fkLTY.ui.theme.AppRadius
import com.mo.fkLTY.ui.theme.AppSpace
import com.mo.fkLTY.ui.theme.AppTheme
import com.mo.fkLTY.ui.theme.AppType
import com.mo.fkLTY.ui.theme.ThemeMode
import com.mo.fkLTY.ui.theme.motionDuration
import com.mo.fkLTY.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay

/**
 * 主屏（现代原生）。
 *
 * 结构：固定标题 + 四段分段控件 + 可滚动内容。
 *
 * 为什么用分段而不是一条长列表：功能一共四块（规则 / 浏览器 / 替换 / 诊断），
 * 全堆一页里滚动，每次都要滑很久才到"应用替换"。分段把每块压到一屏内，
 * 也顺手解决了"改完设置找不到自己在哪"的问题。
 */
@Composable
fun BrowserPickerScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    viewModel: PickerViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val c = AppTheme.colors
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    var toastText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ui.message) {
        val msg = ui.message ?: return@LaunchedEffect
        toastText = msg.arg?.let { context.getString(msg.res, it) } ?: context.getString(msg.res)
        viewModel.consumeMessage()
        delay(2400)
        toastText = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppSpace.screenInset, end = AppSpace.screenInset, top = AppSpace.m),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = AppType.pageTitle,
                    color = c.text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "在这台手机上接管流体云卡片的跳转",
                    style = AppType.pageSubtitle,
                    color = c.textSecondary,
                )
            }

            AppSegment(
                options = listOf("浏览器", "应用替换", "关于"),
                selectedIndex = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(
                    start = AppSpace.screenInset,
                    end = AppSpace.screenInset,
                    top = AppSpace.l,
                ),
            )

            // 页面切换：旧页先快速淡出（不位移），退完新页再淡入 + 向切换方向轻推。
            // 旧页淡出与新页淡入**错开进行**，转场中两页不会叠在一起 ——
            // 之前同时淡入淡出，长列表页切换时新旧内容整个叠印成一团。
            // 每页滚动位置各自保留，切回来还停在原来的位置。
            val reduceMotion = rememberReduceMotion()
            val tabDur = motionDuration(AppMotion.regular, reduceMotion)
            val outDur = motionDuration(AppMotion.instant, reduceMotion)
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (
                        fadeIn(tween(tabDur, delayMillis = outDur)) +
                            slideInHorizontally(tween(tabDur, delayMillis = outDur)) { w -> dir * w / 16 }
                        ) togetherWith fadeOut(tween(outDur))
                },
                label = "tabContent",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { currentTab ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) })
                        .padding(
                            start = AppSpace.screenInset,
                            end = AppSpace.screenInset,
                            top = AppSpace.l,
                            bottom = AppSpace.xxl,
                        ),
                    verticalArrangement = Arrangement.spacedBy(AppSpace.m),
                ) {
                    when (currentTab) {
                        0 -> BrowserTab(
                            ui = ui,
                            onToggleRule = viewModel::toggleRule,
                            onSelect = viewModel::select,
                            onRequestScope = viewModel::requestScope,
                            onGotoSwap = { tab = 1 },
                            onGotoAbout = { tab = 2 },
                        )

                        1 -> SwapTab(
                            swaps = ui.packageSwaps,
                            apps = ui.installedApps,
                            cardText = ui.cardText,
                            rewriteEnabled = ui.rules[RedirectPrefs.KEY_REWRITE_CARD_LABEL] == true,
                            onToggleRewrite = {
                                viewModel.toggleRule(RedirectPrefs.KEY_REWRITE_CARD_LABEL, it)
                            },
                            onSaveCardText = viewModel::saveCardText,
                            onSave = viewModel::savePackageSwaps,
                        )

                        else -> AboutTab(
                            ui = ui,
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            onToggleVerboseLog = { viewModel.toggleRule(RedirectPrefs.KEY_VERBOSE_LOG, it) },
                        )
                    }
                }
            }
        }

        AppToast(
            text = toastText,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = AppSpace.xxl),
        )
    }
}

/* ───────────────────────── 浏览器页 ───────────────────────── */

/**
 * 浏览器页 = 整条「网页/搜索卡」链路：状态 + 目标浏览器 + 4 条匹配规则。
 *
 * 规则放在这里而不是单独一页，是因为它们**只对浏览器重定向生效**：
 * 判据是"意图里有没有网址"。应用替换走的是另一条路（按包名），规则对它没有任何影响。
 */
@Composable
private fun BrowserTab(
    ui: PickerViewModel.UiState,
    onToggleRule: (String, Boolean) -> Unit,
    onSelect: (String?) -> Unit,
    onRequestScope: () -> Unit,
    onGotoSwap: () -> Unit,
    onGotoAbout: () -> Unit,
) {
    val c = AppTheme.colors

    // 只有出问题时才多提示：正常状态下不占位置
    val notice: Pair<String, String>? = when {
        !ui.bound -> stringResource(R.string.status_inactive_title) to
            stringResource(R.string.status_inactive_subtitle)

        !ui.metisInScope -> stringResource(R.string.status_scope_missing_title) to
            stringResource(R.string.status_scope_missing_subtitle)

        else -> null
    }

    HeroCard(
        active = ui.bound && ui.metisInScope && ui.target != null,
        title = when {
            notice != null -> notice.first
            ui.target == null -> stringResource(R.string.status_disabled_title)
            else -> stringResource(R.string.status_active_title)
        },
        meta = when {
            notice != null -> notice.second
            ui.target == null -> stringResource(R.string.status_disabled_subtitle)
            else -> stringResource(R.string.status_active_subtitle, targetLabel(ui))
        },
        actionText = if (notice != null) stringResource(R.string.request_scope) else null,
        onAction = onRequestScope,
    )

    AppSectionTitle(stringResource(R.string.browser_list_header))
    AppCard(padded = false) {
        AppChoiceRow(
            title = stringResource(R.string.disable_redirect),
            subtitle = stringResource(R.string.disable_redirect_subtitle),
            selected = ui.target == null,
            onClick = { onSelect(null) },
            showDivider = ui.browsers.isNotEmpty(),
        )
        ui.browsers.forEachIndexed { index, entry ->
            AppChoiceRow(
                title = entry.label,
                subtitle = entry.pkg,
                icon = entry.icon,
                selected = ui.target == entry.pkg,
                onClick = { onSelect(entry.pkg) },
                showDivider = index != ui.browsers.lastIndex,
            )
        }
    }
    if (ui.browsers.isEmpty() && !ui.loadingBrowsers) {
        Text(
            text = stringResource(R.string.no_browsers),
            style = AppType.rowSub,
            color = c.textTertiary,
            modifier = Modifier.padding(start = AppSpace.xs),
        )
    } else {
        Text(
            text = stringResource(R.string.footer_browser),
            style = AppType.rowSub,
            color = c.textTertiary,
            modifier = Modifier.padding(start = AppSpace.xs),
        )
    }

    AppSectionTitle(stringResource(R.string.section_rules))
    AppCard(padded = false) {
        BROWSER_RULE_SPECS.forEachIndexed { index, spec ->
            AppRuleRow(
                icon = spec.icon,
                title = stringResource(spec.titleRes),
                subtitle = stringResource(spec.subtitleRes),
                checked = ui.rules[spec.key] == true,
                onCheckedChange = { onToggleRule(spec.key, it) },
                showDivider = index != BROWSER_RULE_SPECS.lastIndex,
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(AppSpace.s)) {
        AppChip(text = "＋ 添加替换", onClick = onGotoSwap)
        AppChip(text = "查看日志", onClick = onGotoAbout)
    }

    Text(
        text = "以上规则只影响「带网址的卡片」。复制口令那类卡片没有网址，走的是「应用替换」。",
        style = AppType.rowSub,
        color = c.textTertiary,
        modifier = Modifier.padding(start = AppSpace.xs, top = AppSpace.xs),
    )
}

/** 主状态卡：渐变底 + 白字，是整页唯一的强调色区域。`actionText` 为 null 时不显示按钮 */
@Composable
private fun HeroCard(
    active: Boolean,
    title: String,
    meta: String,
    actionText: String?,
    onAction: () -> Unit,
) {
    val c = AppTheme.colors
    val brush = if (active) {
        Brush.linearGradient(listOf(c.heroStart, c.heroMid, c.heroEnd))
    } else {
        Brush.linearGradient(listOf(c.heroStart, c.heroStart))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.hero))
            .background(brush)
            .padding(AppSpace.xl),
    ) {
        Column {
            Text(
                text = if (active) "当前状态" else "需要处理",
                style = AppType.heroEyebrow,
                color = Color.White.copy(alpha = 0.82f),
            )
            Spacer(Modifier.height(AppSpace.s))
            Text(text = title, style = AppType.heroValue, color = Color.White)
            Spacer(Modifier.height(2.dp))
            Text(
                text = meta,
                style = AppType.heroMeta,
                color = Color.White.copy(alpha = 0.9f),
            )
            if (actionText != null) {
                Spacer(Modifier.height(AppSpace.l))
                AppButton(
                    text = actionText,
                    onClick = onAction,
                    container = Color.White.copy(alpha = 0.94f),
                    contentColor = c.heroStart,
                )
            }
        }
    }
}

/* ───────────────────────── 应用替换页 ───────────────────────── */

/**
 * 应用替换页 = 按包名换目标，与浏览器重定向完全独立。
 *
 * 这条链路是通用的：判据只有 `from == 卡片目标包名`，没有写死任何 App。
 * 只要知道"卡片想打开谁"，把它换成另一个已安装的 App 就行。
 */
@Composable
private fun SwapTab(
    swaps: List<PackageSwap>,
    apps: List<PickerViewModel.BrowserEntry>,
    cardText: CardText,
    rewriteEnabled: Boolean,
    onToggleRewrite: (Boolean) -> Unit,
    onSaveCardText: (CardText) -> Unit,
    onSave: (List<PackageSwap>) -> Unit,
) {
    val c = AppTheme.colors

    PackageSwapEditor(swaps = swaps, apps = apps, onSave = onSave)

    Text(
        text = stringResource(R.string.footer_swap),
        style = AppType.rowSub,
        color = c.textTertiary,
        modifier = Modifier.padding(start = AppSpace.xs, top = AppSpace.s),
    )
    Text(
        text = "不限于夸克：任意「卡片要打开 A → 改成打开 B」都能配。" +
            "目标 App 没装时会自动跳过，卡片保持原样，不会变成死链。",
        style = AppType.rowSub,
        color = c.textTertiary,
        modifier = Modifier.padding(start = AppSpace.xs),
    )

    // ── 卡片文字 ──
    AppSectionTitle(stringResource(R.string.rewrite_section))
    AppCard(padded = false) {
        AppRuleRow(
            icon = Icons.Outlined.Edit,
            title = stringResource(R.string.rewrite_label_title),
            subtitle = stringResource(R.string.rewrite_label_subtitle),
            checked = rewriteEnabled,
            onCheckedChange = onToggleRewrite,
        )
    }

    if (rewriteEnabled) {
        // 预览用第一条启用的替换作为样本：图标与名字都取真机上的实际值
        val preview = swaps.firstOrNull { it.enabled }?.to
            ?.let { pkg -> apps.firstOrNull { it.pkg == pkg } }
        CardTextEditor(
            current = cardText,
            previewLabel = preview?.label ?: "目标应用",
            previewIcon = preview?.icon,
            onSave = onSaveCardText,
        )
    } else {
        Text(
            text = "已关闭：卡片保持流体云原文案（安装 / 去安装），但点了仍然会打开目标 App。",
            style = AppType.rowSub,
            color = c.danger,
            modifier = Modifier.padding(start = AppSpace.xs),
        )
    }
}

/**
 * 卡片文字编辑器：四处文字都能自定义。
 *
 * 编辑态是本地草稿，点「保存」才写入——避免每敲一个字就 commit 一次远程偏好。
 * `{app}` 是占位符，运行时替换成目标应用名，所以换目标 App 不用改文案。
 */
@Composable
private fun CardTextEditor(
    current: CardText,
    previewLabel: String,
    previewIcon: Drawable?,
    onSave: (CardText) -> Unit,
) {
    val c = AppTheme.colors
    var title by remember(current) { mutableStateOf(current.title) }
    var subtitle by remember(current) { mutableStateOf(current.subtitle) }
    var button by remember(current) { mutableStateOf(current.button) }
    var caps by remember(current) { mutableStateOf(current.caps) }

    val draft = CardText(title = title, subtitle = subtitle, button = button, caps = caps)
    val dirty = draft != current
    // 每栏右侧提示的最终效果；模板里的 {app} 在这里展开，一眼能看出真机长什么样
    val resolved = draft.resolve(previewLabel)

    AppCard {
        Text(
            text = "用 {app} 代表目标应用名，换目标 App 时文案会自动跟着变；留空表示这一项不改。右侧灰色小字是最终显示效果。",
            style = AppType.rowSub,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(AppSpace.m))

        AppTextField(
            label = "标题",
            value = title,
            placeholder = RedirectPrefs.DEFAULT_CARD_TITLE,
            hint = if (title.isBlank()) "保持原文案" else "→ ${resolved.title}",
            onValueChange = { title = it },
        )
        Spacer(Modifier.height(AppSpace.m))
        AppTextField(
            label = "副标题",
            value = subtitle,
            placeholder = RedirectPrefs.DEFAULT_CARD_SUBTITLE,
            hint = if (subtitle.isBlank()) "保持原文案" else "→ ${resolved.subtitle}",
            onValueChange = { subtitle = it },
        )
        Spacer(Modifier.height(AppSpace.m))
        AppTextField(
            label = "按钮",
            value = button,
            placeholder = RedirectPrefs.DEFAULT_CARD_BUTTON,
            hint = if (button.isBlank()) "保持原文案" else "→ ${resolved.button}",
            onValueChange = { button = it },
        )
        Spacer(Modifier.height(AppSpace.m))
        AppTextField(
            label = "胶囊（状态栏气泡）",
            value = caps,
            placeholder = RedirectPrefs.DEFAULT_CARD_CAPS,
            hint = if (caps.isBlank()) "保持原文案" else "→ ${resolved.caps}",
            onValueChange = { caps = it },
        )

        Spacer(Modifier.height(AppSpace.l))
        Text(text = "预览", style = AppType.sectionTitle, color = c.textSecondary)
        Spacer(Modifier.height(AppSpace.s))
        CardPreview(text = resolved, icon = previewIcon)

        Spacer(Modifier.height(AppSpace.l))
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpace.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppButton(
                text = "保存",
                onClick = { onSave(draft) },
                enabled = dirty,
            )
            AppChip(
                text = "恢复默认",
                onClick = { onSave(CardText()) },
            )
        }
    }
}

/**
 * 卡片长相的预览：按真机卡片的排布——左图标、中两行文字、右按钮。
 *
 * 配色跟随界面主题（浅色主题用浅底深字），而不是照搬真机的深色卡片：
 * 这一页是设置界面，突兀的黑块反而割裂。要看的是"文字与位置"，不是色值。
 */
@Composable
private fun CardPreview(text: CardText, icon: Drawable?) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surfaceTonal)
            .border(1.dp, c.outline, shape)
            .padding(horizontal = AppSpace.l, vertical = AppSpace.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(c.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                AppIcon(drawable = icon, size = 36.dp)
            }
        }
        Spacer(Modifier.width(AppSpace.m))
        Column(Modifier.weight(1f)) {
            Text(
                text = text.title.ifBlank { "（标题保持原文案）" },
                style = AppType.cardTitle,
                color = if (text.title.isBlank()) c.textTertiary else c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = text.subtitle.ifBlank { "（副标题保持原文案）" },
                style = AppType.rowSub,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(AppSpace.m))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(c.primary)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                text = text.button.ifBlank { "—" },
                style = AppType.button,
                color = c.onPrimary,
            )
        }
    }
}

/* ───────────────────────── 关于页（诊断 + 外观 + 说明） ───────────────────────── */

@Composable
private fun AboutTab(
    ui: PickerViewModel.UiState,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onToggleVerboseLog: (Boolean) -> Unit,
) {
    val c = AppTheme.colors
    val context = LocalContext.current

    AppSectionTitle(stringResource(R.string.section_diagnostics))
    AppCard(padded = false) {
        AppRow(
            title = stringResource(R.string.diag_framework_label),
            subtitle = ui.framework ?: "—",
            showDivider = true,
        )
        AppRow(
            title = stringResource(R.string.diag_scope_label),
            subtitle = when {
                !ui.bound -> "—"
                ui.metisInScope -> "com.oplus.metis"
                else -> stringResource(R.string.diag_scope_none)
            },
            showDivider = true,
        )
        AppRuleRow(
            icon = Icons.Outlined.Terminal,
            title = stringResource(R.string.verbose_log_title),
            subtitle = stringResource(R.string.verbose_log_subtitle),
            checked = ui.rules[RedirectPrefs.KEY_VERBOSE_LOG] == true,
            onCheckedChange = onToggleVerboseLog,
            showDivider = true,
        )
        // 日志提示是一整句，不能当行标题（会被压成一行截断），单独排版
        Text(
            text = stringResource(R.string.diag_log_hint),
            style = AppType.rowSub,
            color = c.textSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpace.l, vertical = AppSpace.m),
        )
    }

    AppSectionTitle("外观")
    AppCard {
        Text(text = "界面主题", style = AppType.rowTitle, color = c.text)
        Spacer(Modifier.height(AppSpace.m))
        val modes = listOf(ThemeMode.Auto, ThemeMode.Dark, ThemeMode.Light)
        AppSegment(
            options = listOf("跟随系统", "深色", "浅色"),
            selectedIndex = modes.indexOf(themeMode).coerceAtLeast(0),
            onSelect = { onThemeModeChange(modes[it]) },
        )
    }

    AppSectionTitle(stringResource(R.string.about_title))
    AppCard {
        val versionName = remember(context.packageName) {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull() ?: ""
        }
        Text(
            text = stringResource(R.string.about_intro),
            style = AppType.rowSub,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(AppSpace.m))
        AboutLine(stringResource(R.string.about_usage_browser))
        AboutLine(stringResource(R.string.about_usage_swap))
        AboutLine(stringResource(R.string.about_usage_uninstalled))
        Spacer(Modifier.height(AppSpace.m))
        Text(
            text = stringResource(R.string.about_note),
            style = AppType.rowSub,
            color = c.danger,
        )
        Spacer(Modifier.height(AppSpace.s))
        Text(
            text = stringResource(R.string.about_version, versionName, context.packageName),
            style = AppType.mono,
            color = c.textTertiary,
        )
    }
}

@Composable
private fun AboutLine(text: String) {
    val c = AppTheme.colors
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(text = "·", style = AppType.rowSub, color = c.textTertiary)
        Spacer(Modifier.width(AppSpace.s))
        Text(text = text, style = AppType.rowSub, color = c.text)
    }
}

/* ───────────────────────── 派生数据 ───────────────────────── */

private fun targetLabel(ui: PickerViewModel.UiState): String {
    val target = ui.target ?: return "—"
    return ui.browsers.firstOrNull { it.pkg == target }?.label ?: target
}

private class RuleSpec(
    val key: String,
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
)

/**
 * 只列「浏览器重定向」的匹配规则（4 条）。
 * 「详细日志」不属于这里——它是诊断开关，放在关于页的「诊断」卡里。
 */
private val BROWSER_RULE_SPECS = listOf(
    RuleSpec(
        RedirectPrefs.KEY_RULE_PROTOCOL,
        R.string.rule_protocol_title,
        R.string.rule_protocol_subtitle,
        Icons.Outlined.Link,
    ),
    RuleSpec(
        RedirectPrefs.KEY_RULE_TEXT_SEARCH,
        R.string.rule_text_search_title,
        R.string.rule_text_search_subtitle,
        Icons.Outlined.Search,
    ),
    RuleSpec(
        RedirectPrefs.KEY_RULE_HEYTAP_HTTP,
        R.string.rule_heytap_http_title,
        R.string.rule_heytap_http_subtitle,
        Icons.Outlined.Public,
    ),
    RuleSpec(
        RedirectPrefs.KEY_RULE_BROWSER_CARD,
        R.string.rule_browser_card_title,
        R.string.rule_browser_card_subtitle,
        Icons.Outlined.Explore,
    ),
)

/** 底部轻提示：深色胶囊 + 白字，明暗两套都能读。上滑淡入，不用时淡出 */
@Composable
private fun AppToast(text: String?, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val reduceMotion = rememberReduceMotion()
    val dur = motionDuration(AppMotion.quick, reduceMotion)
    var lastText by remember { mutableStateOf("") }
    LaunchedEffect(text) {
        if (text != null) lastText = text
    }

    AnimatedVisibility(
        visible = text != null && lastText.isNotEmpty(),
        enter = fadeIn(tween(dur)) + slideInVertically(tween(dur)) { it / 2 },
        exit = fadeOut(tween(dur)) + slideOutVertically(tween(dur)) { it / 2 },
        modifier = modifier,
        label = "appToast",
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(AppRadius.pill))
                .background(if (c.isDark) Color(0xFF2C2938) else Color(0xE61B1B21))
                .padding(horizontal = AppSpace.l, vertical = AppSpace.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(AppRadius.pill))
                    .background(c.primary),
            )
            Spacer(Modifier.width(AppSpace.s))
            Text(text = lastText, style = AppType.rowSub, color = Color.White)
        }
    }
}
