import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 发布签名:根目录 keystore.properties 存在时启用(storeFile 相对 app/ 模块目录)
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

/**
 * CI 用:通过环境变量显式指定 debug 钥匙库。
 *
 * 为什么需要:AGP 默认把 debug 钥匙放在"Android 用户目录"下,而这个位置随环境变化。
 * 实测在 GitHub runner 上并不在 `~/.android`,于是 workflow 里缓存 `~/.android` 缓存到的是
 * 一个几乎空的目录(365 字节),钥匙根本没被保存 —— 表现就是"每次构建签名都不一样",
 * 用户无法覆盖安装升级。
 *
 * 这里把路径改成由 workflow 显式提供并缓存,生成与复用都由 CI 自己控制,
 * 不再依赖 AGP 猜位置。本地构建不设该变量,行为完全不变。
 */
val debugKeystoreEnv = System.getenv("DEBUG_KEYSTORE")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.mo.fkLTY"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mo.fkLTY.pro"
        minSdk = 29
        targetSdk = 37
        versionCode = 35
        versionName = "1.2.16"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (keystoreProps.isNotEmpty()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    // CI 显式指定 debug 钥匙库时改用它(本地不设 DEBUG_KEYSTORE,故此段不生效)
    if (debugKeystoreEnv != null && rootProject.file(debugKeystoreEnv).exists()) {
        signingConfigs {
            getByName("debug") {
                storeFile = rootProject.file(debugKeystoreEnv)
                storePassword = System.getenv("DEBUG_KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("DEBUG_KEYSTORE_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("DEBUG_KEYSTORE_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        /**
         * R8 版:额外产出一个开了 R8(缩减+混淆)的包,与普通版并存发布,由用户自己选。
         *
         * 关键约束:
         * - 与普通版**同 applicationId、同签名**,所以两者能互相覆盖安装,也可以随时换回来;
         *   代价是不能同时装两个(本来也没必要)。
         * - versionCode / versionName 与普通版完全一致,只在 Release 的文件名上区分(`-r8`),
         *   否则"同一版两个 versionName"会让排障时对不上号。
         * - isDebuggable 关掉:R8 版是给日常用的,不是调试包。
         * - keep 规则放在 src/main/keepRules 目录下的 .keep 文件里。**注意 java_init.list
         *   里的入口类必须按名字 keep**,否则 R8 会把这个在代码里零引用的类当死代码删掉,
         *   模块直接不加载。
         */
        create("r8") {
            isDebuggable = false
            signingConfig = if (keystoreProps.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                // CI 里 debug 钥匙库由 DEBUG_KEYSTORE 固定,保证每次构建同一个签名
                signingConfigs.getByName("debug")
            }
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // libxposed 新 API:框架进程内已有实现,必须 compileOnly,禁止打进 APK
    compileOnly(libs.libxposed.api)
    compileOnly(libs.libxposed.annotation)
    // 设置 App 端写远程偏好走 service 库(该库随 APK 打包)
    implementation(libs.libxposed.service)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// M6 验收:APK 内必须存在 META-INF/xposed 三件套(防 AGP 打包丢元数据)
val verifyXposedMetadata = tasks.register("verifyXposedMetadata") {
    group = "verification"
    description = "Asserts that META-INF/xposed/* module metadata survived APK packaging."
    // 同时覆盖 debug 与 release:CI 打 release 包时同样要过这道闸,否则元数据丢了才发现
    val apkRoot = layout.buildDirectory.dir("outputs/apk")
    doLast {
        val required = listOf(
            "META-INF/xposed/java_init.list",
            "META-INF/xposed/module.prop",
            "META-INF/xposed/scope.list",
        )
        val root = apkRoot.get().asFile
        val apks = root.walkTopDown().filter { it.isFile && it.extension == "apk" }.toList()
        if (apks.isEmpty()) throw GradleException("No APK found under $root (run assembleDebug/assembleRelease first)")
        apks.forEach { apk ->
            ZipFile(apk).use { zip ->
                val missing = required.filter { zip.getEntry(it) == null }
                if (missing.isNotEmpty()) {
                    throw GradleException("Xposed metadata missing from ${apk.name}: $missing")
                }
            }
            println("Xposed metadata verified in ${apk.name}")
        }
    }
}

tasks.matching { it.name == "assembleDebug" || it.name == "assembleRelease" || it.name == "assembleR8" }.configureEach {
    finalizedBy(verifyXposedMetadata)
}
