# GKUIDialer 本地化来电功能 —— 整体架构设计

> 适用仓库：`gx-bangsong/GKUIDialer`（LineageOS `lineage-23.2` 分支的 packages/apps/Dialer）
> 目标平台：随 ROM 平台构建（Soong / `sdk_version: "system_current"`，Android 16 级 API，向下兼容 API 29+ 的 CallScreeningService 语义）
> 语言：新模块用 Kotlin（+ coroutines），存量 Java 代码不动
> UI：View + Material Components（仓库现状），新界面按 MD3 规范实现

---

## 0. 仓库现状勘察（设计的事实依据）

| 事实 | 对设计的影响 |
|---|---|
| 纯 Java，`Android.bp` 的 `srcs` 只含 `java/**/*.java`，无 Kotlin/coroutines 依赖 | 需在 `Android.bp` 增加 `java/**/*.kt`、`kotlinx-coroutines-android`、`kotlin-stdlib`；Soong 原生支持混编 |
| 已有 LineageOS 的 `com.android.dialer.lookup`（ReverseLookup/ForwardLookup + LookupProvider ContentProvider），面向海外源（YellowPages/ZabaSearch/dastelefonbuch） | 新功能不塞进该框架（它是"单一 Provider 单例 + 无缓存 TTL + 无规则外置"的旧模型），而是新建 `numberinfo` 模块；但通过同一个 InCallUI 注入点共存，设置项并列 |
| InCallUI 展示链路：`CallCardPresenter` → `ContactInfoCache`（含 `ContactCacheEntry`）→ `ContactGridManager`/`TopRow`/`BottomRow` | Caller ID 卡片在 `ContactCacheEntry` 上扩展 `NumberInfo` 字段，`ContactGridManager` 增加标记/黄页行 |
| 无 `CallScreeningService` 实现；`blocking/` 只是系统 BlockedNumberContract 的包装 | 骚扰拦截为全新 `callscreening` 模块，Telecom 绑定默认电话应用的 `CallScreeningService`，无需额外申请角色 |
| 网络层现有 volley + `LookupUtils`(HttpURLConnection) | 新模块统一用 `HttpURLConnection`（平台构建无 OkHttp prebuilt 负担），全部跑在 `Dispatchers.IO` |
| Room：AOSP prebuilts 提供 `androidx.room_room-runtime`；`room-compiler` 注解处理插件在部分 ROM 树可能缺失 | 首选 Room；**兜底方案**（编译不过时）：同样的 schema 用 `SQLiteOpenHelper` + 手写 DAO，DAO 接口保持一致，上层零改动。二选一在编码阶段确定 |
| 已有 Dagger2 root component 生成器（`@IncludeInDialerRoot`） | 新模块的单例（ProviderManager、RuleEngine、Repo）用现有 Dagger 体系注入，不引 Hilt |

---

## 1. 模块划分与目录树

新增三个顶层模块（均在 `java/com/android/dialer/` 下），加 InCallUI 集成点补丁：

```
java/com/android/dialer/
├── numberinfo/                          # ★ 核心：号码识别（标记 + 黄页在线源）
│   ├── NumberInfo.kt                    # 归一化数据类 + MarkType 枚举
│   ├── NumberInfoComponent.kt           # Dagger 组件（@IncludeInDialerRoot）
│   ├── NumberInfoRepository.kt          # 对外唯一门面：缓存→本地→在线 的编排
│   ├── provider/
│   │   ├── NumberInfoProvider.kt        # 统一接口
│   │   ├── ProviderManager.kt           # 注册/优先级/并发查询/超时/独立容错
│   │   ├── RuleBasedWebProvider.kt      # 通用"网页解析型"Provider（360/百度/搜狗共用同一个类，只是规则 id 不同）
│   │   ├── BaiduHaomaProvider.kt        # 百度号码库 haoma.baidu.com（同为规则驱动）
│   │   ├── LocalYellowPageProvider.kt   # 本地离线黄页库（同步、零延迟）
│   │   └── ManualMarkProvider.kt        # 用户手动标记（最高优先级）
│   ├── rules/
│   │   ├── ParseRuleEngine.kt           # 加载/校验/执行规则
│   │   ├── RuleRepository.kt            # assets 内置 → filesDir 覆盖 → 远程 URL 热更新
│   │   ├── RuleModel.kt                 # 规则 JSON 的数据模型
│   │   └── extractor/
│   │       ├── Extractor.kt             # 抽取器接口
│   │       ├── RegexExtractor.kt        # 正则（含命名分组）
│   │       ├── JsonPathExtractor.kt     # 轻量 JSONPath（自实现子集，不引第三方）
│   │       └── CssExtractor.kt          # 可选：CSS selector（若引 jsoup 则启用，默认用 Regex 兜底）
│   ├── aggregate/
│   │   └── NumberInfoAggregator.kt      # 多源投票/置信度聚合
│   ├── cache/
│   │   ├── NumberInfoDatabase.kt        # Room DB（缓存表 + 手动标记表 + 拦截记录表）
│   │   ├── NumberInfoCacheDao.kt
│   │   └── ManualMarkDao.kt
│   ├── location/
│   │   └── NumberLocationLoader.kt      # 归属地：内置号段库（assets 压缩表，二分查找）
│   ├── settings/
│   │   ├── NumberInfoSettings.kt        # 总开关/各源独立开关/TTL（SharedPreferences）
│   │   ├── NumberInfoSettingsFragment.kt# 设置页（含隐私说明）
│   │   └── res/xml/numberinfo_settings.xml
│   └── res/values/ (strings/attrs)
│
├── callscreening/                       # ★ 骚扰拦截
│   ├── DialerCallScreeningService.kt    # 官方 CallScreeningService 实现
│   ├── ScreeningDecisionEngine.kt       # 纯函数判定引擎（可单测）：黑/白名单模式 + 三维度规则
│   ├── rule/
│   │   ├── BlockRule.kt                 # 规则模型（号段前缀/归属地/标记类型/黑白名单）
│   │   ├── BlockRuleDao.kt              # Room 表
│   │   └── RulePorter.kt                # 规则导入/导出（JSON 文件，SAF）
│   ├── record/
│   │   ├── BlockedCallRecordDao.kt      # 拦截记录表
│   │   └── BlockedCallsActivity.kt      # 拦截记录列表：查看/恢复(回拨)/加白/删除
│   ├── ui/
│   │   ├── BlockRuleListActivity.kt     # 规则管理列表
│   │   ├── BlockRuleEditorFragment.kt   # MD3 规则编辑：号段快捷 chips + 标记类型 FilterChip 多选
│   │   └── res/layout|values/…
│   └── res/…
│
├── yellowpage/                          # ★ 离线黄页库
│   ├── OfflineYellowPageStore.kt        # 只读预置 db（assets 拷贝/直接 openDatabase），同步查询 <1ms
│   └── YellowPageUpdater.kt             # 可选：离线库整包更新（校验 + 原子替换）
│
└── (集成点改动，非新增)
    ├── incallui/ContactInfoCache.java        # ContactCacheEntry 增加 numberInfo 字段；本地同步查 + 在线异步回填
    ├── incallui/contactgrid/ContactGridManager.java / TopRow.java  # 标记/黄页展示行
    ├── incallui/contactgrid/res/layout/incall_numberinfo_banner.xml # Caller ID 卡片（Pixel 风格）
    ├── app/…settings…                        # 设置入口挂载
    └── AndroidManifest.xml / Android.bp      # 服务注册、Kotlin/Room 依赖

功能资产随模块放置，由 Android.bp 的 `asset_dirs` 打包（目录内的子目录名即 APK assets 路径）：
java/com/android/dialer/numberinfo/assets/numberinfo/rules.json          # 内置解析规则（可被热更新覆盖）
java/com/android/dialer/numberinfo/assets/numberinfo/phone_location.csv  # 号段归属地表（prefix7,省,市,运营商）
java/com/android/dialer/yellowpage/assets/yellowpage/yellowpage.db       # 预置离线黄页库（tools/gen_yellowpage_db.py 生成）
```

单元测试（Robolectric，新增 `android_robolectric_test` 或 `android_test` 目标 `DialerNumberInfoTests`）：

```
javatests/com/android/dialer/numberinfo/
├── ParseRuleEngineTest.kt      # 各 extractor + 规则加载/降级
├── NumberInfoAggregatorTest.kt # 多源投票
├── NumberNormalizerTest.kt     # 号码归一化（+86/86/0 前缀/空格）
└── ScreeningDecisionEngineTest.kt # 拦截判定全矩阵
```

---

## 2. 核心数据结构

### 2.1 `NumberInfo`（统一归一化模型）

```kotlin
enum class MarkType(val id: String) {
    NONE("none"),
    FRAUD("fraud"),           // 诈骗
    HARASSMENT("harassment"), // 骚扰
    TELEMARKETING("telemarketing"), // 推销/广告
    AGENT("agent"),           // 房产/中介
    DEBT_COLLECTION("debt"),  // 催收
    EXPRESS("express"),       // 快递外卖（正向）
    TAXI("taxi"),             // 网约车（正向）
    CUSTOM("custom");         // 用户自定义
    companion object { fun fromId(id: String?): MarkType }
}

data class NumberInfo(
    val number: String,            // E.164 归一化后的号码
    val markType: MarkType = MarkType.NONE,
    val markCount: Int = 0,        // 被标记次数（"被 1283 人标记"）
    val bizName: String? = null,   // 黄页企业名（"顺丰速运客服"）
    val bizCategory: String? = null, // 企业类型标签（"快递物流"）
    val bizVerified: Boolean = false, // 是否认证号码（百度号码认证）
    val location: String? = null,  // 归属地 "广东深圳 移动"
    val source: String,            // 来源标识 "so360" / "baidu" / "sogou" / "haoma" / "local_yp" / "manual"
    val confidence: Float = 0f,    // 0..1，聚合用
    val timestampMillis: Long = 0, // 查询时间（TTL 用）
) {
    val isSpamLike get() = markType in setOf(FRAUD, HARASSMENT, TELEMARKETING, AGENT, DEBT_COLLECTION)
    val isBusiness get() = bizName != null
}
```

### 2.2 Room schema（`numberinfo.db`，version 1）

```
表 number_info_cache             —— 在线查询结果缓存
  number TEXT PK, source TEXT PK    (联合主键：一个号码保留各源结果，聚合在读取时做)
  mark_type TEXT, mark_count INT, biz_name TEXT, biz_category TEXT,
  biz_verified INT, location TEXT, confidence REAL,
  fetched_at INT      -- TTL 判定：now - fetched_at > ttl 即过期
  negative INT        -- 1 = 该源确认"查无标记"，负缓存也要缓存，避免反复打接口

表 manual_mark                   —— 用户手动标记/纠错（最高优先级）
  number TEXT PK, mark_type TEXT, note TEXT, created_at INT

表 block_rule                    —— 拦截规则
  id INT PK AUTO, type TEXT      -- PREFIX / LOCATION / MARK_TYPE / BLACKLIST / WHITELIST
  value TEXT                     -- "170" | "北京" | "fraud,telemarketing" | "+8613800138000"
  enabled INT, created_at INT, note TEXT

表 blocked_call_record           —— 拦截记录
  id INT PK AUTO, number TEXT, blocked_at INT,
  rule_id INT NULL, rule_summary TEXT,   -- 冗余存判定原因快照（规则删了也能看）
  number_info_json TEXT NULL             -- 拦截时刻的识别快照
```

> 若 ROM 树无 room-compiler 插件：同 schema 落到 `SQLiteOpenHelper`，DAO 接口签名不变（suspend 函数内部切 IO），上层代码零感知。

---

## 3. 识别源解耦的落地方式（最高优先级需求）

### 3.1 接口与管理器

```kotlin
interface NumberInfoProvider {
    val id: String                 // "so360" / "baidu" / "sogou" / "haoma" / "local_yp" / "manual"
    val priority: Int              // 越大越优先；manual=1000, local_yp=900, haoma=500, 搜索源=100~300
    val isOnline: Boolean          // 在线源受隐私开关约束
    fun isEnabled(settings: NumberInfoSettings): Boolean
    /** 失败返回 null，绝不抛异常到外层；实现内部自行 try-catch + 打点 */
    suspend fun query(number: String): NumberInfo?
}

class ProviderManager @Inject constructor(...) {
    fun register(provider: NumberInfoProvider)
    /** 本地组（manual/local_yp）同步快速返回 */
    fun queryLocal(number: String): NumberInfo?
    /** 在线组并发查询：coroutineScope + async，每源独立 withTimeoutOrNull(3s)，
        supervisorScope 保证单源崩溃不影响其他源 */
    suspend fun queryOnline(number: String): List<NumberInfo>
}
```

**关键点：360/百度/搜狗三个搜索源不各写一个类**，共用 `RuleBasedWebProvider`，实例化时只传 `ruleId`。新增一个搜索引擎数据源 = 在 rules.json 里加一段配置 + 注册一行 `RuleBasedWebProvider("newsource")`，未来甚至可做到"规则文件里声明即自动注册"。

### 3.2 ParseRuleEngine 与规则 JSON 结构

规则加载优先级：`filesDir/numberinfo/rules.json`（热更新落盘）> `numberinfo/rules.json` assets（内置兜底）。远程更新：设置页手动/定期从 `ruleUpdateUrl` 拉取，**校验 schemaVersion + 全量规则可解析** 后才原子替换落盘，失败保留旧规则。

```jsonc
{
  "schemaVersion": 1,
  "updatedAt": "2026-07-20T00:00:00Z",
  "sources": [
    {
      "id": "so360",
      "name": "360搜索",
      "enabled": true,
      "request": {
        "urlTemplate": "https://www.so.com/s?q={number}&src=dialer",
        "method": "GET",
        "headers": {
          "User-Agent": "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Mobile Safari/537.36"
        },
        "charset": "UTF-8",
        "timeoutMs": 3000
      },
      "extract": {
        "type": "regex",                       // regex | jsonpath | css
        "fields": {
          "markType":  { "pattern": "该号码被\\d+人标记为[\"“]?([^\"”<]+)", "group": 1 },
          "markCount": { "pattern": "该号码被(\\d+)人标记", "group": 1, "coerce": "int" },
          "bizName":   { "pattern": "class=\"mohe-tel-name\"[^>]*>([^<]+)<", "group": 1 },
          "location":  { "pattern": "归属地[:：]\\s*([^<\\s]+)", "group": 1 }
        },
        "successRequires": ["markType|bizName"]   // 任一命中才算有效结果，否则返回 null
      },
      "mapping": {                                // 源词汇 → MarkType 枚举归一化
        "markType": {
          "诈骗": "fraud", "疑似诈骗": "fraud", "骚扰电话": "harassment",
          "广告推销": "telemarketing", "房产中介": "agent", "催收": "debt",
          "快递送餐": "express", "*": "custom"    // 未知词落 custom，原词存 note
        }
      },
      "confidence": 0.7
    },
    {
      "id": "haoma",
      "name": "百度号码库",
      "enabled": true,
      "request": {
        "urlTemplate": "https://haoma.baidu.com/phoneSearch?search={number}&srcid=8757",
        "method": "GET",
        "headers": { "User-Agent": "…" },
        "timeoutMs": 3000
      },
      "extract": {
        "type": "jsonpath",                      // 该站数据在内嵌 JSON 中
        "preRegex": "window\\.__INITIAL_STATE__\\s*=\\s*(\\{.+?\\});",  // 先用正则抠出 JSON
        "fields": {
          "bizName":   { "path": "$.phoneSearch.data[0].name" },
          "bizCategory":{ "path": "$.phoneSearch.data[0].category" },
          "bizVerified":{ "path": "$.phoneSearch.data[0].certified", "coerce": "boolean" },
          "markType":  { "path": "$.phoneSearch.data[0].label" },
          "markCount": { "path": "$.phoneSearch.data[0].markCount", "coerce": "int" }
        },
        "successRequires": ["bizName|markType"]
      },
      "mapping": { "markType": { "诈骗电话": "fraud", "骚扰电话": "harassment", "*": "custom" } },
      "confidence": 0.9
    }
    // sogou / baidu(搜索) 同构，略
  ],
  "ruleUpdateUrl": "https://example.com/gkui/dialer-rules.json"   // 可为空 = 关闭远程更新
}
```

**引擎执行流程**（每步失败都降级返回 null 并记录 `NumberInfoLog.w(source, rule, reason)`）：

```
urlTemplate 填充({number} 做 URL encode)
→ HttpURLConnection 拉取(限 512KB, 3s 超时)
→ [preRegex 预抠取]
→ extractor(type) 按 fields 逐字段抽取
→ successRequires 校验（"a|b" 表示 a 或 b 至少一个非空）
→ mapping 归一化 markType
→ 组装 NumberInfo(source=id, confidence=规则声明值)
```

站点改版时：只改 rules.json 的 `pattern`/`path`/`urlTemplate`，推到 `ruleUpdateUrl` 或让用户在设置页"检查规则更新"，**不改代码、不发版**。

### 3.3 聚合策略（`NumberInfoAggregator`）

```
输入：List<NumberInfo>（各源结果）+ manual（若有）
1. manual 存在 → 直接返回 manual（用户纠错为最高真理）
2. bizName 存在的结果 → 取 (bizVerified desc, priority desc) 首个作为黄页结果
3. 标记聚合：按 markType 分组投票
   得分 = Σ(source.confidence) ，同分取 markCount 最大者
   markCount 取该组最大值；location 取首个非空
4. 黄页与标记可共存（如银行客服也被少量人标记），展示层黄页优先，
   但 markType=FRAUD 且得分超阈值时强制显示警示（防仿冒）
```

---

## 4. 数据流

### 4.1 来电识别（低延迟设计）

```
Telecom 来电
   │
   ├─(1) DialerCallScreeningService.onScreenCall()   [5s 硬时限]
   │      号码归一化 → runBlocking(IO, 总预算 1.5s):
   │        manual/黑白名单/号段/归属地 → 纯本地判定 (<5ms)
   │        标记维度 → 只读缓存 + 本地库，在线源只给 800ms 机会，超时按"无标记"放行
   │      → respondToCall(disallow/silence or allow)
   │      → 拦截则写 blocked_call_record + 静默通知
   │
   └─(2) 放行 → InCallUI 显示
          ContactInfoCache.findInfo()
            ├─ 同步：NumberInfoRepository.peekLocal(number)
            │        = manual ∪ 离线黄页 ∪ 未过期缓存聚合  → 首帧即显示
            └─ 异步：repository.refreshOnline(number) (coroutine, IO)
                     ProviderManager.queryOnline 并发查 → 写缓存 → 聚合
                     → 主线程回调 ContactInfoCacheCallback → ContactGridManager 刷新卡片
```

### 4.2 查询编排（`NumberInfoRepository.query`）

```
query(number):
  n = normalize(number)                      # libphonenumber，仓库已依赖
  if contactsContains(n): return null        # 联系人不查询、不上传（合规硬规则）
  manual = manualDao.get(n)  → 命中直接返回
  local  = offlineYellowPage.get(n) + locationLoader.get(n)
  cached = cacheDao.getValid(n, ttl)         # 含负缓存
  if settings.masterSwitch && needOnline(cached):
      online = providerManager.queryOnline(n)   # 每源独立开关 + try-catch
      cacheDao.upsertAll(online or negative)
  return aggregator.aggregate(manual, local, cached + online)
```

### 4.3 拦截判定（`ScreeningDecisionEngine`，纯函数，全量单测）

```
decide(number, numberInfo, rules, mode):
  1. 白名单命中 → ALLOW（两种模式下都最高优先）
  2. mode == WHITELIST → 非白名单一律 BLOCK
  3. mode == BLACKLIST:
       黑名单精确/前缀命中        → BLOCK(reason=BLACKLIST)
       号段前缀规则命中(最长匹配) → BLOCK(reason=PREFIX)
       归属地规则命中             → BLOCK(reason=LOCATION)
       markType ∈ 选中类型集合 且 markCount ≥ 阈值 → BLOCK(reason=MARK)
  4. 其余 → ALLOW
返回 Decision(action, ruleId, summary) —— summary 直接落拦截记录
```

---

## 5. 三大功能的模块级设计要点

### 5.1 号码标记（Caller ID）
- 数据源：`RuleBasedWebProvider("so360"|"baidu"|"sogou")` + 手动标记；全部走 ParseRuleEngine。
- InCallUI 展示（Pixel 风格卡片，`incall_numberinfo_banner.xml`）：
  - 骚扰类：`?attr/colorErrorContainer` 底 + 警示图标，主文案 `疑似诈骗 · 被 1,283 人标记`，副文案归属地；
  - 黄页类：`?attr/colorSecondaryContainer` 中性可信底 + 商家图标，`顺丰速运客服 · 快递物流`，认证号加 verified 角标；
  - 字符串全部 `strings.xml`（含复数 `plurals`），颜色全部 MD3 attr，不写死色值。
- 通话结束后在 `postcall`/通话详情挂"标记此号码/纠错"入口 → 写 `manual_mark` 表。
- 通话记录列表（calllog）复用同一 Repository，只读缓存不触发联网（滚动列表禁止打接口）。

### 5.2 骚扰拦截
- `DialerCallScreeningService` 在 Manifest 注册 `android.telecom.CallScreeningService` intent-filter + `BIND_SCREENING_SERVICE` 权限保护；本应用是默认电话应用时，Telecom 自动绑定其 `CallScreeningService`，无需 RoleManager 额外申请。注意：用户若在系统设置里另选了来电筛选应用（如 Truecaller），Telecom 会绑定那个应用，本服务不会收到来电，这是预期行为，设置页文案也按此说明。
- 拦截动作可配置：`disallowCall(true) + rejectCall(true) + skipCallLog(false) + skipNotification(true)`，默认**保留系统通话记录但跳过未接通知**，另写自有拦截记录表（可查看/恢复/加白）。
- 规则编辑页（MD3）：
  - 号段输入：`TextInputLayout` + 常用号段 `ChipGroup`（AssistChip：`170` `171` `162` `165` `167` `1690-1699` `950xx` `951xx` `952` `400` `+区号`），点击填入；号段常量集中在 `CommonPrefixes.kt`，与规则导入导出共用。
  - 标记类型：MD3 `FilterChip` 多选，选中态 `secondaryContainer` 配色 + 前置勾选图标，多选结果存为 `MARK_TYPE` 规则的 `value="fraud,telemarketing,…"`。
- 导入导出：`RulePorter` 用 SAF 读写 JSON（版本头 + 规则数组），号段/标记类型枚举同文件承载。

### 5.3 黄页
- 离线优先：`OfflineYellowPageStore` 只读打开；每次打开时比较 assets 内置库与已安装库的 `meta.version`，内置库更新则替换（已下载的更新版本不会被旧内置库覆盖），内置库缺失时保留已安装库；表结构 `yellow_page(number TEXT PK, name TEXT, category TEXT, verified INT)`，同步点查。
- 在线兜底：`BaiduHaomaProvider`（规则驱动，见 3.2 样例），仅本地未命中且开关开启时触发。
- 离线库来源与更新：预置 db 由构建时脚本从 CSV 生成（来源需你提供/授权，exthmUI 方案同款思路）；`YellowPageUpdater` 支持从配置 URL 下载整包 + SHA-256 校验 + 原子替换。

---

## 6. 隐私合规默认策略（建议）

| 项 | 默认值 | 理由 |
|---|---|---|
| 在线识别总开关 | **默认关闭**，首次进入拨号或设置页弹隐私说明征求开启 | 上传来电号码到第三方属个人信息对外提供，按《个人信息保护法》/工信部 164 号文需**单独同意**，不能默认开 |
| 各在线源独立开关 | 跟随总开关，逐源可关 | 最小化 + 用户可控 |
| 本地离线黄页/归属地/手动标记 | 默认开启 | 纯本地，无数据出境 |
| 联系人号码 | **永不在线查询** | 硬编码规则，不可配置 |
| 拦截功能 | 默认关闭（引导页开启） | 拦截具有破坏性，须明示 |
| 查询日志 | 仅本地 logcat 级，规则失效打点不含完整号码（号码打码 `138****0000`） | 便于定位失效源又不泄露 |

风险提示：360/百度/搜狗页面抓取属于对第三方服务的非授权接口使用，存在被封锁 UA/IP、页面反爬导致失效的固有风险（这正是规则外置要解决的），商用发布需自行评估与数据源的授权关系。

---

## 7. 构建接入（`Android.bp` 变更预告）

```
srcs 增加:      "java/**/*.kt"
static_libs 增加: "kotlin-stdlib", "kotlinx-coroutines-android",
                 "androidx.room_room-runtime"(如可用), "androidx.lifecycle_lifecycle-runtime-ktx"
plugins 增加:    "androidx.room_room-compiler-plugin"(如可用)
resource_dirs 增加: numberinfo/callscreening 各 res 目录
aaptflags 增加:  对应 --extra-packages
新增 android_robolectric_test: DialerNumberInfoTests
```

---

## 8. 需要你手动准备/确认的外部项（完整清单随代码给出）

1. Manifest：`CallScreeningService` 注册（我来写）；确认本应用是默认电话应用；如用户另选了来电筛选应用，本服务不会被绑定。
2. 离线黄页库数据来源（CSV/db 均可），或先用我生成的示例小库跑通链路。
3. 归属地号段库：当前随仓库的 `phone_location.csv` 仅为样例（几十行），上线前需替换为完整 7 位号段表，并确认数据许可证可入库。
4. `ruleUpdateUrl` 远程规则托管地址（可暂空，走内置规则）。
5. 存储已定为 SQLiteOpenHelper（Soong 不支持 kapt，不引入 Room），无需再确认。
6. 隐私弹窗文案的法务确认（我给默认文案）。

---

## 9. 实现状态

| 批次 | 内容 | 状态 |
|---|---|---|
| ① core | 规则引擎、多源聚合、缓存、ProviderManager、设置开关（`97223f0`） | 已提交 |
| ② callscreening | `CallScreeningService` 拦截、黑白名单/号段/归属地/标记类型规则、拦截记录、规则导入导出（`912a902`） | 已提交 |
| ③ yellowpage + InCallUI | 离线黄页 provider + 归属地 provider、InCallUI 标记/黄页展示、号码识别设置页、资产打包 | 本次提交 |

第三批要点：
- 本地源（黄页 `local_yp`=900、归属地 `local_geo`=800）同步可用，默认开启，不出境。
- 在线源（haoma/sogou/so360/baidu）受总开关与逐源开关控制，默认关闭；开启需二次确认。
- InCallUI 仅在 `ContactInfoCache` 中做装饰：联系人/紧急号码/语音信箱不查询、不装饰；CP2 结果覆盖 entry 时会重新应用已得到的号码识别结果，避免竞态丢失。
- 展示：spam 标记在来电卡片显示具体标签（如“疑似诈骗 · 被 1,283 人标记”），无标签时回退通用文案；商家显示名仅在号码本身无 CP2 名称时替换。
- 测试：纯逻辑测试（JVM）覆盖规则解析、聚合、拦截决策、标签格式化、归属地表解析、黄页 provider。

