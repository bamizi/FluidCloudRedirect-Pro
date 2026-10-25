# FluidCloudRedirect Pro (流体云重定向 Pro)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

> **本项目是 [wangliaoqiongchen/FluidCloudRedirect](https://github.com/wangliaoqiongchen/FluidCloudRedirect) 的衍生版本(第三方修改版)。**
> 原作者:wangliaoqiongchen,原项目以 GPL-3.0 授权。本版本自 2026-10-24 起由社区修改,
> 新增「应用替换:按目标包名把卡片改投另一个 App」等能力,具体修改点见 [NOTICE](NOTICE) 与 [CHANGELOG-PRO.md](CHANGELOG-PRO.md)。
> 本项目同样以 **GPL-3.0** 发布;这不是原作者发布的版本,原作者不对本版本提供支持或背书。

将 OPPO/一加「流体云」(智慧决策服务 `com.oplus.metis`)识别出的卡片重定向到你选择的浏览器，或把「打开某个 App」改成打开另一个 App。

LSPosed 模块,基于 **libxposed 新 API**(`io.github.libxposed:api:102.0.0`,minApi/targetApi=101)。工程当前 `compileSdk=37`、`targetSdk=37`，对应 Android 17 / API 37。

## 工作原理

复制链接后,流体云会把识别出的网页卡片硬编码到 OPPO 浏览器打开。本模块在卡片执行层做改写,三层 hook 互为备份:

| 级别 | 目标 | 作用 |
|---|---|---|
| 主 | `TextIntent.invoke(Context)` | 执行层咽喉,所有卡片打开必经 |
| 冗余 | `SchemeData/WebData.parseIntent(Context)` | 包名硬编码产生点改写,卡片图标/名称同步变对 |
| 监控 | `ZoomOpenHandler.startByZoom`(仅记录) | 未来出现绕过 TextIntent 直开路径时预警 |

仅挂钩 `com.oplus.metis` 的 `:text_intent` 子进程,其余进程零干预。

## 重定向规则(应用内可开关)

1. `heytapbrowser://webpage?url=<真实URL>` 协议卡片 → 提取并打开真实网址(默认开);
2. 文字搜索卡片 `textType == 29`(默认开);
3. 目标被硬编码为 `com.heytap.browser` 的 http(s) 网页卡片(默认开);
4. 任意浏览器网页卡片(实验性,默认关)。

## 应用替换（复制口令场景）

这是本版本最实用的能力。「复制口令」卡片里没有网址,只有一段口令文本;metis 直接认定这段
口令属于哪个 App,并把目标包名写死。这种卡片**按 URL 永远匹配不到**,只能按**目标包名**替换:

| 字段 | 值 |
|---|---|
| A(原本要打开的) | `com.quark.browser`(夸克) |
| B(改成打开的) | `com.quark.clouddrive`(夸克网盘) |

夸克系有两个包名，**别选错**：

| 包名 | 应用 | 说明 |
|---|---|---|
| `com.quark.clouddrive` | 夸克网盘（独立 App） | 网盘专用客户端。目标就是它 |
| `com.quark.browser` | 夸克（主 App） | 浏览器 + 内置网盘。选它只会打开浏览器界面，不是网盘原生页 |

本机实际装了哪些，用 `adb shell pm list packages | grep quark` 一看便知，别照抄网上的旧教程。

### 源 App 卸载之后

只装夸克网盘、不装夸克时，metis 不再产出"打开夸克"的意图，而是换成"去安装夸克"的卡片，
点它发出的是商店意图：

```
action=VIEW  data=market://details?id=com.quark.browser   （package 为空或为商店）
```

这种意图的目标包是商店、不是源包，所以按包名匹配天然不命中，点击会落到应用商店。
1.2.6 起会把这类"引导安装源 App"的意图接住，照样改投到 B：

| 优先级 | 落地方式 | 日志 |
|---|---|---|
| 1 | 剪贴板里还留着分享地址，直接用它打开分享页 | `plan=shared-link:https://…` |
| 2 | 原始数据原样给 | `plan=keep-data` |
| 3 | 从内嵌参数里拆出地址 | `plan=nested-url:…` |
| 4 | 只把 B 拉起来 | `plan=launcher` |

第 1 级能命中时体验最好；Android 10+ 后台读不到剪贴板属正常，会退到第 4 级——
表现为"打开了夸克网盘"，但不会自动进到那条分享。

### 三级降级：为什么不是"认不认"二选一

这类卡片带的是各家私有协议,例如:

```
qklink://www.uc.cn/<hash>?action=open_url&url=https%3a%2f%2fpan.quark.cn%2fs%2f<id>
```

夸克网盘并不认 `qklink://`,所以不能只判断一次"目标接不接得住"就放弃。实现上逐级降级,
第一个能被目标接住的就是答案:

| 级别 | 做法 | 命中场景 |
|---|---|---|
| `keep-data` | 原始 data 原样给目标 | 目标也注册了该私有协议 |
| `nested-url` | 从 `?url=` 挖出真实 https 地址,当普通链接打开 | 目标只认 `https://pan.quark.cn/…`(最常见) |
| `launcher` | 清空 data,只把 App 拉到前台 | 目标连链接都不认,靠它自己读剪贴板 |

日志里 `Swapped package … plan=xxx` 会写明实际用了哪一级、跳过了哪几级。

还有一层兜底:优先走 `TextIntent.invoke` 等原有 hook;若 metis 在后续步骤用
`bean.packageName` 重建 Intent 又把原始 data 贴回来,`ContextImpl.startActivity` 出口会再修一次,
避免 `ActivityNotFoundException` 表现为「点了完全没反应」。

## 安装与使用

1. 安装 APK,LSPosed 管理器中启用模块;
2. 作用域确认包含「智慧决策服务」(scope.list 已默认声明;也可在应用内点「一键申请作用域」);
3. **给 metis 开后台启动资格**(见下节,不开则"复制口令 → 打开网盘"这条会被系统拦掉);
4. 重启手机或 `adb shell am force-stop com.oplus.metis`;
5. 应用内选择目标浏览器,或在「应用替换」里配置 A → B;设置通过 LSPosed 远程偏好下发,**即时生效**,无需重启 metis;
6. 「禁用重定向」= 保持系统默认行为。

要求:Android 10+(minSdk 29)、支持 libxposed 新 API 的 LSPosed、**root**。

### 必须做:`SYSTEM_ALERT_WINDOW`

流体云的点击跳转是 metis 这个**后台服务**发起的。Android 从 10 起禁止后台应用启动界面,
系统返回 `BAL_BLOCK`(`result code=102`),表现为**点了完全没反应**——
日志里长这样:

```
START u0 { … cmp=com.quark.clouddrive/com.ucpro.MainActivity mCallingUid=10132 }
    from uid 10132 (com.oplus.metis) (BAL_BLOCK) result code=102
```

AOSP 的豁免清单里有一条:调用方持有 `SYSTEM_ALERT_WINDOW` 即允许后台启动。这个 AppOp 可以直接置：

```sh
su -c cmd appops set com.oplus.metis SYSTEM_ALERT_WINDOW allow
```

执行后强停一次 metis 或重启。撤销:

```sh
su -c cmd appops set com.oplus.metis SYSTEM_ALERT_WINDOW default
```

> 这个设置会写进 `/data/system/appops.xml`,重启后保留;卸载模块不会自动撤销它。
> 不想每次手动设置的话,仓库使用的同一目录下有一个 `MetisBALAllow` 模块可开机自动执行这条命令。

日志:LSPosed 管理器 → 日志,过滤 `FluidRedirect`;应用内可开「详细日志」。

## 构建

```bat
gradlew assembleDebug      :: 产物 app\build\outputs\apk\debug\app-debug.apk
gradlew assembleRelease    :: R8 混淆 + release 签名
gradlew assembleR8         :: R8 版（同 release 一样的缩减/混淆，但沿用 debug 签名，便于覆盖安装）
gradlew testDebugUnitTest  :: 策略层单元测试
```

- 构建后自动执行 `verifyXposedMetadata` 任务,断言 APK 内存在 `META-INF/xposed/` 三件套;
- **每个版本发布两个包**:`FluidCloudRedirect-Pro-<版本>.apk`(普通版)与
  `FluidCloudRedirect-Pro-<版本>-r8.apk`(R8 版,代码缩减+混淆)。两者同签名、
  同 applicationId、同 versionCode,可互相覆盖安装。R8 版由 `app` 模块的 `r8` buildType 产出
  (`gradlew assembleR8`),版本号取自 `app/build.gradle.kts` 的 `versionName`;
- **R8 版的 keep 规则在 `app/src/main/keepRules/*.keep`**(AGP 9.3+ 的新约定,不再用
  `proguardFiles`)。其中 `rules.keep` 开头就写明:`META-INF/xposed/java_init.list` 的入口类
  在代码中零引用,不按名字 keep 就会被 R8 当死代码删掉,表现为"模块装了但完全不生效";
- Gradle 默认产物永远叫 `app-debug.apk` / `app-release.apk`,几个版本下载到手机文件夹里
  全重名,分不清谁新谁旧,所以工作流里多了一步按版本号改名;
- release 签名读取根目录 `keystore.properties`(不入库)。自建签名:生成自己的 keystore 后按以下格式写 `keystore.properties` 即可,并同步修改 `app/build.gradle.kts` 的引用(工程已内置读取逻辑):

  ```properties
  storeFile=<keystore 文件路径>
  storePassword=<密码>
  keyAlias=<别名>
  keyPassword=<密码>
  ```

## 工程结构

```
app/src/main/java/com/mo/fkLTY
├─ hook/      MainHook(入口)· RedirectHooker(主)· ParseIntentHooker(冗余)
│             MonitorHooker(监控)· ReflectedTextIntent(反射封装)· HookLog
├─ engine/    Policy(规则判定,纯 JVM,含单测)· RedirectEngine(改写执行)
│             PackageSwap(应用替换 + 三级降级)· IntentInspector(卡片取证)
│             BrowserAvailability · RedirectPrefs
├─ service/   PrefsBridge(XposedService 封装:作用域查询/一键申请/远程偏好写入)
└─ ui/        Compose M3 主屏(状态卡 · 规则开关 · 浏览器列表 · 应用替换 · 诊断)
app/src/main/resources/META-INF/xposed/   java_init.list · module.prop · scope.list
```

## 已知注意事项

- hook 进程内远程偏好**只读**(libxposed API 语义),写入只能发生在设置 App 端;
- `META-INF/xposed` 三件套由 `verifyXposedMetadata` 任务把关,改打包方式时请保留该校验;
- 智慧决策服务未来若把「打开动作」下沉到检测层,监控位日志会首先暴露,届时主 hook 迁移即可。

## 开源协议

本项目基于 [GPL-3.0](LICENSE) 协议开源,为 [wangliaoqiongchen/FluidCloudRedirect](https://github.com/wangliaoqiongchen/FluidCloudRedirect) 的衍生作品。

本仓库(对应源码):https://github.com/bamizi/FluidCloudRedirect-Pro

Copyright (c) 2026 wangliaoqiongchen
Modifications Copyright (c) 2026 bamizi (https://github.com/bamizi)

任何对本项目代码的复制、修改与再分发,均须遵循 GPL-3.0:衍生作品必须同样以 GPL-3.0 开源、保留原作者版权声明,并标注修改内容与日期。
分发本项目的 APK 时,须同时提供对应源码(本仓库即为其一)与 [NOTICE](NOTICE) 文件。
