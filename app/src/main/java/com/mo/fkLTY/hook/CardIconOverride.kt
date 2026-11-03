package com.mo.fkLTY.hook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.File
import java.io.FileOutputStream

/**
 * 卡片图标覆盖。
 *
 * ## 为什么能改（反汇编 metis 得到的事实，不是猜的）
 *
 * `CardInfo` 上那几个图标字段（`cardIcon` / `leadingIcon` / `capsIcon` …）
 * 全是 **String**，而且它们装的不是 `@drawable/资源名`，是 **Uri 字符串**。
 * 证据链在 metis 的 `classes2/classes6/classes7`：
 *
 * ```
 * SeedlingSender.sendByCommand
 *   ├─ 服务端下发 PlatformBeen.getCardIcon() -> CardIcon(largeIcon, smallIcon)   // 两个 String
 *   ├─ async largeDeferred: SeedlingIconHandler.getShareUri(ctx, cardIcon.getLargeIcon())
 *   │     ├─ ImageCache.getLocalPath(ctx, url)           // 把 URL 下载成本地文件
 *   │     ├─ FileShareHelper.getDefaultShareFileDir()    // share_images 目录
 *   │     └─ FileShareHelper.getShareFileUri(path, ctx)  // -> content:// Uri
 *   └─ 结果写回 CardInfo.cardIcon
 * ```
 *
 * `FileShareHelper.getShareFileUri` 内部：`authority = context.packageName + ".FileShareProvider"`，
 * 再走 `permissionUri()` 把读权限授予 SystemUI 等包。metis 的 AndroidManifest 里确实注册了
 * `com.oplus.metis.FileShareProvider`（`grantUriPermissions=true`），路径映射到 `share_images`。
 *
 * 结论：**只要把图标写成 PNG 放进 metis 的 `share_images` 目录，再用同一个
 * `FileShareHelper.getShareFileUri()` 换成 Uri，渲染层就认**——和 metis 自己下发网络图标的做法完全一致，
 * 格式和权限都不用我们操心。
 *
 * ## 支持的写法
 *
 * - `app://{app}` —— 取目标应用自己的启动图标（`{app}` 在这里是包名）
 * - `file:///…`、`content://…` —— 原样透传，留给高级用法
 * - 空 —— 不改
 */
internal object CardIconOverride {

    private const val SHARE_HELPER = "com.oplus.pantanal.seedling.file.FileShareHelper"

    /**
     * 把卡片上的图标换成 [spec] 指的东西。
     *
     * @param spec 已解析过的图标来源（`{app}` 已展开成包名 / 或 `app://` 已剥离）
     * @param pkg  目标应用包名，`app://` 模式用它取图标
     * @return 成功改了哪几个字段的可读描述；全失败返回 null
     */
    fun apply(context: Context, cardInfo: Any, ciCls: Class<*>, spec: String, pkg: String): String? {
        val uri = resolveToUri(context, ciCls, spec, pkg) ?: return null

        /*
         * 只改 cardIcon 这一个字段。
         *
         * 1.2.19 一开始把 leadingIcon / capsIcon 也一起写了，结果真机上卡片
         * 直接点不动了——这三个字段在 metis 里的用途并不相同（cardIcon 是卡片主图，
         * leadingIcon 是通知栏左侧小图，capsIcon 是胶囊图标），后两者在部分布局里
         * 由 SystemUI 用另一套逻辑解析，塞一个 content:// 进去会让渲染阶段拿不到图，
         * 整张卡片退化成不可点。教训：不确定用途的字段不要"顺手一起设"。
         */
        val changed = if (setField(ciCls, cardInfo, "setCardIcon", uri)) 1 else 0
        if (changed == 0) return null

        return "uri=$uri"
    }

    /** 把用户填写的内容变成一个渲染层能读的 Uri 字符串 */
    private fun resolveToUri(context: Context, ciCls: Class<*>, spec: String, pkg: String): String? {
        val s = spec.trim()
        if (s.isEmpty()) return null

        // app:// 取目标应用图标 → PNG → metis 共享目录 → 官方 Uri
        val target = if (s.startsWith("app://")) s.removePrefix("app://").ifBlank { pkg } else null
        if (target != null) return shareAppIcon(context, ciCls, target)

        // 其余（file:// content:// 或裸路径）原样交给渲染层
        return when {
            s.startsWith("file://") || s.startsWith("content://") -> s
            s.startsWith("/") -> "file://$s"
            else -> null
        }
    }

    /** 取出某个应用的图标，写成 PNG 放进 metis 的共享目录，返回已授权的 content:// Uri */
    private fun shareAppIcon(context: Context, ciCls: Class<*>, pkg: String): String? {
        val bitmap = runCatching {
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            val drawable = context.packageManager.getApplicationIcon(info)
            drawable.toBitmap(192, 192)
        }.getOrNull() ?: return null

        val dir = shareDir(context, ciCls) ?: return null
        val file = File(dir, "fcr_${pkg.replace('.', '_')}.png")
        runCatching {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.onFailure { return null }

        return shareUri(context, ciCls, file.absolutePath) ?: "file://${file.absolutePath}"
    }

    /** metis 的共享目录（`share_images`）；拿不到就返回 null，由调用方退回 file:// */
    private fun shareDir(context: Context, ciCls: Class<*>): File? {
        val inst = shareHelperInstance(ciCls) ?: return null
        val path = runCatching {
            val m = inst.javaClass.getMethod("getDefaultSharePath", Context::class.java)
            m.invoke(inst, context) as? String
        }.getOrNull()
        val dir = path?.let { File(it) } ?: return null
        if (!dir.exists() && !dir.mkdirs()) return null
        return dir.takeIf { it.isDirectory }
    }

    /**
     * 用 metis 自己的 API 生成 Uri。
     *
     * 不能自己拼 `content://com.oplus.metis.FileShareProvider/<path>`：
     * 路径怎么编码、文件是否在映射表里都由 provider 决定；更重要的是
     * `permissionUri()` 会顺带把读权限授予 SystemUI，自己拼的 Uri 没有这一步，
     * 渲染层拿到也读不了。
     */
    private fun shareUri(context: Context, ciCls: Class<*>, path: String): String? {
        val inst = shareHelperInstance(ciCls) ?: return null
        return runCatching {
            val m = inst.javaClass.getMethod(
                "getShareFileUri",
                String::class.java,
                Context::class.java,
            )
            (m.invoke(inst, path, context) as? android.net.Uri)?.toString()
        }.getOrNull()
    }

    /**
     * 拿 `FileShareHelper` 单例。
     *
     * **必须用 metis 那边的 ClassLoader**：hook 代码是模块的 PathClassLoader 加载的，
     * 直接 `Class.forName` + `javaClass.classLoader` 找不到宿主应用的类，会静默失败。
     * 这里以 `CardInfo` 的 ClassLoader 为准（它就是 metis 的），再退回 Context 的。
     */
    private fun shareHelperInstance(ciCls: Class<*>): Any? {
        val loaders = listOfNotNull(ciCls.classLoader, javaClass.classLoader)
        for (cl in loaders) {
            val inst = runCatching {
                val cls = Class.forName(SHARE_HELPER, false, cl)
                cls.getField("INSTANCE").get(null)
            }.getOrNull()
            if (inst != null) return inst
        }
        return null
    }

    private fun setField(cls: Class<*>, target: Any, setter: String, value: String): Boolean =
        runCatching {
            cls.getMethod(setter, String::class.java).invoke(target, value)
            true
        }.getOrDefault(false)

    private fun setBool(cls: Class<*>, target: Any, setter: String, value: Boolean): Boolean =
        runCatching {
            cls.getMethod(setter, java.lang.Boolean.TYPE).invoke(target, value)
            true
        }.getOrDefault(false)

    /** Drawable → Bitmap，按目标边长等比缩放后居中绘制（图标一般是 1:1，留个保险） */
    private fun Drawable.toBitmap(size: Int, size2: Int = size): Bitmap {
        if (this is BitmapDrawable && bitmap != null) {
            val src = bitmap
            if (src.width == size && src.height == size2) return src
        }
        val w = if (intrinsicWidth > 0) intrinsicWidth else size
        val h = if (intrinsicHeight > 0) intrinsicHeight else size2
        val scale = minOf(size.toFloat() / w, size2.toFloat() / h)
        val dw = (w * scale).toInt().coerceAtLeast(1)
        val dh = (h * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(size, size2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val left = (size - dw) / 2
        val top = (size2 - dh) / 2
        setBounds(left, top, left + dw, top + dh)
        draw(canvas)
        return bmp
    }
}
