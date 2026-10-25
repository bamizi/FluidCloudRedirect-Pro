package com.mo.fkLTY.ui.theme

import android.content.Context

/**
 * 界面本地偏好（只存外观，与模块的远程偏好无关）。
 *
 * 单独存一份的理由：模块的远程偏好是给被 hook 的 metis 进程读的，
 * 外观属于"这台设备上这个 App 长什么样"，两者生命周期和权限模型都不同，混在一起会互相牵连。
 */
object UiPrefs {

    private const val FILE = "ui_prefs"
    private const val KEY_THEME = "theme_mode"

    /** 默认浅色：即设计稿那一版；用户没选过时按这个来，不跟系统漂移 */
    private const val DEFAULT_THEME = "light"

    fun themeMode(context: Context): ThemeMode {
        val id = runCatching {
            context.applicationContext
                .getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getString(KEY_THEME, DEFAULT_THEME)
        }.getOrNull()
        return ThemeMode.fromId(id ?: DEFAULT_THEME)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME, mode.id)
                .apply()
        }
    }
}
