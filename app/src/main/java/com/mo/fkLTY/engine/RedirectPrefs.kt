package com.mo.fkLTY.engine

import android.content.SharedPreferences

/**
 * 远程偏好键(hook 端读取 / 设置 App 端写入),group 名沿用旧模块的 "redirect_config"。
 */
object RedirectPrefs {
    /** 远程偏好组名,与 01 号文档旧模块语义一致 */
    const val GROUP = "redirect_config"

    /** 重定向目标浏览器包名;不存在或空白 = 禁用重定向 */
    const val KEY_TARGET = "target_browser_package"

    /** 规则 1:heytapbrowser://webpage?url= 协议卡片,默认开 */
    const val KEY_RULE_PROTOCOL = "rule_heytap_protocol"

    /** 规则 2:文字搜索卡片(textType == 29),默认开 */
    const val KEY_RULE_TEXT_SEARCH = "rule_text_search"

    /** 规则 3:OPPO/Heytap 浏览器 + 合法 http(s) 网页卡片,默认开 */
    const val KEY_RULE_HEYTAP_HTTP = "rule_heytap_http"

    /** 规则 4(实验性,默认关):目标是其他浏览器的 VIEW+http(s) 卡片 */
    const val KEY_RULE_BROWSER_CARD = "rule_browser_card"

    /** 应用替换表(JSON 数组):流体云要打开 A 时改成打开 B。口令卡用得到 */
    const val KEY_PACKAGE_SWAPS = "package_swaps_v1"

    /** 详细日志(hook 端 verbose 输出,含监控位) */
    const val KEY_VERBOSE_LOG = "verbose_log"

    /**
     * 卡片改字:命中「应用替换」时,把卡片上的"安装/去安装"换成自定义文字。
     * 默认开——改字是为了让卡片和实际行为一致,关掉只会让人以为卡片还是坏的。
     */
    const val KEY_REWRITE_CARD_LABEL = "rewrite_card_label"

    // ---- 自定义卡片文字(四个字段各存一个键,不拼串,省掉转义与解析) ----

    /** 卡片标题,`{app}` 会替换成目标应用名 */
    const val KEY_CARD_TEXT_TITLE = "card_text_title"

    /** 卡片副标题 */
    const val KEY_CARD_TEXT_SUBTITLE = "card_text_subtitle"

    /** 按钮文字(主按钮 / 左右按钮共用) */
    const val KEY_CARD_TEXT_BUTTON = "card_text_button"

    /** 胶囊文字(状态栏气泡上的那句) */
    const val KEY_CARD_TEXT_CAPS = "card_text_caps"

    /** 占位符:运行时替换为目标应用的显示名 */
    const val PLACEHOLDER_APP = "{app}"

    const val DEFAULT_CARD_TITLE = "打开${PLACEHOLDER_APP}"
    const val DEFAULT_CARD_SUBTITLE = "点击打开${PLACEHOLDER_APP}"
    const val DEFAULT_CARD_BUTTON = "打开"
    const val DEFAULT_CARD_CAPS = "打开${PLACEHOLDER_APP}"

    /**
     * 读取自定义卡片文字。留空的字段保持空串——钩子侧据此判断"这一项不改",
     * 而不是偷偷回退成默认值(用户清空某一行,通常就是不想改那一行)。
     */
    fun cardText(prefs: SharedPreferences): CardText = CardText(
        title = prefs.getString(KEY_CARD_TEXT_TITLE, null) ?: DEFAULT_CARD_TITLE,
        subtitle = prefs.getString(KEY_CARD_TEXT_SUBTITLE, null) ?: DEFAULT_CARD_SUBTITLE,
        button = prefs.getString(KEY_CARD_TEXT_BUTTON, null) ?: DEFAULT_CARD_BUTTON,
        caps = prefs.getString(KEY_CARD_TEXT_CAPS, null) ?: DEFAULT_CARD_CAPS,
    )

    /** 规则开关集;设置页与 hook 端共用同一组键与默认值 */
    fun toggles(prefs: SharedPreferences): RuleToggles = RuleToggles(
        protocol = prefs.getBoolean(KEY_RULE_PROTOCOL, true),
        textSearch = prefs.getBoolean(KEY_RULE_TEXT_SEARCH, true),
        heytapHttp = prefs.getBoolean(KEY_RULE_HEYTAP_HTTP, true),
        anyBrowserCard = prefs.getBoolean(KEY_RULE_BROWSER_CARD, false),
    )
}

/** 重定向规则开关(数据驱动,替代旧模块的硬编码三规则) */
data class RuleToggles(
    val protocol: Boolean,
    val textSearch: Boolean,
    val heytapHttp: Boolean,
    val anyBrowserCard: Boolean,
)

/**
 * 卡片上四处可自定义的文字。
 *
 * 用 `{app}` 占位目标应用名,所以换目标 App 时文案会自动跟着变,不用手改。
 * 任一项留空 = 该项不动(保留 metis 原文案)。
 */
data class CardText(
    val title: String = RedirectPrefs.DEFAULT_CARD_TITLE,
    val subtitle: String = RedirectPrefs.DEFAULT_CARD_SUBTITLE,
    val button: String = RedirectPrefs.DEFAULT_CARD_BUTTON,
    val caps: String = RedirectPrefs.DEFAULT_CARD_CAPS,
) {
    /** 把 `{app}` 换成实际应用名 */
    fun resolve(appLabel: String): CardText = CardText(
        title = title.replace(RedirectPrefs.PLACEHOLDER_APP, appLabel),
        subtitle = subtitle.replace(RedirectPrefs.PLACEHOLDER_APP, appLabel),
        button = button.replace(RedirectPrefs.PLACEHOLDER_APP, appLabel),
        caps = caps.replace(RedirectPrefs.PLACEHOLDER_APP, appLabel),
    )
}
