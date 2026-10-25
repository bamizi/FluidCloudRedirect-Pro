package com.mo.fkLTY.hook

import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 模块入口,由 META-INF/xposed/java_init.list 按名加载。
 *
 * 进程过滤:仅 com.oplus.metis 的 :text_intent 子进程挂钩(02 号文档 §1.1——
 * TextIntentWorkerService / RouterActivity / TextIntentActionProvider 等 10 个组件全在该子进程)。
 *
 * hook 布局(03 号文档 §4.4):
 *  A 主:TextIntent.invoke(Context)                 —— 行为咽喉
 *  B 冗余:SchemeData/WebData.parseIntent(Context)  —— packageName 产生点,卡片 UI 同步变对
 *  C 监控:ZoomOpenHandler.startByZoom(仅记录)     —— 未来旁路预警
 *  D 兜底:ContextImpl.startActivity                —— metis 自己现场发出跳转时
 *
 *  注:1.2.6 曾额外挂钩 PendingIntent.getActivity 覆盖"点击由卡片宿主代发"的情形,
 *  1.2.7 已移除。原因见 CHANGELOG:它就地修改调用方传入的 Intent,而该对象可能被 metis
 *  缓存复用,改坏后表现为"卡片点击完全没反应",比跳错地方更难排查。
 */
class MainHook : XposedModule() {

    /**
     * 真实进程名以 onModuleLoaded(param).processName 为准。
     * ⚠️ 不能用 PackageLoadedParam.applicationInfo.processName 判断子进程:
     * 该 appInfo 处于 PM 层,processName 是 "com.oplus.metis",不带 ":text_intent" 后缀,
     * 会把所有包生命周期回调全部误滤掉(1.0.0 真机踩坑)。
     */
    @Volatile
    private var loadedProcessName: String? = null

    /** onPackageLoaded / onPackageReady 双回调兜底,只装一次 */
    private val installed = AtomicBoolean(false)

    /** 兜底启动 hook(ContextImpl.startActivity),在所有 metis 进程都装一次 */
    private val installedLaunch = AtomicBoolean(false)

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        HookLog.bind(this)
        loadedProcessName = param.processName
        HookLog.log("loaded in ${param.processName}")
        if (param.processName.endsWith(TARGET_PROCESS_SUFFIX)) {
            HookLog.log("target process detected; waiting for package lifecycle to install hooks")
        }
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        handlePackageLifecycle(param, phase = "onPackageLoaded")
    }

    /** 兜底:个别框架实现对 scope 应用自身只派发 PackageReady */
    override fun onPackageReady(param: PackageReadyParam) {
        handlePackageLifecycle(param, phase = "onPackageReady")
    }

    private fun handlePackageLifecycle(param: PackageLoadedParam, phase: String) {
        try {
            val pkg = param.packageName
            val procName = loadedProcessName ?: param.applicationInfo.processName
            // 诊断日志:任何包生命周期回调都留痕,杜绝静默漏挂
            HookLog.once("lc:$phase:$pkg", "package lifecycle $phase: package=$pkg, process=$procName")

            if (pkg != TARGET_PACKAGE) return
            if (procName == null || !procName.startsWith(TARGET_PACKAGE)) return
            val classLoader = param.getDefaultClassLoader()

            // 兜底层:任何 metis 进程都装上,覆盖"卡片不走 TextIntent.invoke"的分支
            if (installedLaunch.compareAndSet(false, true)) {
                installLaunchHook(classLoader)
            }

            if (!procName.endsWith(TARGET_PROCESS_SUFFIX)) return
            if (!installed.compareAndSet(false, true)) return

            HookLog.log("matched target process via $phase; installing hooks")
            installHooks(classLoader)
        } catch (t: Throwable) {
            HookLog.log("lifecycle error in $phase: ${t.javaClass.simpleName}: ${t.message}", t)
        }
    }

    /**
     * 兜底钩 `ContextImpl.startActivity`:实测本机型卡片不经过 TextIntent.invoke,
     * 因此在"真正发出跳转"这一步再兜一次。只记录 + 命中规则才改写。
     */
    private fun installLaunchHook(classLoader: ClassLoader) {
        val cls = try {
            Class.forName(CLASS_CONTEXT_IMPL, false, classLoader)
        } catch (t: Throwable) {
            HookLog.once("fail:ctximpl", "Cannot load ContextImpl: ${t.javaClass.simpleName}: ${t.message}")
            return
        }
        var installedCount = 0
        val signatures = listOf(
            arrayOf<Class<*>>(Intent::class.java),
            arrayOf<Class<*>>(Intent::class.java, Bundle::class.java),
        )
        signatures.forEachIndexed { index, sig ->
            try {
                val method = cls.getDeclaredMethod("startActivity", *sig)
                hook(method)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(StartActivityHooker(if (index == 0) "launch" else "launchOpts"))
                installedCount++
            } catch (t: Throwable) {
                HookLog.once(
                    "fail:launch$index",
                    "Cannot hook ContextImpl.startActivity/${sig.size}: ${t.javaClass.simpleName}: ${t.message}",
                )
            }
        }
        if (installedCount > 0) {
            HookLog.log("Hook installed: ContextImpl.startActivity ($installedCount overloads)")
        }
    }

    private fun installHooks(classLoader: ClassLoader) {
        installInvokeHook(classLoader)
        installParseIntentHooks(classLoader)
        installMonitorHook(classLoader)
        installSeedlingLabelHook(classLoader)
    }

    /**
     * E 显示层 hook:`Seedling.Builder.build()` —— 卡片构建完成、尚未交给 SystemUI 时改按钮文案。
     *
     * 为什么要这一层:卡片由 `Seedling.Builder` 构建后交给 SystemUI 的 SeedlingCard 渲染,
     * 与点击走的 Intent 是两条路。改 Intent 只能改"点了去哪",改不了卡上写的字。
     * 具体符号(class/method)已从本机 metis APK 的 dex 里核对过,见 SeedlingLabelHooker 的注释。
     */
    private fun installSeedlingLabelHook(classLoader: ClassLoader) {
        try {
            val cls = classLoader.loadClass("com.oplus.textintent.pantanal.Seedling\$Builder")
            val method = cls.getDeclaredMethod("build")
            hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(SeedlingLabelHooker())
            HookLog.once("installed:seedling", "Hook installed: Seedling\$Builder.build()")
        } catch (t: Throwable) {
            HookLog.once(
                "fail:seedling",
                "Cannot hook Seedling\$Builder.build: ${t.javaClass.simpleName}: ${t.message}",
            )
        }
    }

    /** A 主 hook:TextIntent.invoke(Context) */
    private fun installInvokeHook(classLoader: ClassLoader) {
        try {
            val cls = classLoader.loadClass(CLASS_TEXT_INTENT)
            val method = cls.getDeclaredMethod("invoke", Context::class.java)
            hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(RedirectHooker())
            HookLog.once("installed:invoke", "Hook installed: TextIntent.invoke(Context)")
        } catch (t: Throwable) {
            HookLog.once("fail:invoke", "Cannot hook TextIntent.invoke: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** B 冗余 hook:SchemeData.parseIntent / WebData.parseIntent(签名均为 public (Context) -> TextIntent) */
    private fun installParseIntentHooks(classLoader: ClassLoader) {
        for (name in PARSE_INTENT_CLASSES) {
            try {
                val cls = classLoader.loadClass(name)
                val method = cls.getDeclaredMethod("parseIntent", Context::class.java)
                hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(ParseIntentHooker())
                HookLog.once("installed:$name", "Hook installed: ${cls.simpleName}.parseIntent(Context)")
            } catch (t: Throwable) {
                HookLog.once("fail:$name", "Cannot hook $name.parseIntent: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /** C 监控 hook:ZoomOpenHandler.startByZoom(Context, Intent, Bundle, String),仅记录 */
    private fun installMonitorHook(classLoader: ClassLoader) {
        try {
            val cls = classLoader.loadClass(CLASS_ZOOM_HANDLER)
            val method = cls.getDeclaredMethod(
                "startByZoom",
                Context::class.java, Intent::class.java, Bundle::class.java, String::class.java,
            )
            hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(MonitorHooker())
            HookLog.once("installed:monitor", "Hook installed: ZoomOpenHandler.startByZoom (monitor only)")
        } catch (t: Throwable) {
            HookLog.once("fail:monitor", "Cannot hook ZoomOpenHandler.startByZoom: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    companion object {
        const val TARGET_PACKAGE = "com.oplus.metis"
        const val TARGET_PROCESS_SUFFIX = ":text_intent"

        private const val CLASS_TEXT_INTENT = "com.oplus.textintent.distribution.bean.TextIntent"
        private const val CLASS_ZOOM_HANDLER = "com.oplus.textintent.common.utils.ZoomOpenHandler"
        private const val CLASS_CONTEXT_IMPL = "android.app.ContextImpl"
        private val PARSE_INTENT_CLASSES = arrayOf(
            "com.oplus.textintent.distribution.bean.SchemeData",
            "com.oplus.textintent.distribution.bean.WebData",
        )
    }
}
