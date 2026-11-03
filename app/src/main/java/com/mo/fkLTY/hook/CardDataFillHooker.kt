package com.mo.fkLTY.hook

import android.content.Context
import io.github.libxposed.api.XposedInterface
import org.json.JSONObject

/**
 * 卡片数据补位：在 metis 没有下发卡片内容时，主动补一次 `updateData`。
 *
 * ## 为什么需要这个
 *
 * 2026-11-03 真机排查发现：复制夸克口令后，整条链路只差最后一步 ——
 *
 * ```
 * 识别    TokenRegexMatcher hits=1                        ✅
 * 建卡    onCardCreate / subscribed                       ✅
 * 投递    seedling_card_load result=Success               ✅
 * 数据    loadPage uiData is null                         ❌  ← 卡片是空壳，看不见
 * ```
 *
 * 反编译 metis 的 `TextSeedlingCardWidgetNewProvider.call()` 后确认它只分发三种 method：
 *
 * ```
 * "updateData"            → updateData(card, businessData, cardOptions)   ← 卡片内容
 * "updateIntelligentData" → 转 PantanalIntent 交给排序算法                ← 只有 intent_id
 * 其他                    → super.call()
 * ```
 *
 * 而整段日志里 `call method: updateData` 只出现过 1 次，且不属于 text_intent 链路；
 * `updateIntelligentData` 每次复制都调、businessData 里只有 `intent_id`。
 *
 * 结论：**metis 只发了「意图事件」，从来没发「卡片内容」**，于是 SystemUI 拿到的
 * `uiData` 永远是 null，模板里 `{{cardTitle}}` 这些变量没人填，渲染出来就是透明卡片。
 *
 * ## 这个 hook 做什么
 *
 * 挂在 `TextSeedlingCardWidgetNewProvider.onCardCreate(Context, SeedlingCard)` 之后，
 * 如果发现是本模块关心的卡片（serviceId 命中 text_intent），就用一份按
 * `pages/index.json` 的 `data` 字段构造的 JSON，补调一次父类的
 * `updateData(SeedlingCard, JSONObject, SeedlingCardOptions)`。
 *
 * ## 字段来源
 *
 * 字段名直接取自 UPK 的 `pages/index.json`（真机 `/data/user_de/0/com.android.systemui/
 * files/upk/<hash>/2000038/pages/index.json` 已核对）：
 *
 * ```
 * cardTitle cardSubTitle capsTitle capsDirectText
 * cardIcon capsIcon capsDirectIcon leadingIcon
 * enableButton buttonText showCardIcon showCardSubTitle
 * eventType eventUri eventData
 * ```
 *
 * ## 安全性
 *
 * - 只对 `serviceId = 536879167`（文本意图卡）生效，不碰其他流体云卡片
 * - 开关 `KEY_CARD_DATA_FILL` 默认开，用户可关
 * - 任何一步反射失败都只记日志并原样放行，绝不影响宿主
 * - 不修改任何已有数据，只在卡片内容为空时补位
 */
class CardDataFillHooker : XposedInterface.Hooker {

    override fun intercept(chain: XposedInterface.Chain): Any? {
        val result = chain.proceed()
        try {
            fill(chain.thisObject, chain.getArg(1))
        } catch (t: Throwable) {
            HookLog.card("CardDataFill 异常: ${t.javaClass.simpleName}: ${t.message}")
        }
        return result
    }

    private fun fill(provider: Any?, card: Any?) {
        if (provider == null || card == null) return

        val serviceId = callString(card, "getServiceId")
        val uniqueKey = callString(card, "getCardUniqueKey")
        HookLog.card("datafill: onCardCreate serviceId=$serviceId key=$uniqueKey")
        if (serviceId != TEXT_INTENT_SERVICE_ID) return

        val prefs = HookLog.prefs()
        if (prefs != null && !prefs.getBoolean(KEY_CARD_DATA_FILL, true)) {
            HookLog.card("datafill: 开关关闭，跳过")
            return
        }

        val payload = buildPayload(card)
        HookLog.card("datafill: 构造数据 ${payload.toString().take(220)}")

        // updateData / updateAllCardData 都在父类 SeedlingCardWidgetProvider 上，
        // 参数都是 (SeedlingCard, JSONObject, SeedlingCardOptions)。
        val invoked = tryInvoke(provider, "updateData", card, payload) ||
            tryInvoke(provider, "updateAllCardData", card, payload)
        HookLog.card(
            if (invoked) "datafill: 已调用 updateData，等待 SystemUI 侧 loadPage"
            else "datafill: 找不到可用的 updateData/updateAllCardData，放弃",
        )
    }

    /** 按 pages/index.json 的 data 字段构造。未列出的字段走模板默认值。 */
    private fun buildPayload(card: Any): JSONObject {
        val title = "口令"
        val sub = "点击打开"
        val button = "打开"
        return JSONObject().apply {
            // 胶囊（状态栏那一小条）
            put("capsTitle", title)
            put("capsDirectText", button)
            put("capsDirectShowIcon", true)
            put("capsIcon", ICON_LOGO)
            put("capsIconShape", "round-rectangle")
            put("capsDirectIcon", ICON_RIGHT)
            // 展开态卡片
            put("cardTitle", title)
            put("cardSubTitle", sub)
            put("showCardSubTitle", true)
            put("showCardIcon", true)
            put("cardIcon", ICON_LOGO)
            put("cardIconShape", "round-rectangle")
            // 按钮
            put("enableButton", true)
            put("buttonText", button)
            put("buttonIcon", "")
            put("buttonColor", "")
            put("buttonTextColor", "")
            // 首尾
            put("showLeading", false)
            put("showTrailing", false)
            put("showLeftButton", false)
            put("leftButtonText", "")
            put("rightButtonText", "")
            put("leadingTitle", "")
            // 点击事件：沿用模板默认值，实际跳转由 TextIntentActionProvider 按 identifyId 解析
            put("eventType", "deeplink")
            put("eventUri", "com.oplus.metis.provider.TextIntentActionProvider")
            put("eventData", "ti://com.oplus.textintent")
            put("eventMessage", "")
        }
    }

    /**
     * 在 provider 及其父类上按名字 + 参数个数找方法并调用。
     * metis 里这两个方法参数都是 (SeedlingCard, JSONObject, SeedlingCardOptions)，
     * 第三个参数传 null —— 卡片侧不依赖它做决策，缺失时走默认配置。
     */
    private fun tryInvoke(provider: Any, name: String, card: Any, payload: JSONObject): Boolean {
        var cls: Class<*>? = provider.javaClass
        while (cls != null) {
            for (m in cls.declaredMethods) {
                // updateData / updateAllCardData 都返回 void、都吃 3 个参数；
                // 其他同名重载一律跳过
                if (m.name != name || m.parameterTypes.size != 3) continue
                if (m.returnType != Void.TYPE) continue
                return try {
                    m.isAccessible = true
                    m.invoke(provider, card, payload, null)
                    HookLog.card("datafill: $name 调用成功 (${cls.name})")
                    true
                } catch (t: Throwable) {
                    val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
                    HookLog.card("datafill: $name 调用失败 ${cause.javaClass.simpleName}: ${cause.message}")
                    false
                }
            }
            cls = cls.superclass
        }
        return false
    }

    private fun callString(target: Any, getter: String): String? = try {
        target.javaClass.getMethod(getter).invoke(target) as? String
    } catch (_: Throwable) {
        null
    }

    companion object {
        /** 文本意图卡片的固定 serviceId，来自 UPK config.json 的 identifier.id */
        const val TEXT_INTENT_SERVICE_ID = "536879167"

        /** 卡片数据补位开关（默认开） */
        const val KEY_CARD_DATA_FILL = "card_data_fill_enabled"

        private const val ICON_LOGO = "assets/images/general_logo.png"
        private const val ICON_RIGHT = "assets/images/caps_right_icon.png"
    }
}
