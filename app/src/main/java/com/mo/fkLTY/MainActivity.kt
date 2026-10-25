package com.mo.fkLTY

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mo.fkLTY.service.PrefsBridge
import com.mo.fkLTY.ui.picker.BrowserPickerScreen
import com.mo.fkLTY.ui.theme.ThemeMode
import com.mo.fkLTY.ui.theme.UiPrefs
import com.mo.fkLTY.ui.theme.流体云重定向Theme
import com.mo.fkLTY.ui.theme.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PrefsBridge.init()
        setContent {
            val context = LocalContext.current
            // 外观模式存在本地偏好里，切换后立即重组，不需要 recreate Activity
            var mode by remember { mutableStateOf(UiPrefs.themeMode(context)) }
            流体云重定向Theme(darkTheme = mode.isDark()) {
                // 用背景色而不是 panel 色，否则整屏会被面板色铺满，卡片的层次就没了
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    BrowserPickerScreen(
                        themeMode = mode,
                        onThemeModeChange = { next: ThemeMode ->
                            UiPrefs.setThemeMode(context, next)
                            mode = next
                        },
                    )
                }
            }
        }
    }
}
