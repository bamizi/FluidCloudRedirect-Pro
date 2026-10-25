package com.mo.fkLTY.service

import android.content.SharedPreferences
import com.mo.fkLTY.engine.RedirectPrefs
import com.mo.fkLTY.engine.PackageSwap
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 设置 App 端与 LSPosed 框架的桥:
 * - 通过 XposedServiceHelper 拿到 XposedService,才能写远程偏好(hook 端只读);
 * - service.scope 查询作用域,requestScope() 一键申请(03 号文档 §4.5);
 * - 服务能绑定本身就说明模块在 LSPosed 中已启用——UI 状态卡据此推断"是否生效"。
 */
object PrefsBridge {

    data class State(
        val service: XposedService? = null,
        val frameworkLabel: String? = null,
        val scope: Set<String> = emptySet(),
        val prefs: SharedPreferences? = null,
    ) {
        val bound: Boolean get() = service != null
        val metisInScope: Boolean get() = TARGET_PACKAGE in scope
    }

    private const val TARGET_PACKAGE = "com.oplus.metis"

    @Volatile
    private var isRegistered = false

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Activity.onCreate 调用;框架要求 listener 只注册一次,内部幂等 */
    fun init() {
        if (isRegistered) return
        isRegistered = true
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                refresh(service)
            }

            override fun onServiceDied(service: XposedService) {
                _state.update { current ->
                    if (current.service === service) State() else current
                }
            }
        })
    }

    private fun refresh(service: XposedService) {
        val framework = try {
            "${service.frameworkName} ${service.frameworkVersion}"
        } catch (_: Throwable) {
            null
        }
        val scope = try {
            service.scope.toSet()
        } catch (_: Throwable) {
            emptySet()
        }
        val prefs = try {
            service.getRemotePreferences(RedirectPrefs.GROUP)
        } catch (_: Throwable) {
            null
        }
        _state.value = State(service, framework, scope, prefs)
    }

    /** 一键申请智慧决策服务作用域;结果回调运行在 Binder 线程 */
    fun requestScope(onResult: (approved: Boolean, message: String?) -> Unit = { _, _ -> }) {
        val service = _state.value.service ?: run {
            onResult(false, "service not bound")
            return
        }
        service.requestScope(
            listOf(TARGET_PACKAGE),
            object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: List<String>) {
                    refresh(service)
                    onResult(true, null)
                }

                override fun onScopeRequestFailed(message: String) {
                    onResult(false, message)
                }
            },
        )
    }

    /** 写目标浏览器;null = 禁用重定向(沿用旧模块"列表头禁用项"语义) */
    fun saveTarget(pkg: String?): Boolean {
        val prefs = _state.value.prefs ?: return false
        val editor = prefs.edit()
        if (pkg == null) editor.remove(RedirectPrefs.KEY_TARGET) else editor.putString(RedirectPrefs.KEY_TARGET, pkg)
        return editor.commit()
    }

    fun saveRule(key: String, value: Boolean): Boolean {
        val prefs = _state.value.prefs ?: return false
        return prefs.edit().putBoolean(key, value).commit()
    }

    fun savePackageSwaps(swaps: List<PackageSwap>): Boolean {
        val prefs = _state.value.prefs ?: return false
        return prefs.edit().putString(RedirectPrefs.KEY_PACKAGE_SWAPS, PackageSwap.encodeAll(swaps)).commit()
    }

    fun saveVerboseLog(value: Boolean): Boolean = saveRule(RedirectPrefs.KEY_VERBOSE_LOG, value)

    /** 保存自定义卡片文字(四个字段一起写,commit 一次) */
    fun saveCardText(text: com.mo.fkLTY.engine.CardText): Boolean {
        val prefs = _state.value.prefs ?: return false
        return prefs.edit()
            .putString(RedirectPrefs.KEY_CARD_TEXT_TITLE, text.title)
            .putString(RedirectPrefs.KEY_CARD_TEXT_SUBTITLE, text.subtitle)
            .putString(RedirectPrefs.KEY_CARD_TEXT_BUTTON, text.button)
            .putString(RedirectPrefs.KEY_CARD_TEXT_CAPS, text.caps)
            .commit()
    }
}
