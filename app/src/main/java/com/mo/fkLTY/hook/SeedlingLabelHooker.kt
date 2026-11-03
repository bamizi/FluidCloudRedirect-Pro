package com.mo.fkLTY.hook

import android.content.Context
import com.mo.fkLTY.engine.BrowserAvailability
import com.mo.fkLTY.engine.PackageSwapEngine
import com.mo.fkLTY.engine.Policy
import com.mo.fkLTY.engine.RedirectPrefs
import io.github.libxposed.api.XposedInterface

/**
 * E 显示层:改写「引导安装」卡片的按钮文案(安装 → 打开)。
 *
 * 为什么现在才做:卡片是 metis 用 `Seedling.Builder` 构建、再交给 SystemUI(SeedlingCard 窗口)
 * 渲染的,和点击走的 `TextIntent.invoke` / `ContextImpl.startActivity` 是两条路。
 * 所以以前无论怎么改 Intent,卡片上的字都不动 —— 那不是 bug,是没找到渲染层。
 *
 * 已在真机 metis(40463283 字节的 /product/priv-app/Metis/Metis.apk)里核对过符号:
 * ```
 * Lcom/oplus/textintent/pantanal/Seedling$Builder;
 *   build()                    -> Lcom/oplus/textintent/pantanal/Seedling;
 *   buttonLabel(String)        -> Builder      // 构建期设文案
 *   cardTitle / cardSubTitle / capsTitle / capsDirectText(String) -> Builder
 * Lcom/oplus/textintent/pantanal/Seedling;
 *   getCardInfo() -> CardInfo, getData() -> ActionData, show()V
 * Lcom/oplus/textintent/pantanal/bean/CardInfo;   // Kotlin data class,有 get/set
 *   setButtonText / setLeftButtonText / setRightButtonText
 *   setCardTitle / setCardSubTitle / setCapsTitle / setCapsDirectText   (均 String)
 * ```
 *
 * 1.2.13 起不只改按钮:实测 metis 给 SystemUI 的 businessData 里,按钮改成"打开X"之后
 * `cardTitle`/`capsTitle` 仍写着"安装"夸克"查看"、`cardSubTitle` 仍写着"前往一加软件商店安装",
 * 卡片自相矛盾。所以标题三处 + 副标题一并改写(仅当原文含"安装"时)。
 * 实测证据见文档 §17.5。
 *
 * 1.2.14 按实际使用体验收敛文案长度:「打开“夸克网盘”查看」在卡片上偏挤,
 * 改为 标题/按钮 = 「打开夸克网盘」、副标题 = 「点击打开夸克网盘」。
 * 1.2.15 按钮压到两个字「打开」,目标 App 名交给标题和副标题,按钮不再重复。
 *
 * 选 `build()` 而不是 `buttonLabel(String)` 的原因:后者被调用时还没设动作/包名,
 * 无法判断"这张卡是不是我们关心的那个源 App";`build()` 返回的 Seedling 已经带着
 * 完整的 CardInfo + ActionData,可以按包名严格匹配再动手,不会误伤别的安装卡。
 *
 * 安全性:只改文案,不动 Intent/不改跳转逻辑;任何一步反射失败都只记日志并原样放行。
 */
class SeedlingLabelHooker : XposedInterface.Hooker {

    override fun intercept(chain: XposedInterface.Chain): Any? {
        val seedling = chain.proceed()
        try {
            rewrite(chain.thisObject, seedling)
        } catch (t: Throwable) {
            // 别用 once：抛过一次之后就不再记录，会把后续的失败全藏起来
            HookLog.card("SeedlingLabelHooker error: ${t.javaClass.simpleName}: ${t.message}")
        }
        return seedling
    }

    private fun rewrite(builder: Any?, seedling: Any?) {
        // 进过一次就留痕:用来判断 build() 这条路径到底有没有被走到
        HookLog.once("seedling-entered", "seedling[build]: entered")
        // 以下每个出口都留痕。之前这些 return 全是静默的，导致只知道"进来了"
        // 却不知道卡在哪一步——排查时最大的盲点。
        if (seedling == null) {
            HookLog.card("diag: seedling=null，跳过")
            return
        }
        val prefs = HookLog.prefs()
        if (prefs == null) {
            HookLog.card("diag: 远程偏好不可用(prefs=null)，跳过")
            return
        }
        // 开关：「应用替换」页里的「改卡片文字」，默认开
        val rewriteEnabled = prefs.getBoolean(RedirectPrefs.KEY_REWRITE_CARD_LABEL, true)
        val swaps = PackageSwapEngine.load(prefs).filter { it.enabled && it.from != it.to }
        // 浏览器卡片改字：独立开关，默认关（网页卡数量多，判据错一次就改到不该改的卡）
        val browserTextEnabled = prefs.getBoolean(RedirectPrefs.KEY_BROWSER_CARD_TEXT_ENABLED, false)
        // 卡片图标：与改字完全独立——用户可能只想换图标、不动文案，所以单独判一次
        val iconSpec = prefs.getString(RedirectPrefs.KEY_CARD_ICON_URI, null)?.trim().orEmpty()
        HookLog.card(
            "diag: 开关 text=$rewriteEnabled browser=$browserTextEnabled " +
                "icon=${iconSpec.ifEmpty { "无" }} swaps=${swaps.size}" +
                swaps.joinToString(prefix = " [", postfix = "]") { "${it.from}->${it.to}" },
        )
        if (!rewriteEnabled && !browserTextEnabled && iconSpec.isEmpty()) {
            HookLog.card("diag: 三个开关全关，跳过")
            return
        }
        if (swaps.isEmpty() && !browserTextEnabled) {
            HookLog.card("diag: 没有启用的替换规则，跳过")
            return
        }

        val ctxRaw = readField(builder, "context")
        val context = ctxRaw as? Context
        if (context == null) {
            HookLog.card(
                "diag: builder.context 不可用 " +
                    "(builder=${builder?.javaClass?.name} value=${ctxRaw?.javaClass?.name})",
            )
            return
        }

        val sCls = seedling.javaClass
        val cardInfo = invoke(sCls, seedling, "getCardInfo")
        if (cardInfo == null) {
            HookLog.card("diag: getCardInfo 返回 null (seedlingCls=${sCls.name})")
            return
        }
        val data = invoke(sCls, seedling, "getData")
        val ciCls = cardInfo.javaClass

        val button = str(ciCls, cardInfo, "getButtonText")
        val left = str(ciCls, cardInfo, "getLeftButtonText")
        val right = str(ciCls, cardInfo, "getRightButtonText")
        val title = str(ciCls, cardInfo, "getCardTitle")

        // 只碰"引导安装"这类卡片,普通卡片一个字都不动
        val looksInstall = listOfNotNull(button, left, right, title).any { it.contains("安装") }
        HookLog.card(
            "diag: looksInstall=$looksInstall title=$title button=$button left=$left right=$right",
        )
        if (looksInstall && swaps.isNotEmpty() && (rewriteEnabled || iconSpec.isNotEmpty())) {
            if (
                rewriteInstallCard(
                    context, prefs, cardInfo, ciCls, data, swaps,
                    button, left, right, title,
                    textEnabled = rewriteEnabled,
                    iconSpec = iconSpec,
                )
            ) return
        }

        // 不是安装卡（或安装卡没匹配到）时，再看是不是网页卡。
        // 安装卡一律不交给浏览器分支：它身上可能带商店链接，被网页文案盖掉会自相矛盾。
        if (browserTextEnabled && !looksInstall) {
            rewriteBrowserCard(context, prefs, cardInfo, ciCls)
        }
    }

    /**
     * 「引导安装」卡：按命中的那一条替换规则取文案（每条可单独设，未设的回落全局默认）。
     *
     * @return true 表示已处理（无论是否改成功），调用方不必再走浏览器分支
     */
    private fun rewriteInstallCard(
        context: Context,
        prefs: android.content.SharedPreferences,
        cardInfo: Any,
        ciCls: Class<*>,
        data: Any?,
        swaps: List<com.mo.fkLTY.engine.PackageSwap>,
        button: String?,
        left: String?,
        right: String?,
        title: String?,
        textEnabled: Boolean,
        iconSpec: String,
    ): Boolean {
        val blobs = listOfNotNull(
            title,
            str(ciCls, cardInfo, "getCardSubTitle"),
            str(ciCls, cardInfo, "getEventData"),
            str(ciCls, cardInfo, "getEventUri"),
            str(ciCls, cardInfo, "getEventMessage"),
            str(ciCls, cardInfo, "getEventType"),
            str(ciCls, cardInfo, "getIdentifyId"),
            data?.let { str(it.javaClass, it, "getText") },
        )

        for (swap in swaps) {
            // 严格匹配:卡片里必须出现源包名,避免把"安装别的 App"的卡也改掉
            if (blobs.none { it.contains(swap.from, ignoreCase = true) }) continue
            // 目标 App 没装就一个字都别改:改了文案却打不开,比不改更糟
            if (!BrowserAvailability.isAvailable(context, swap.to)) continue

            val label = appLabel(context, swap.to) ?: swap.to
            val subTitle = str(ciCls, cardInfo, "getCardSubTitle")
            val capsTitle = str(ciCls, cardInfo, "getCapsTitle")
            val capsDirect = str(ciCls, cardInfo, "getCapsDirectText")

            var changed = 0
            var newButton = "(未改)"
            var newTitle = "(未改)"
            var newSubTitle = "(未改)"
            var newCaps = "(未改)"

            if (textEnabled) {
                // 1.2.18：先查这条规则自己的文案，没有才回落全局默认；{app} 已替换成目标应用名。
                // 任一字段留空 = 该项不动，保留 metis 原文案。
                val text = RedirectPrefs.cardText(prefs, swap.from).resolve(label)
                newButton = text.button
                newTitle = text.title
                newSubTitle = text.subtitle
                newCaps = text.caps

                if (newButton.isNotBlank()) {
                    if (button != null) if (set(ciCls, cardInfo, "setButtonText", newButton)) changed++
                    if (left != null) if (set(ciCls, cardInfo, "setLeftButtonText", newButton)) changed++
                    if (right != null) if (set(ciCls, cardInfo, "setRightButtonText", newButton)) changed++
                }

                /*
                 * 标题 / 胶囊标题 / 胶囊直显文字:仍写着"安装"的才改。
                 * 不无条件覆盖的原因:metis 在不同场景会给这三处换成别的文案(如"打开查看"),
                 * 硬套自定义文字反而制造新的不一致。
                 */
                if (newTitle.isNotBlank() && title != null && title.contains("安装")) {
                    if (set(ciCls, cardInfo, "setCardTitle", newTitle)) changed++
                }
                if (newCaps.isNotBlank()) {
                    if (capsTitle != null && capsTitle.contains("安装")) {
                        if (set(ciCls, cardInfo, "setCapsTitle", newCaps)) changed++
                    }
                    if (capsDirect != null && capsDirect.contains("安装")) {
                        if (set(ciCls, cardInfo, "setCapsDirectText", newCaps)) changed++
                    }
                }
                // 副标题是"前往一加软件商店安装"这类诱导安装话术,目标 App 已装就该说清楚
                if (newSubTitle.isNotBlank() && subTitle != null && subTitle.contains("安装")) {
                    if (set(ciCls, cardInfo, "setCardSubTitle", newSubTitle)) changed++
                }
            }

            // 图标：与改字互不影响。`{app}` 在图标里展开成**包名**（app:// 后面要包名）。
            var iconNote = ""
            if (iconSpec.isNotEmpty()) {
                val resolvedIcon = iconSpec.replace(RedirectPrefs.PLACEHOLDER_APP, swap.to)
                val r = CardIconOverride.apply(context, cardInfo, ciCls, resolvedIcon, swap.to)
                iconNote = if (r != null) ", icon[$r]" else ", icon[失败: $resolvedIcon]"
            }

            HookLog.card(
                "seedling[build]: 引导安装卡改文案 fields=$changed " +
                    "(from=${swap.from} -> ${swap.to}; " +
                    "按钮「$button」->「$newButton」, 标题「$title」->「$newTitle」, " +
                    "副标题「$subTitle」->「$newSubTitle」, 胶囊「$capsTitle」)$iconNote",
            )
            return true
        }

        HookLog.card(
            "seedling[build]: 是安装卡但没匹配到源包名,未改; " +
                "title=$title, button=$button, blobs=${blobs.take(5)}",
        )
        return true
    }

    /**
     * 浏览器（网页 / 搜索）卡片的文案。
     *
     * 判据：能从卡片字段里解析出 http(s) 地址，或 `textType == 29`（文字搜索卡）。
     * 这是**用户主动开启**的功能，默认关闭——网页卡数量远多于安装卡，
     * 判据宽松一点就会改到不该改的卡上，所以宁可少改。
     *
     * 与安装卡不同：这里不要求原文案含"安装"，因为网页卡本来就没有那句话；
     * 但仍然遵守"字段留空 = 该项不改"。
     */
    private fun rewriteBrowserCard(
        context: Context,
        prefs: android.content.SharedPreferences,
        cardInfo: Any,
        ciCls: Class<*>,
    ) {
        val text = RedirectPrefs.browserCardText(prefs)
        if (text.isEmpty()) return

        val url = listOfNotNull(
            str(ciCls, cardInfo, "getEventUri"),
            str(ciCls, cardInfo, "getEventData"),
        ).firstNotNullOfOrNull { Policy.extractHttpUrl(it) }
        val textType = int(ciCls, cardInfo, "getTextType")
        if (url == null && textType != 29) return

        val host = url?.let { runCatching { java.net.URI(it).host }.getOrNull() }
        val browserLabel = prefs.getString(RedirectPrefs.KEY_TARGET, null)
            ?.let { appLabel(context, it) }
            ?: ""
        val resolved = text.resolve(appLabel = browserLabel, host = host)

        var changed = 0
        if (resolved.button.isNotBlank()) {
            if (set(ciCls, cardInfo, "setButtonText", resolved.button)) changed++
            if (set(ciCls, cardInfo, "setLeftButtonText", resolved.button)) changed++
            if (set(ciCls, cardInfo, "setRightButtonText", resolved.button)) changed++
        }
        if (resolved.title.isNotBlank() && set(ciCls, cardInfo, "setCardTitle", resolved.title)) changed++
        if (resolved.subtitle.isNotBlank() && set(ciCls, cardInfo, "setCardSubTitle", resolved.subtitle)) changed++
        if (resolved.caps.isNotBlank()) {
            if (set(ciCls, cardInfo, "setCapsTitle", resolved.caps)) changed++
            if (set(ciCls, cardInfo, "setCapsDirectText", resolved.caps)) changed++
        }

        HookLog.card(
            "seedling[build]: 网页卡改文案 fields=$changed (url=${url ?: "textType=29"}, host=$host, " +
                "标题「${str(ciCls, cardInfo, "getCardTitle")}」->「${resolved.title}」)",
        )
    }

    private fun appLabel(context: Context, pkg: String): String? = try {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getApplicationInfo(pkg, 0)
        context.packageManager.getApplicationLabel(info).toString()
    } catch (_: Throwable) {
        null
    }

    private fun str(cls: Class<*>, target: Any, getter: String): String? = try {
        cls.getMethod(getter).invoke(target) as? String
    } catch (_: Throwable) {
        null
    }

    private fun int(cls: Class<*>, target: Any, getter: String): Int? = try {
        (cls.getMethod(getter).invoke(target) as? Number)?.toInt()
    } catch (_: Throwable) {
        null
    }

    private fun set(cls: Class<*>, target: Any, setter: String, value: String): Boolean = try {
        cls.getMethod(setter, String::class.java).invoke(target, value)
        true
    } catch (_: Throwable) {
        false
    }

    private fun invoke(cls: Class<*>, target: Any, method: String): Any? = try {
        cls.getMethod(method).invoke(target)
    } catch (_: Throwable) {
        null
    }

    private fun readField(target: Any?, name: String): Any? {
        if (target == null) return null
        var c: Class<*>? = target.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(name)
                f.isAccessible = true
                return f.get(target)
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            } catch (_: Throwable) {
                return null
            }
        }
        return null
    }
}
