package com.mo.fkLTY.hook

import android.content.Intent
import com.mo.fkLTY.engine.RedirectEngine
import io.github.libxposed.api.XposedInterface

/**
 * 兜底 hook:直接拦 `ContextImpl.startActivity`。
 *
 * 为什么要这层:实测取证(1.1.3 日志)显示,本机型上流体云的卡片**根本没有走**
 * `TextIntent.invoke` —— 四个原 hook 全部装上了,但日志里没有任何一次改写记录,
 * 卡片点击走的是别的路径。与其继续猜是哪条路径,不如在"metis 真正发出跳转"的那一步
 * 兜住:凡是带 http(s) / heytapbrowser:// 数据的 startActivity,都过一遍同一套规则。
 *
 * 安全性:只在命中规则时改写,命中不了一律原样放行;异常全部吞掉,绝不影响宿主。
 */
class StartActivityHooker(private val source: String) : XposedInterface.Hooker {

    override fun intercept(chain: XposedInterface.Chain): Any? {
        try {
            val intent = chain.getArg(0) as? Intent
            val context = chain.thisObject as? android.content.Context
            // 留痕：这条兜底链路被调到没有，是判断"卡片点击到底走哪条路"的关键。
            // 配额 40 次（HookLog.card），排查期够用，日常也不会刷屏。
            if (intent != null) {
                HookLog.card(
                    "startActivity reached[$source]: pkg=${intent.component?.packageName ?: "-"}" +
                        " action=${intent.action} data=${intent.dataString}",
                )
            }
            // 具体要不要处理由 RedirectEngine 内部判定(带网页数据 / 目标包在替换表里),
            // 这里不做前置过滤,否则会漏掉"没有网址只有目标包"的口令卡片。
            if (intent != null && context != null) {
                RedirectEngine.applyToIntent(context, intent, source)
            }
        } catch (t: Throwable) {
            HookLog.once("err:startActivity", "StartActivityHooker error: ${t.javaClass.simpleName}: ${t.message}")
        }
        return chain.proceed()
    }
}
