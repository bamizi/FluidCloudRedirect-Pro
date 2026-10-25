package com.mo.fkLTY.hook

import android.content.Context
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
 *   setButtonText / setLeftButtonText / setRightButtonText / setCardTitle(String)
 * ```
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
            val label = appLabel(context, swap.to) ?: swap.to
            val newText = "打开$label"
            var changed = 0
            if (button != null) if (set(ciCls, cardInfo, "setButtonText", newText)) changed++
            if (left != null) if (set(ciCls, cardInfo, "setLeftButtonText", newText)) changed++
            if (right != null) if (set(ciCls, cardInfo, "setRightButtonText", newText)) changed++
            HookLog.card(
                "seedling[build]: 引导安装卡改文案 -> $newText " +
                    "(from=${swap.from}, fields=$changed, 原按钮=$button, 原标题=$title)",
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
