package com.mo.fkLTY.hook

import android.content.Context
import com.mo.fkLTY.engine.BrowserAvailability
import com.mo.fkLTY.engine.PackageSwapEngine
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
            HookLog.once(
                "err:seedling",
                "SeedlingLabelHooker error: ${t.javaClass.simpleName}: ${t.message}",
            )
        }
        return seedling
    }

    private fun rewrite(builder: Any?, seedling: Any?) {
        // 进过一次就留痕:用来判断 build() 这条路径到底有没有被走到
        HookLog.once("seedling-entered", "seedling[build]: entered")
        if (seedling == null) return
        val prefs = HookLog.prefs() ?: return
        val swaps = PackageSwapEngine.load(prefs).filter { it.enabled && it.from != it.to }
        if (swaps.isEmpty()) return

        val context = readField(builder, "context") as? Context ?: return

        val sCls = seedling.javaClass
        val cardInfo = invoke(sCls, seedling, "getCardInfo") ?: return
        val data = invoke(sCls, seedling, "getData")
        val ciCls = cardInfo.javaClass

        val button = str(ciCls, cardInfo, "getButtonText")
        val left = str(ciCls, cardInfo, "getLeftButtonText")
        val right = str(ciCls, cardInfo, "getRightButtonText")
        val title = str(ciCls, cardInfo, "getCardTitle")

        // 只碰"引导安装"这类卡片,普通卡片一个字都不动
        val looksInstall = listOfNotNull(button, left, right, title).any { it.contains("安装") }
        if (!looksInstall) return

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
            // 1.2.14:按用户要求去掉"查看"与引号,标题与按钮同文,副标题独立一句
            val openText = "打开$label"
            val newButton = openText
            val newTitle = openText
            val newSubTitle = "点击$openText"

            var changed = 0
            if (button != null) if (set(ciCls, cardInfo, "setButtonText", newButton)) changed++
            if (left != null) if (set(ciCls, cardInfo, "setLeftButtonText", newButton)) changed++
            if (right != null) if (set(ciCls, cardInfo, "setRightButtonText", newButton)) changed++

            /*
             * 标题 / 胶囊标题 / 胶囊直显文字:仍写着"安装"的才改。
             * 不无条件覆盖的原因:metis 在不同场景会给这三处换成别的文案(如"打开查看"),
             * 硬套成"打开X查看"反而制造新的不一致。
             */
            if (title != null && title.contains("安装")) {
                if (set(ciCls, cardInfo, "setCardTitle", newTitle)) changed++
            }
            val capsTitle = str(ciCls, cardInfo, "getCapsTitle")
            if (capsTitle != null && capsTitle.contains("安装")) {
                if (set(ciCls, cardInfo, "setCapsTitle", newTitle)) changed++
            }
            val capsDirect = str(ciCls, cardInfo, "getCapsDirectText")
            if (capsDirect != null && capsDirect.contains("安装")) {
                if (set(ciCls, cardInfo, "setCapsDirectText", newTitle)) changed++
            }
            // 副标题是"前往一加软件商店安装"这类诱导安装话术,目标 App 已装就该说清楚
            val subTitle = str(ciCls, cardInfo, "getCardSubTitle")
            if (subTitle != null && subTitle.contains("安装")) {
                if (set(ciCls, cardInfo, "setCardSubTitle", newSubTitle)) changed++
            }

            HookLog.card(
                "seedling[build]: 引导安装卡改文案 fields=$changed " +
                    "(from=${swap.from} -> ${swap.to}; " +
                    "按钮「$button」->「$newButton」, 标题「$title」->「$newTitle」, " +
                    "副标题「$subTitle」->「$newSubTitle」, 胶囊「$capsTitle」)",
            )
            return
        }

        HookLog.card(
            "seedling[build]: 是安装卡但没匹配到源包名,未改; " +
                "title=$title, button=$button, blobs=${blobs.take(5)}",
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
