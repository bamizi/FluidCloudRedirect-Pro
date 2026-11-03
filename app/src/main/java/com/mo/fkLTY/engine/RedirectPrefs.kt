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

    /** 占位符:运行时替换成网页域名(仅浏览器卡片有意义) */
    const val PLACEHOLDER_HOST = "{host}"

    const val DEFAULT_CARD_TITLE = "打开${PLACEHOLDER_APP}"
    const val DEFAULT_CARD_SUBTITLE = "点击打开${PLACEHOLDER_APP}"
    const val DEFAULT_CARD_BUTTON = "打开"
    const val DEFAULT_CARD_CAPS = "打开${PLACEHOLDER_APP}"

    // ---- 分条覆盖与浏览器卡片(1.2.18) ----

    /** 每条替换规则自己的文案(JSON 数组,key = from 包名);未设置的回落上面四个全局键 */
    const val KEY_CARD_OVERRIDES = "card_overrides_v1"

    /** 浏览器卡片的专用文案(JSON 对象);与替换链路的文案完全独立 */
    const val KEY_BROWSER_CARD_TEXT = "browser_card_text_v1"

    /** 浏览器卡片改字的开关;**默认关**——网页卡数量多,判据错一次就改到不该改的卡 */
    const val KEY_BROWSER_CARD_TEXT_ENABLED = "browser_card_text_enabled"

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

    /**
     * 某条替换规则实际使用的文案:先查它自己的覆盖,没有就回落全局默认。
     *
     * 这是 1.2.18 的核心——多条规则不再共用一份文案。
     */
    fun cardText(prefs: SharedPreferences, from: String): CardText =
        overrides(prefs)[from] ?: cardText(prefs)

    /** 浏览器卡片文案(独立一份,不看替换表) */
    fun browserCardText(prefs: SharedPreferences): CardText =
        CardOverrides.decodeOne(prefs.getString(KEY_BROWSER_CARD_TEXT, null))

    /** 分条覆盖表;`Seedling$Builder.build()` 每次卡片刷新都会问一次,按 JSON 串缓存 */
    fun overrides(prefs: SharedPreferences): Map<String, CardText> {
        val json = prefs.getString(KEY_CARD_OVERRIDES, null)
        if (json != cachedOverrideJson) {
            cachedOverrideJson = json
            cachedOverrides = CardOverrides.decodeAll(json)
        }
        return cachedOverrides
    }

    @Volatile
    private var cachedOverrideJson: String? = null

    @Volatile
    private var cachedOverrides: Map<String, CardText> = emptyMap()

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
    /**
     * 把占位符换成实际值。
     *
     * @param appLabel 目标应用的显示名（`{app}`）
     * @param host     网页域名（`{host}`，仅浏览器卡片用）。为 null 时占位符替换成空串，
     *                 不把 `{host}` 原样漏到卡片上。
     */
    fun resolve(appLabel: String, host: String? = null): CardText = CardText(
        title = fill(title, appLabel, host),
        subtitle = fill(subtitle, appLabel, host),
        button = fill(button, appLabel, host),
        caps = fill(caps, appLabel, host),
    )

    private fun fill(raw: String, appLabel: String, host: String?): String =
        raw.replace(RedirectPrefs.PLACEHOLDER_APP, appLabel)
            .replace(RedirectPrefs.PLACEHOLDER_HOST, host ?: "")

    /** 四个字段全空 = 这条什么也不改 */
    fun isEmpty(): Boolean =
        title.isBlank() && subtitle.isBlank() && button.isBlank() && caps.isBlank()
}
