package com.mo.fkLTY.ui.picker

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mo.fkLTY.R
import com.mo.fkLTY.engine.RedirectPrefs
import com.mo.fkLTY.engine.CardText
import com.mo.fkLTY.engine.PackageSwap
import com.mo.fkLTY.service.PrefsBridge
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主屏状态:浏览器枚举/排序/卸载校验(01 号文档 §3.2 行为定义)+
 * 远程偏好读写经 PrefsBridge(模块未激活时写失败给反馈,不再被动 toast)。
 */
class PickerViewModel(app: Application) : AndroidViewModel(app) {

    data class BrowserEntry(
        val label: String,
        val pkg: String,
        val icon: Drawable?,
    )

    data class UiMessage(val res: Int, val arg: String? = null)

    data class UiState(
        val browsers: List<BrowserEntry> = emptyList(),
        val installedApps: List<BrowserEntry> = emptyList(),
        val loadingBrowsers: Boolean = true,
        val target: String? = null,
        val bound: Boolean = false,
        val metisInScope: Boolean = false,
        val framework: String? = null,
        val rules: Map<String, Boolean> = emptyMap(),
        val packageSwaps: List<PackageSwap> = emptyList(),
        val cardText: CardText = CardText(),
        val message: UiMessage? = null,
    )

    private val _ui = MutableStateFlow(UiState(rules = defaultRules(null)))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            PrefsBridge.state.collect { st ->
                val prefs = st.prefs
                _ui.update { current ->
                    // 已绑定时偏好值是权威来源(含被清空的情况);未绑定时保留当前值
                    val remoteTarget = if (st.bound && prefs != null) {
                        prefs.getString(RedirectPrefs.KEY_TARGET, null)?.trim()?.takeIf { it.isNotEmpty() }
                    } else {
                        current.target
                    }
                    current.copy(
                        bound = st.bound,
                        metisInScope = st.metisInScope,
                        framework = st.frameworkLabel,
                        target = remoteTarget,
                        rules = defaultRules(prefs),
                        packageSwaps = PackageSwap.decodeAll(prefs?.getString(RedirectPrefs.KEY_PACKAGE_SWAPS, null)),
                        // 未绑定时保留当前值，避免界面被清空
                        cardText = prefs?.let { RedirectPrefs.cardText(it) } ?: current.cardText,
                    )
                }
                validateTarget()
            }
        }
        loadBrowsers()
        loadInstalledApps()
    }

    /** 浏览器枚举:VIEW(https + BROWSABLE, MATCH_DEFAULT_ONLY)∪ WEB_SEARCH,Collator 本地化排序,按包名去重 */
    private fun loadBrowsers() {
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { queryBrowsers() }
            _ui.update { it.copy(browsers = entries, loadingBrowsers = false) }
            validateTarget()
        }
    }

    fun refresh() {
        _ui.update { it.copy(loadingBrowsers = true) }
        loadBrowsers()
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { queryLaunchableApps() }
            _ui.update { it.copy(installedApps = entries) }
        }
    }

    /** 选择目标浏览器;null = 禁用重定向(列表头禁用项) */
    fun select(pkg: String?) {
        val ok = PrefsBridge.saveTarget(pkg)
        if (!ok) {
            _ui.update { it.copy(message = UiMessage(R.string.save_failed)) }
            return
        }
        _ui.update { current ->
            current.copy(
                target = pkg,
                message = pkg?.let {
                    UiMessage(R.string.selection_saved, labelOf(pkg))
                },
            )
        }
    }

    fun toggleRule(key: String, value: Boolean) {
        if (PrefsBridge.saveRule(key, value)) {
            _ui.update { it.copy(rules = it.rules + (key to value)) }
        } else {
            _ui.update { it.copy(message = UiMessage(R.string.save_failed)) }
        }
    }

    fun savePackageSwaps(swaps: List<PackageSwap>) {
        if (PrefsBridge.savePackageSwaps(swaps)) {
            _ui.update { it.copy(packageSwaps = swaps) }
        } else {
            _ui.update { it.copy(message = UiMessage(R.string.save_failed)) }
        }
    }

    /** 自定义卡片文字：一次性写四个字段 */
    fun saveCardText(text: CardText) {
        if (PrefsBridge.saveCardText(text)) {
            _ui.update { it.copy(cardText = text, message = UiMessage(R.string.card_text_saved)) }
        } else {
            _ui.update { it.copy(message = UiMessage(R.string.save_failed)) }
        }
    }

    fun requestScope() {
        PrefsBridge.requestScope { approved, message ->
            viewModelScope.launch {
                _ui.update {
                    it.copy(
                        message = if (approved) {
                            UiMessage(R.string.request_scope_approved)
                        } else {
                            UiMessage(R.string.request_scope_failed, message ?: "")
                        },
                    )
                }
            }
        }
    }

    fun consumeMessage() {
        _ui.update { it.copy(message = null) }
    }

    /** 已保存目标被卸载时自动清除(旧模块 onResume 校验行为) */
    private fun validateTarget() {
        val state = _ui.value
        val target = state.target ?: return
        if (state.browsers.isEmpty()) return
        if (state.browsers.none { it.pkg == target }) {
            PrefsBridge.saveTarget(null)
            _ui.update { it.copy(target = null, message = UiMessage(R.string.target_uninstalled)) }
        }
    }

    private fun labelOf(pkg: String): String =
        _ui.value.browsers.firstOrNull { it.pkg == pkg }?.label ?: pkg

    private fun defaultRules(prefs: android.content.SharedPreferences?): Map<String, Boolean> = mapOf(
        RedirectPrefs.KEY_RULE_PROTOCOL to (prefs?.getBoolean(RedirectPrefs.KEY_RULE_PROTOCOL, true) ?: true),
        RedirectPrefs.KEY_RULE_TEXT_SEARCH to (prefs?.getBoolean(RedirectPrefs.KEY_RULE_TEXT_SEARCH, true) ?: true),
        RedirectPrefs.KEY_RULE_HEYTAP_HTTP to (prefs?.getBoolean(RedirectPrefs.KEY_RULE_HEYTAP_HTTP, true) ?: true),
        RedirectPrefs.KEY_RULE_BROWSER_CARD to (prefs?.getBoolean(RedirectPrefs.KEY_RULE_BROWSER_CARD, false) ?: false),
        RedirectPrefs.KEY_VERBOSE_LOG to (prefs?.getBoolean(RedirectPrefs.KEY_VERBOSE_LOG, false) ?: false),
        // 卡片改字：默认开（改字是为了让卡片和实际行为一致）
        RedirectPrefs.KEY_REWRITE_CARD_LABEL to
            (prefs?.getBoolean(RedirectPrefs.KEY_REWRITE_CARD_LABEL, true) ?: true),
    )

    /** 可启动应用列表，用于规则编辑器选目标；无桌面入口的应用仍可手动填写包名。 */
    private fun queryLaunchableApps(): List<BrowserEntry> {
        val context = getApplication<Application>()
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val byPackage = LinkedHashMap<String, ResolveInfo>()
        // 桌面应用：不加 MATCH_DEFAULT_ONLY，否则大部分应用查不出来（见 query 的注释）
        for (ri in query(pm, launcher, flags = 0)) {
            val pkg = ri.activityInfo?.packageName ?: continue
            byPackage.putIfAbsent(pkg, ri)
        }
        val collator = Collator.getInstance()
        return byPackage.map { (pkg, ri) ->
            val label = runCatching { ri.loadLabel(pm)?.toString()?.trim().takeIf { !it.isNullOrEmpty() } ?: pkg }.getOrDefault(pkg)
            val icon = runCatching { ri.loadIcon(pm) }.getOrNull()
            BrowserEntry(label, pkg, icon)
        }.sortedWith(compareBy(collator) { it.label })
    }

    private fun queryBrowsers(): List<BrowserEntry> {
        val context = getApplication<Application>()
        val pm = context.packageManager

        val viewIntent = Intent(Intent.ACTION_VIEW)
            .setData(Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val webSearchIntent = Intent(Intent.ACTION_WEB_SEARCH)

        val byPackage = LinkedHashMap<String, ResolveInfo>()
        for (ri in query(pm, viewIntent) + query(pm, webSearchIntent)) {
            val pkg = ri.activityInfo?.packageName ?: continue
            byPackage.putIfAbsent(pkg, ri)
        }

        val collator = Collator.getInstance()
        return byPackage.map { (pkg, ri) ->
            val label = try {
                ri.loadLabel(pm)?.toString()?.trim().takeIf { !it.isNullOrEmpty() } ?: pkg
            } catch (_: Throwable) {
                pkg
            }
            val icon = try {
                ri.loadIcon(pm)
            } catch (_: Throwable) {
                null
            }
            BrowserEntry(label, pkg, icon)
        }.sortedWith(compareBy(collator) { it.label })
    }

    /**
     * @param flags 默认 MATCH_DEFAULT_ONLY；**查桌面应用时必须传 0**。
     *
     * 原因:绝大多数 App 的 MAIN/LAUNCHER filter 不带 CATEGORY_DEFAULT
     * (例如 com.quark.clouddrive 就只有 MAIN + LAUNCHER),
     * 用 MATCH_DEFAULT_ONLY 会把它们全部过滤掉,查出来的列表几乎是空的 ——
     * 表现就是"应用替换"里显示包名而不是 App 名、选择 A/B 的列表点开是空的。
     */
    private fun query(
        pm: PackageManager,
        intent: Intent,
        flags: Int = PackageManager.MATCH_DEFAULT_ONLY,
    ): List<ResolveInfo> = try {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, flags)
        }
    } catch (_: Throwable) {
        emptyList()
    }
}
