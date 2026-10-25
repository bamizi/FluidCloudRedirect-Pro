# FluidCloudRedirect Pro - 变更记录

## 1.2.13（真机实测定案：文案层只有 `Seedling$Builder.build()` 一条路，标题一并改）

1.2.12 在真机上跑通了，但拿到 metis 推给 SystemUI 的原始 `businessData` 后发现两件事：

**一、`SingleAppInstallPanel.onPositiveText()/onTitle()` 在本机型从未被调用。**
两个 hook 都装上了、都打出了 `Hook installed`，日志里却一条命中都没有 ——
说明这张卡走的是 pantanal 的 `Seedling` + `ChannelClient` JS 卡（`eventCode:20104`），
不是传统安装面板。因此 1.2.12 那套 `InstallPanelTextHooker` 属于"装上了但用不上"，
保留作为其他机型/其他卡片类型的兜底，不再作为主路径。

**二、只改按钮会让卡片自相矛盾。** 实测原始数据是：

```json
"buttonText":   "打开夸克网盘",          // 我们改的
"cardTitle":    "安装“夸克”查看",        // 没动
"capsTitle":    "安装“夸克”查看",        // 没动
"cardSubTitle": "前往一加软件商店安装"    // 没动
```

本版在同一个 `build()` 里补齐（`CardInfo` 的 setter 已从 `classes7.dex` 核对，
`setCardTitle` / `setCardSubTitle` / `setCapsTitle` / `setCapsDirectText` 均存在）：

| 字段 | 原文案 | 改后 |
|---|---|---|
| `buttonText` | 去安装 | 打开夸克网盘 |
| `cardTitle` | 安装“夸克”查看 | 打开“夸克网盘”查看 |
| `capsTitle` | 安装“夸克”查看 | 打开“夸克网盘”查看 |
| `capsDirectText` | 安装“夸克”查看 | 打开“夸克网盘”查看 |
| `cardSubTitle` | 前往一加软件商店安装 | 已安装夸克网盘，点击直接打开 |

改写条件收紧了：标题三处与副标题**只在原文含「安装」时才改**，避免 metis 换文案后
被硬套成新的不一致；另外新增目标 App 可用性校验（`BrowserAvailability.isAvailable`），
目标没装就一个字都不动（改了文案却打不开，比不改更糟）。

## 1.2.12（换更准的切入点：安装卡片自己的文案方法）

1.2.11 用 `Seedling$Builder.build()` 改按钮文案，真机上钩子装上了、但 `build()` 那条路没被走到
（日志里连 `seedling[build]: entered` 都没有）。继续翻 dex，找到更直接的类：

```
Lcom/oplus/textintent/distribution/ui/SingleAppInstallPanel;   // 就是「引导安装」这张卡
    private final TextIntent textIntent;   getTextIntent(): TextIntent
    onPositiveText(): String               // 「去安装」按钮文字
    onTitle(): String                      // 「安装"夸克"查看」标题
    onPackageChanged(String, Z) / onInstallClick(Z) / onShow() / onViewCreate(View)
```

于是新增 `hook/InstallPanelTextHooker.kt`，hook `onPositiveText()` 与 `onTitle()`：

- `chain.proceed()` 拿到原文，含「安装」才继续
- 用 `panel.getTextIntent()` 取 `packageName` / `intent.dataString`，
  与「应用替换」的 `from` 精确比对（`pkg == from` 或 data 里含 `from`）
- 命中才返回「打开<目标应用名>」，未命中/异常一律原样返回并留一行日志
- 上下文用 `ActivityThread.currentApplication()` 反射获取，不依赖调用方传参

同时给 `SeedlingLabelHooker` 加了入口留痕（`seedling[build]: entered`），
用来确认那条路径到底有没有被走到。

## 1.2.11（卡片按钮文案：安装 → 打开）

之前一直说"卡片上的字改不了"。这句话只在找错层的前提下成立——卡片不是 metis 现场画的，
而是用 `Seedling.Builder` 构建好、交给 SystemUI 的 SeedlingCard 窗口渲染的，和点击走的
`TextIntent.invoke` / `startActivity` 是两条路。改 Intent 只能改"点了去哪"。

本版从本机 metis APK（`/product/priv-app/Metis/Metis.apk`，9 个 dex）里核对到符号：

```
Lcom/oplus/textintent/pantanal/Seedling$Builder;
    build()             -> Lcom/oplus/textintent/pantanal/Seedling;
    buttonLabel(String), cardTitle(String), capsTitle(String) …
Lcom/oplus/textintent/pantanal/bean/CardInfo;      // Kotlin data class,有 get/set
    setButtonText / setLeftButtonText / setRightButtonText(String)
```

新增 `hook/SeedlingLabelHooker.kt`，hook `Seedling$Builder.build()`：

- 在 `build()` 返回后读 `Seedling.getCardInfo()` / `getData()`，此时卡片信息已完整
- **只碰"引导安装"类卡片**（按钮或标题里含「安装」），其余卡片一个字不动
- 且要求卡片内容里出现「应用替换」的源包名，才把按钮改成「打开<目标 App 名称>」
- 改完记一行 `seedling[build]: …`，未匹配时也记一行，便于排查
- 全程反射 + 每步 try-catch，`ExceptionMode.PROTECTIVE`，失败只记日志，不影响 metis

已知限制：卡片**标题**（如“安装"夸克"查看”）本次没动，只改按钮；标题字段同样是
`setCardTitle(String)`，等确认按钮这条路稳定后再一起改。

## 1.2.10（补上必需的系统前提，并让日志直接告诉你缺什么）

1.2.9 修好 Intent 之后，真机上又出现"点了没反应"。这次日志把原因写得很清楚：

```
START u0 { … cmp=com.quark.clouddrive/com.ucpro.MainActivity mCallingUid=10132 }
    from uid 10132 (com.oplus.metis)  (BAL_BLOCK)  result code=102
```

`cmp=` 出现说明 Intent 没问题了，`BAL_BLOCK` 说明卡在**后台启动限制**：
流体云的跳转由 metis 这个后台服务发起，Android 10 起默认禁止。

AOSP 的豁免清单里有一条"调用方持有 `SYSTEM_ALERT_WINDOW` 即允许后台启动"，
这个 AppOp 可以直接设：

```sh
su -c cmd appops set com.oplus.metis SYSTEM_ALERT_WINDOW allow
```

本版做的改动：

- **新增运行期提示**：改写前检查宿主的 `SYSTEM_ALERT_WINDOW`，不是 `allow` 就写一行
  带命令的日志（每进程一次），不用再靠猜"为什么点了没反应"
- README 新增「必须做：SYSTEM_ALERT_WINDOW」一节，附 `BAL_BLOCK` 日志样例与撤销命令
- Release 说明的安装步骤补上这一步

> 更正 1.2.9 发布说明里"那个 appop 不是原因"的说法——它是必需的。
> 之前看不出来，是因为两个故障串联：Intent 解析失败（-91）挡在前面，
> 根本走不到 BAL 这一步。详见仓库文档 §16。

## 1.2.9（真正的元凶：`launcher` 方案拼错了 Intent）

前三版（1.2.6 / 1.2.7 / 1.2.8）都在改判据、改钩子、改校验，但真机上"点了没反应"始终没解决。
拿到 1.2.8 的完整日志后才定位到：**改写本身是对的，是我们拼出来的那个 Intent 根本无法启动**。

日志里 metis 自己记的失败原因写得很清楚：

```
fail_reason=No Activity found to handle Intent {
    act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER]
    flg=0x14000000 pkg=com.quark.clouddrive }
```

**原因**：`ACTION_MAIN + CATEGORY_LAUNCHER + setPackage()` 这种隐式意图**启动不了绝大多数第三方 App**。
Android 的硬性规则是——隐式启动要求目标过滤器带 `CATEGORY_DEFAULT`，而 App 的启动 Activity
通常只声明 `MAIN + LAUNCHER`，没有 `DEFAULT`。于是出现了一个很迷惑的组合：

| 判据 | 结果 |
|---|---|
| `getLaunchIntentForPackage(pkg) != null`（我们的探针） | ✅ 通过（PM 查询不强制 DEFAULT） |
| 真的 `startActivity(该 Intent)` | ❌ `ActivityNotFoundException`（系统按 DEFAULT 语义解析） |

**探针和真实启动用的不是同一条判据**，所以"校验通过 → 改写成功 → 点了没反应"。
在任意第三方 App 上都能用一条命令复现：

```sh
adb shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p com.quark.clouddrive
# Error: Activity not started, unable to resolve Intent { … pkg=com.quark.clouddrive }

adb shell am start -n com.quark.clouddrive/com.ucpro.MainActivity
# Starting: Intent { cmp=com.quark.clouddrive/com.ucpro.MainActivity }   ← 成功
```

**修法**：`launcher` 方案不再自己拼 Intent，直接用 `getLaunchIntentForPackage` 的返回值——
它是**显式组件**（`MAIN + LAUNCHER + component=pkg/Activity`），绕开 DEFAULT 限制。
桌面点图标走的就是这条路径。探针也换成同一个显式意图，让"校验"和"启动"用同一把尺子。

顺带纠正两个此前的错误结论（详见仓库文档 §12–§15）：

- 1.2.6 的"死卡"不是 `PendingIntent` 钩子改坏的——1.2.6 与 1.2.8 的失败日志形态完全一致；
- 也不是后台启动限制（BAL）：给 metis 加 `SYSTEM_ALERT_WINDOW` appop 后仍然失败，
  而且同样的失败在 adb shell 里能直接复现，与调用方是谁无关。

## 1.2.8（放宽启动性判定，避免"认出来了却全判死"）

1.2.7 用同一套判据校验所有改写方案：`resolveActivity` 能解析到具体 Activity，且该 Activity
是 `exported`。问题在于 **`resolveActivity` 对隐式意图存在假阴性**——解析不到不等于启动不了。
一旦剪贴板方案、原始 data、内嵌地址、启动 App 这四级被全判死，`applyVerified` 返回 false，
卡片退回"跳商店"：现象和没修一样，只是日志里多一行 `no usable plan`。

改成按方案性质分别判断：

- **`launcher`（只把 App 拉起来）**：用 `getLaunchIntentForPackage`。1.2.5 起一直在用它，
  不受 Android 11+ 包可见性影响。
- **带 data 的方案**：先 `resolveActivity` + `exported`（能拿到具体组件就用它）；
  解析不到时再用 `queryIntentActivities` 兜一次——目标包里真有能接住该意图的组件也算过关。

两条判据都只认"目标里确实存在接收者"，不会凭空放行，所以这次放宽**不会**重新引入死卡
（1.2.6 → 1.2.7 修的正是死卡）。

未变的部分：识别（`StoreLink`）、剪贴板补偿（`ClipboardShare`）、钩子集合（仍是 1.2.5 的五个钩）。
卡片文字仍是 metis 渲染的"去安装夸克"，模块只改点击目标，改不了文字。

## 1.2.7（修 1.2.6 引入的"点击无反应"）

1.2.6 装到真机后，卡片仍然显示"去安装夸克"，但**点击完全没有反应**了——
比原来跳到应用商店更糟。两处都改掉：

- **撤掉 `PendingIntent` 钩子**（1.2.6 新增）。
  它原本是为了覆盖"点击由卡片宿主 SystemUI 代发 PendingIntent"的情形，
  但实现上是**就地修改调用方传进来的 Intent**。那个对象很可能被 metis 缓存/复用，
  改坏之后卡片就彻底失效，而且日志里看不出异常。属于投机性覆盖，收益不确定、风险明确，
  故整条移除，钩子集合回到 1.2.5 的状态。

- **改写前逐级校验可启动性**（新增 `applyVerified`）。
  1.2.6 直接改真身，若改写结果启动失败，metis 抛 `ActivityNotFoundException` 自己吞掉，
  用户只看到"点了没反应"，日志里只有一行"已改写"。
  现在每个候选方案都先在**副本**上校验：`resolveActivity` 能解析到具体 Activity，
  且该 Activity 是 `exported` 的；通过才动真身。
  全部方案不合格时返回 false，**原样放行**——卡片退回改造前的行为（跳商店），
  绝不会变成点不动的死卡。

- 新增诊断日志 `store-intent[source]`：当目标是应用商店但没有提到任何源包名时，
  把真实 Intent 完整打出来。这条用来判定 metis 是不是用了别的标识方式
  （内部商品 id、`oaps://` 载荷等），否则只能靠猜。

单测 34 项不变，全部通过。

### 签名固定（同一版附带）

在此之前**每次构建签名都不一样**，导致每个新版本都要卸载重装、配置全丢。实测指纹：

| 版本 | 证书 SHA-256（前 8 位） |
|---|---|
| v1.2.5 | `5b4af133` |
| v1.2.6 | `5b95cfb7` |
| v1.2.7（首版） | `4b49d339` |

原因：AGP 在 runner 上不把 debug 钥匙放在 `~/.android`，缓存那个目录只存到 365 字节的空壳，
钥匙压根没被保存。

修法：工作区固定路径 `.ci-debug/debug.keystore`，由 workflow 用 keytool 生成、缓存，
再通过环境变量 `DEBUG_KEYSTORE` 显式传给 Gradle（`app/build.gradle.kts` 里读取）。
路径与生成规则都由 CI 自己控制，不再依赖 AGP 猜位置。本地不设该变量，行为不变。

同时 Release 说明里会写出本包的签名证书 SHA-256：升级前对照上一版那一行，
相同就能直接覆盖安装，不同则需卸载。

> 缓存连续 7 天不用会被 GitHub 回收，届时会重新生成一把钥匙（签名随之改变，
> Release 页面能立刻看出）。想让签名永远不变，只有配 release keystore 一条路。

## 1.2.6（卸载源 App 后仍能跳转）

真机反馈：夸克和夸克网盘都装着时，复制口令点卡片能跳到夸克网盘；**一旦卸载夸克**，
卡片变成"去安装夸克"，点它跳到应用商店。

根因不在改写逻辑，而在上游链路断得更早：卸载源 App 后 metis 根本不再产出
`package=com.quark.browser` 的跳转意图，而是发出一个商店意图

```
action=VIEW  data=market://details?id=com.quark.browser   （package 为空或为商店）
```

此时目标包是商店、不是源包，按 from 匹配的应用替换天然不命中；它也不是网页数据，
兜底钩子的 `hasWebData` 预筛同样放过——两道判定都落空，Intent 原样进应用商店。

新增「引导安装拦截」：

- `engine/StoreLink.kt`：纯 JVM 判定"这是不是一次引导安装"。识别 `market://`、
  `oaps://` 等商店协议、常见商店包名、商店域名，再结合 data 与 extras 里是否提到源包名。
  三种结论：无关 / 只是提到（只留痕） / 确认引导安装（改写）。
  只提到不改写，是为了不误伤"网页内容里恰好出现这个包名"的正常跳转。
- `engine/PackageSwap.kt`：`redirectInstallPrompt()` 把命中的商店意图改写成打开替换目标。
  落地优先级：剪贴板里的分享地址 > 原始 data > 内嵌地址 > 仅启动 App。
- `engine/ClipboardShare.kt`：卸载源 App 后，真实分享地址在链路上已经不存在了，
  但用户刚复制的口令往往还在剪贴板。能读到就直接用它落到分享页，读不到就退化为拉起 App。
  Android 10+ 后台读不到剪贴板属正常，不影响主流程。
- `RedirectEngine.applyToIntent`：这项检查放在 `hasWebData` 预筛**之前**，否则必然漏过。
- 新增 `StoreLinkTest` 10 项：商店意图、显式商店包、商店网页、仅 extras 命中、
  无关商店意图、正常跳转不被抢、纯分享链接不动、非商店网页只留痕、包名/协议匹配的大小写。

单测 24 → 34 项，全部通过。

## 1.2.5（署名落定）

- `NOTICE` 的修改者、`README` 的 Modifications Copyright 填入实际维护者 `bamizi`。
- `module.prop` 的 `author` 由通用占位改成 `bamizi · 原作 fkLTY`，LSPosed 模块列表里能直接看到
  修改者是谁，同时原作者署名不丢。

## 1.2.4（发布整理：元数据与 CI）

准备公开仓库时发现的三处问题，都在发布前修掉：

- `module.prop` 的 `author` 还写着上游作者 `fkLTY`，LSPosed 模块列表会原样显示，
  等于把修改版标成了原作者出品。改为 `第三方修改版 · 原作 fkLTY`，
  原作者信息仍保留在 NOTICE / LICENSE / README 中。
- `module.prop` 的 `description` 还在宣传 1.2.2 已删除的「正则匹配 / Deep Link 模板」，
  改为当前真实功能（浏览器接管网页卡片 + 应用替换）。
- `.github/workflows/build-apk.yml`：release 签名步骤的 `if: ${{ secrets.X != '' }}` 不成立——
  GitHub Actions 的 `secrets` 上下文在 step 级 `if` 里不可用，条件恒为假，release 包永远打不出来。
  改为经 job 级 `env` 中转，再用 `if: env.KEYSTORE_B64 != ''` 判断。
- `gradlew` 在仓库里补上可执行位（100755），免得别人克隆到 Linux/macOS 后要手动 chmod。

## 1.2.3（关于卡改成"怎么用"）

原来的关于卡是一整段原理说明（卡片执行层、LSPosed 远程偏好、`:text_intent` 进程……），
用户看完仍不知道自己该点哪里，而新加的「应用替换」在里面一个字都没提。重写：

- 一句话说明 + 两条用法（网页卡片选浏览器 / 复制口令配应用替换）+ 一条生效提示，
  用圆点列表排版，比纯段落更容易扫读。
- 复制口令那条直接给了可照抄的例子：`com.quark.browser → com.quark.clouddrive`。
- 补上原先完全没说的前提：**首次安装或升级后要停一次 `com.oplus.metis`（或重启）**，
  否则钩子还是旧的——这是最容易踩的坑，之前只写在 README 里。
- 文案拆成 `about_intro` / `about_usage_browser` / `about_usage_swap` / `about_note`
  四条资源，`values-en` 同步更新，删掉旧的 `about_body`。

## 1.2.2（精简：移除未使用的「自定义 App 跳转规则」）

实用场景确定后，URL 规则这条线用不上（真正管用的是「应用替换」），设置页留着只会让人
以为还需要配它。整条链一起删掉，不是只藏 UI：

- 删除设置页「自定义 App 跳转规则」区块。
- 删除 `RedirectRule` / `RuleMatcher`（规则模型与匹配）、`RuleDiagnostics`（规则自检）、
  `RuleTestDialog`、`CustomRulesEditor`，以及对应单测 `CustomRedirectRuleTest`。
- `RedirectEngine` 判定链简化为 **应用替换 → 原有浏览器规则**，不再读取自定义规则。
  顺手去掉不再使用的 `BrowserAvailability.canHandle()` / `handlersFor(uri)`。
- `RedirectPrefs.KEY_CUSTOM_RULES`、`PrefsBridge.saveCustomRules()`、
  `PickerViewModel.customRules/saveCustomRules` 一并移除。
- 未做迁移：已存在的 `custom_redirect_rules_v1` 键不再被读取，属于无害的孤立偏好项，
  不影响任何行为。这样处理是刻意的——设置页已经没有入口，若引擎仍偷偷读它，
  用户将无法查看或删除一条正在生效的规则。

保留 `Policy` 的四条浏览器规则开关（协议卡 / 文字搜索 / heytap 网页卡 / 任意浏览器卡），
它们与「应用替换」是两件事。单测 24 个全绿（PolicyTest 23 + ExampleUnitTest 1）。

## 1.2.1（修复：私有协议卡片导致应用替换被跳过）

真机日志（1.2.0，`text_intent` 进程）拿到了第一条完整链路：

```
card[invoke]: textType=17, pkg=com.quark.browser,
  data=qklink://www.uc.cn/b20b84fd735a8dd3f7541129bacc4e9a?action=open_url&url=https%3a%2f%2fpan.quark.cn%2fs%2febb0e2cb19e0
Swap target com.quark.clouddrive cannot handle this intent; skip
custom[invoke]: card carries no http(s) url, skip custom rules
```

两个缺陷都在这一张卡上：

1. **替换被"目标接不住"误判**。探测时把原始 `data` 一起带过去问"夸克网盘认不认",
   网盘不认 `qklink://`,于是整条规则放弃。实际上网盘认 `https://pan.quark.cn/…`,
   而且再不济把它拉起来也能自己读剪贴板。1.2.0 只有"能接住 / 放弃"两档，缺了降级。
2. **URL 规则永远看不到真实地址**。`qklink://` 不是 http(s)，被判成"卡片没有网址"，
   于是同一张卡上的 URL 规则、自检、日志全部瞎掉。

修复：

- `PackageSwapEngine` 改为**三级降级**，逐级探测，第一个能被目标接住的就用：
  1. `keep-data`：原始 data 原样给（目标认识该私有协议时最精确）；
  2. `nested-url`：从 `?url=` 参数挖出真实 https 地址，当普通链接打开（最理想）；
  3. `launcher`：清空 data 与 categories，`ACTION_MAIN` + `CATEGORY_LAUNCHER` 只拉起 App。
  日志会打印实际用了哪一级、跳过了哪几级。
- 新增 `Policy.extractNestedUrl()`：任意包装型 URI 的 query 里挖内嵌 http(s) 地址，
  支持百分号编码与双重编码；参数名白名单（`url` / `u` / `link` / `target` / `jumpurl` …）
  优先，其余参数只要值是合法 http(s) 也接受。
- `Policy.extractHttpUrl()` 改为 `http(s) → heytapbrowser://webpage?url= → 通用挖取`
  三段式，是旧行为的严格超集。`Policy.evaluate()` 的规则 1 语义**未改动**。
- `IntentInspector` 对 `clipData` / `extras` 文本先尝试整体百分号解码再匹配，
  覆盖"值被整段编码"的卡片。
- 新增 `PackageSwapEngine.repairIfNeeded()`，在 `ContextImpl.startActivity` 出口兜底：
  若包名**已经是替换目标**、但身上 data 目标接不住（metis 用 `bean.packageName`
  重建 Intent 并贴回原始 data 的情况），就地重新降级修复，避免
  `ActivityNotFoundException` 表现为"点了没反应"。
- `RedirectEngine.applyToIntent()` 的廉价预筛增加 `data.contains("url=")`，
  让 `qklink://` 这类包装协议也能进入判定（此前会被直接放过）。
- 设置页「应用替换」提示改为说明三级降级行为。
- 新增 7 个单测，覆盖 `qklink://` 取内嵌地址、双重编码、无白名单参数名、
  非 http 内嵌值不采纳、`extractHttpUrl` 三段式。

**订正**：1.1.4 条目里"本机型流体云卡片不经过 `TextIntent.invoke`"的结论是错的。
那次日志里没有任何 `card[...]` 记录，真实原因是当时装的 1.1.3 还没有 `HookLog.card()`、
且「详细日志」开关从未打开。1.2.0 的日志已证明 `invoke reached` 与 `card[invoke]` 正常触发。

## 1.2.0（应用替换：支持「复制口令」卡片）

需求澄清后的正确解法。用户场景不是网页链接,而是 **智慧文本服务 → 复制口令**:
口令卡里没有网址,metis 直接认定"这段口令属于夸克",把目标包名写死成 `com.quark.browser`。
按 URL 匹配永远不可能命中,必须按**目标包名**替换。

- 新增「应用替换」规则:`A(原本要打开的) → B(改成打开的)`,独立于 URL 规则。
  典型配置:`com.quark.browser` → `com.quark.clouddrive`。
- 新增 `PackageSwapEngine`:带 JSON 缓存(避免每次 startActivity 重复解析);
  改写前用 Intent 副本探测目标是否可解析,不可解析则原样放行;
  无 data 的 Intent 自动转成 `ACTION_MAIN` + `CATEGORY_LAUNCHER` 拉起目标 App;
  保留原有 extras,口令仍由目标 App 自己从剪贴板/参数解析。
- 新增设置页「应用替换（复制口令）」区块,支持从已安装应用中选择 A/B。
- `ContextImpl.startActivity` 兜底 hook 不再前置过滤 http,改为交给引擎判定
  (带网页数据 或 目标包在替换表里),否则会漏掉"没有网址只有目标包"的口令卡片。

## 1.1.4（兜底启动 hook）

日志取证结论：本机型上流体云卡片**不经过 `TextIntent.invoke`**。四个原 hook 均已安装
（两次重启进程各装一次），但两个会话的模块日志里没有任何一次 `card[...]` / `Redirected` /
`Custom redirect` 记录，说明卡片点击走的是别的路径。

- 新增 `StartActivityHooker`：hook `ContextImpl.startActivity(Intent)` 与
  `(Intent, Bundle)`，在"metis 真正发出跳转"这一步兜住。仅当 Intent 带
  http(s)/heytapbrowser 数据时才处理，命中规则才改写，其余一律原样放行。
- 兜底 hook 安装在**所有** `com.oplus.metis:*` 进程（不再只限 `:text_intent`）。
- `RedirectEngine` 重构为 `apply()`（bean 入口）与 `applyToIntent()`（Intent 入口）共用同一套判定。
- 新增 `HookLog.card()`：取证日志不再依赖「详细日志」开关（本次日志证明该开关当时是关的，
  导致什么都看不到），每进程限额 40 条防刷。
- `RedirectHooker` 入口无条件留痕，用 `invoke reached` 判断主 hook 是否真的被调用。

## 1.1.3（卡片取证 + URL 提取兜底）

- 新增 `IntentInspector`：详细日志会把整张卡片的 Intent 压成一行输出
  （action / package / component / data / flags / clipData / extras），
  用于判断"这张卡到底是不是网页卡片、地址存在哪里"。
- URL 提取不再只看 `dataString`：依次尝试 `dataString` → `clipData` → `extras`，
  覆盖把地址塞在 extra 里的卡片类型。
- 未命中时也留痕：`no rule matched` / `card carries no http(s) url` /
  `browser redirection disabled` 都会在详细日志中给出，不再是"静默不动"。

## 1.1.2（新增规则自检）

- **新增「规则自检」**：自定义规则卡片上多了「自检」按钮，输入一条真实链接即可看到
  逐层结论：命中哪条规则 → 目标是否安装 → 构造出的目标 URI → 目标能否接收 →
  系统上哪些包能开这个 URI → 对目标包逐个探测常见 scheme（✓/✗）→ 一目了然的结论。
  支持「直接试跳一次」，把系统抛出的真实异常打印出来。
  解决的问题：自定义规则失败时，「没命中 / 目标没装 / 目标不认这个 URI」三种情况的
  现象都是"还是原来的浏览器"，肉眼无法区分。
- 自定义规则改写时补 `FLAG_ACTIVITY_NEW_TASK`：宿主若从非 Activity 上下文发起
  `startActivity`，缺这个 flag 会抛 `AndroidRuntimeException`。
- 文档更正：夸克系有两个包名，`com.quark.clouddrive` 是夸克网盘独立版（2026-02 起），
  `com.quark.browser` 是夸克主 App。README 的示例已修正。

## 1.1.1（修复版，基于 1.1.0 预览版）

> 说明：1.1.0 为提交时未经编译验证的预览版，**无法编译**。本版为实际可构建版本。

### 阻断性问题修复

- **修复编译失败**：`CustomRulesEditor.kt` 导入了 `androidx.compose.foundation.layout.weight`，
  该符号在 Compose 中是 `RowScope/ColumnScope` 的成员，导入会命中内部声明，
  编译报错 `Cannot access 'val RowColumnParentData?.weight: Float': it is internal in file`。已删除该导入。
- **修复 `ReflectedTextIntent.setPackageName` 的分支判断**：`Method.invoke` 对返回 void 的
  setter 恒返回 `null`，原来用 `?:` 判断会把每次调用都落到字段反射分支。改为显式 `if/else`。
- **修复 Android 10~12 上规则 4 静默失效**：`BrowserAvailability.isBrowserPackage` 在
  `SDK_INT < 33` 时也调用了 `ResolveInfoFlags`，抛 `NoSuchMethodError` 后被 catch 吞掉，
  结果恒为 `false`。已按 SDK 版本分支。

### 行为与健壮性

- 自定义规则改写 Intent 前，先探测目标 App 是否真能接收该 URI；不能接收则记日志并回退到
  浏览器规则，避免宿主 `startActivity` 抛 `ActivityNotFoundException`（表现为卡片点了没反应）。
- 自定义规则命中但目标包未安装 / URI 构造失败 / 目标不认该 scheme 时，均记一次性日志并回退，
  不再静默。
- `RedirectEngine` 拆出 `tryCustomRule()`，自定义规则与浏览器规则的优先级关系显式化为返回值。

### 工程与发布

- `applicationId` 改为 `com.mo.fkLTY.pro`，LSPosed 模块 `id` 改为 `fkLTY_pro`：
  可与上游原版共存，避免同包不同签名的安装冲突、以及两个模块在 LSPosed 中互相覆盖。
  ⚠️ 请勿同时启用原版与本版，两者会 hook 同一方法。
- `verifyXposedMetadata` 改为扫描 `outputs/apk` 下所有 APK，`assembleRelease` 同样过闸。
- 新增 `.github/workflows/build-apk.yml`：push / PR / 手动触发构建，打 tag 自动挂 Release。
- 新增 `NOTICE`：GPL-3.0 要求的修改声明与归属信息；README 顶部加衍生版本声明。
- 同步 `strings.xml` 的 `app_name` / `xposed_description` 与 `module.prop` 口径。

### 验证状态

- 已在本机 JDK 25 + Gradle 9.5.0 + AGP 9.3.3 + compileSdk 37 完成 `assembleDebug` 构建，
  `verifyXposedMetadata` 通过。
- **未在真机（一加 15 / ColorOS / LSPosed）验证跳转效果**，仍需设备端确认。

## 1.1.0（预览版，无法编译）

- 新增按 URL 匹配的自定义 App 重定向规则。
- 支持域名（含子域名）、URL 包含、正则表达式匹配。
- 支持规则优先级、启用/停用、编辑和删除。
- 支持指定目标包名和可选 Deep Link 模板：`{url}`、`{url_raw}`、`{host}`、`{path}`。
- 自定义规则优先于原有浏览器重定向；未命中时保留原行为。
- 原有 LSPosed Hook 入口、作用域、远程偏好组和三层 Hook 保持不变。
