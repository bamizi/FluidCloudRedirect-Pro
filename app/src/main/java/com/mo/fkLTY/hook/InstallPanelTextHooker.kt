package com.mo.fkLTY.hook

import android.content.Context
import com.mo.fkLTY.engine.BrowserAvailability
import com.mo.fkLTY.engine.PackageSwapEngine
import com.mo.fkLTY.engine.RedirectPrefs
import io.github.libxposed.api.XposedInterface

/**
 * 「引导安装」卡片的**文案层** hook:把「去安装」改成「打开夸克网盘」,标题同理。
 *
 * 为什么用这个类而不是 Seedling.Builder:
 * 从真机 metis(`/product/priv-app/Metis/Metis.apk`,classes6.dex)反汇编得到
 * `com.oplus.textintent.distribution.ui.SingleAppInstallPanel`:
 * ```
 *   private final TextIntent textIntent;   getTextIntent(): TextIntent
 *   onPositiveText(): String               // 「去安装」按钮文字
 *   onTitle(): String                      // 「安装"夸克"查看」标题
 *   onPackageChanged(String, Z) / onInstallClick(Z) / onShow() / onViewCreate(View)
 * ```
 * 也就是**安装卡自己就是一个类**,文案由这两个方法直接给出,还能通过 `getTextIntent()`
 * 拿到完整的 TextIntent(packageName / intent.dataString)——
 * 判定"这张卡是不是我们配置的源 App"所需的信息全都在,比反推 Seedling 稳得多。
 *
 * 改动范围:只改返回的字符串,不动视图、不动点击逻辑;判定不命中一律原样返回。
 * 失败只记日志(PROTECTIVE),不影响宿主。
 */
class InstallPanelTextHooker(private val which: String) : XposedInterface.Hooker {

    override fun intercept(chain: XposedInterface.Chain): Any? {
        val original = chain.proceed()
        try {
            val text = original as? String ?: return original
            if (!text.contains("安装")) return original

            val panel = chain.thisObject ?: return original
            val textIntent = invokeNoArg(panel, "getTextIntent") ?: run {
                HookLog.once("panel-no-ti:$which", "install-panel[$which]: getTextIntent 为空,原样返回")
                return original
            }
            val reflected = ReflectedTextIntent(textIntent)
            val pkg = reflected.packageName
            val data = reflected.intent?.dataString

            val prefs = HookLog.prefs() ?: return original
            // 与 SeedlingLabelHooker 共用同一个开关（应用替换页里的「改卡片文字」）
            if (!prefs.getBoolean(RedirectPrefs.KEY_REWRITE_CARD_LABEL, true)) return original
            val swap = PackageSwapEngine.load(prefs).firstOrNull { s ->
                s.enabled && s.from != s.to &&
                    (pkg == s.from || (data != null && data.contains(s.from, ignoreCase = true)))
            } ?: run {
                HookLog.once(
                    "panel-nomatch:$which:$pkg",
                    "install-panel[$which]: 未匹配到源包名(pkg=$pkg, data=$data), 原文=$text",
                )
                return original
            }

            val context = AppContext.get()
            if (context != null && !BrowserAvailability.isAvailable(context, swap.to)) return original
            val label = context?.let { appLabel(it, swap.to) } ?: swap.to
            // 与 SeedlingLabelHooker 共用同一份自定义文案；留空则不改
            val text = RedirectPrefs.cardText(prefs).resolve(label)
            val newText = text.button.takeIf { it.isNotBlank() }
                ?: text.title.takeIf { it.isNotBlank() }
                ?: return original
            HookLog.card("install-panel[$which]: 「$text」-> 「$newText」 (from=${swap.from}, pkg=$pkg)")
            return newText
        } catch (t: Throwable) {
            HookLog.once("err:panel:$which", "InstallPanelTextHooker($which) error: ${t.javaClass.simpleName}: ${t.message}")
        }
        return original
    }

    private fun invokeNoArg(target: Any, name: String): Any? = try {
        target.javaClass.getMethod(name).invoke(target)
    } catch (_: Throwable) {
        null
    }

    private fun appLabel(context: Context, pkg: String): String? = try {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getApplicationInfo(pkg, 0)
        context.packageManager.getApplicationLabel(info).toString()
    } catch (_: Throwable) {
        null
    }
}

/** 在 hook 进程里取宿主 Application 的 Context(不依赖调用方传参) */
internal object AppContext {
    private val cached: Context? by lazy {
        try {
            val cls = Class.forName("android.app.ActivityThread")
            val app = cls.getMethod("currentApplication").invoke(null)
            app as? Context
        } catch (_: Throwable) {
            null
        }
    }

    fun get(): Context? = cached
}
