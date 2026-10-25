# feedah → KMP 迁移计划（PORT_PLAN）

> 源仓库：`C:\code3\feedah`（Expo 57 / RN 0.86 / expo-router / expo-sqlite + drizzle-orm）
> 目标仓库：本仓库 `feedah-kt`（KMP + Compose Multiplatform 1.12.1，分支 `mvp/261025`）
> 规划方式：两个分析 agent（TS 侧全量盘点 / KMP 技术栈评估）+ 一个红队评审 agent，主会话决策。
> 日期：2026-10-25

## 0. 排除面（用户指定：Convex 与通知不做）

不迁移：`src/cloud/*`（6 文件）、`convex/`、`src/lib/reminders.ts`、`src/lib/notification-texts.ts`、
settings 键 `remindersEnabled` / `reminders` / `accountEmail` 及其 UI 组（含 DateTimePicker 依赖）、
`db/studio.tsx`（drizzle dev 工具）、legacy 库 reset 与 `mealTimes` 旧迁移路径。

保留：`push-once`（800ms 防连点，与通知无关）、`silentHintShown`（iOS 静音键 TTS 提示，属语音体验）。

## 1. 技术选型

| 能力 | 选择 | 版本 | 备注 |
|---|---|---|---|
| DB | SQLDelight | 2.4.1 | 五 target 唯一全覆盖；.sq 直接写最终形态 schema v1（新 app 无老库） |
| 驱动 | android-driver / native-driver / sqlite-driver(JDBC) | 2.4.1 | DriverFactory expect/actual，**随 M2 首个 commit 落地** |
| 导航 | org.jetbrains.androidx.navigation:navigation-compose | 2.10.0 | @Serializable 路由替代 expo-router file-based |
| 序列化 | kotlinx-serialization | 1.12.0 | 路由 + seed JSON |
| 日期 | kotlinx-datetime | 0.8.0 | `LocalDate.atStartOfDayIn(TZ.currentSystemDefault())` 天然 DST 安全 |
| 协程 | kotlinx-coroutines | 1.11.0 | 模板既定 |
| 键值 | 继续用 SQLite `meta` 表（JSON value） | — | 不引 multiplatform-settings，行为与原 app 一致 |
| TTS | 自建 `TtsPlayer` interface | — | android: TextToSpeech；iOS: AVSpeechSynthesizer；jvm: no-op |
| UI | CMP 1.12.1（保持模板版本，不升级）+ material3 1.12.0-alpha03 | — | 自定义色板，M3 仅作组件库 |

Target：**android / jvm / iosArm64 / iosSimulatorArm64**。
移除 js / wasmJs / webApp（SQLDelight web 需 worker driver + CMP web 仍 Beta；恢复路径：加回 target +
`web-worker-driver` + webApp 模块，见 README）。

## 2. 包结构（`com.time1043.feedah_kt`）

```
shared/src/commonMain/kotlin/com/time1043/feedah_kt/
├── App.kt              # 根：初始化闸 + ThemeProvider + NavHost + NavigationBar
├── logic/              # 纯 Kotlin：DateOps / Format / Color / Daily / pushOnce / DayJump / SearchReset
├── data/db/            # .sq schema、DriverFactory、FeedahDatabase 封装、Repo、Seed、SettingsStore、UsageTracker
├── platform/           # TtsPlayer interface + 工厂
├── ui/
│   ├── theme/          # FeedahColors(11 色×2) + spacing/radius/fontSize tokens + CompositionLocal
│   ├── navigation/     # @Serializable Routes
│   ├── components/     # Screen / BucketTabs / ProgressBar / RoundBar / Heatmap / WordCard
│   └── screens/        # Home / Words / Stats / Settings / Feed / Review / Search / WordDetail
shared/src/commonMain/sqldelight/com/time1043/feedah_kt/db/   # *.sq
shared/src/commonMain/composeResources/files/data/            # 2050.json / 700.json / 370.json
```

## 3. 里程碑与 commit 序列（31 个基准）

**M0 基建**：① 本计划文档 ② 移除 web target/webApp ③ 依赖接入 + iosArm64 K/N 编译 spike。
**M1 纯逻辑**：④ DateOps（DST/跨年用例）⑤ Color/Format/pushOnce ⑥ computeDailyUsage（轻量行类型解耦 repo）⑦ DayJump(StateFlow)/SearchReset(SharedFlow)。
**M2 数据层**：⑧ .sq schema v1（9 表、BOOLEAN、idx_word_flag）+ DriverFactory 三端 actual ⑨ seed（幂等：word_count 相等只补 progress；不等则删词重插**清 flag 保进度**；composeResources JSON；事务逐行插无参数上限问题）⑩ 写域 advancePointer/startNextRound/setFlag（事务+高水位+startedAt 首写）⑪ 读域 flagged/roundWords/history/roundFlagCounts ⑫ 统计域 dailyStat/dailyPointer/getWordsCompletedOn（**改整桶取回内存过滤**，原 inArray 最多 2050）/recoverRoundStartedAt ⑬ reviewCursor(MAX 单调) + SettingsStore（meta JSON，砍 3 键，SPEECH_RATE_VALUE）。
**M3 平台**：⑭ TtsPlayer（android/jvm/ios actual）⑮ UsageTracker（**4 个 flush 触发点**：10s ticker / AppState / settle / 失焦；**余数保留在内存**；iOS 用 didEnterBackgroundNotification，不 stub）。
**M4 主题+导航**：⑯ Theme ⑰ NavHost+4 tabs+stack routes ⑱ Screen/SafeArea 壳。
**M5 组件**：⑲ ProgressBar(scrub) ⑳ WordCard（meaningLines 纯函数测试、forms 12 格、TextAutoSize.StepBased(24..48sp) ≙ minimumFontScale 0.5）㉑ Heatmap（Canvas+pointerInput，`<=` 分级，月标签变化检测）㉒ RoundBar（pixelCount=min(len,w)，红>绿>灰）。
**M6 八屏**：㉓ Home+Words ㉔ Stats（loadRounds **兜底循环全量移植**）㉕ Feed（Pager settle 策略见 §5）㉖ Review（三 flavor + cursor + **review 也计 feed 时间**）㉗ Search ㉘ WordDetail。
**M7 收尾**：㉙ resetDatabase + 设置页全量（清空数据→删库重开→resetUsage→reload）㉚ 回归验证 ㉛ README。

## 4. 验证梯度

- 每 commit：`./gradlew :shared:jvmTest`（M1/M2 全量语义锁）
- M2 起：`./gradlew :shared:compileKotlinIosArm64`（Windows 可跑 K/N 编译）
- M4/M6 关键点：`./gradlew :androidApp:assembleDebug` + `:desktopApp:build`

## 5. Feed 屏 Pager settle 策略（最高回归风险）

```kotlin
val pager = rememberPager(pageCount = { words.size + 1 })   // +1 = roundEnd footer
var suppress by remember { mutableStateOf(false) }
val dragged = pager.interactionSource.collectIsDraggedAsState()

LaunchedEffect(pager) {
  snapshotFlow { pager.settledPage }.collect { page ->
    if (!suppress) handleSettle(page)   // 落点页 index i ⇒ 完成上一张（position=i，1-based）
    suppress = false
  }
}
LaunchedEffect(dragged) { if (dragged.value) suppress = false }   // ≙ onMomentumScrollBegin
```

- `handleSettle`：仅 study 模式且 `page > pointer`；footer 分支 `pointer >= words.size - 1 && page > pointer` →
  `advancePointer(bucketId, words.size)`；`pointer >= words.size` → `startNextRound`；
  之后 `delay(2000)` linger 期间吞手势，`suppress = true; pager.scrollToPage(0)`。
- 初始定位 / jumpTo / resumeStudy 全部 `suppress = true` + **scrollToPage（禁 animate，会连续触发 settledPage）**。
- settle 后 speak + `flushUsage()`；browse 模式零记录。
- CMP Pager 页高天然满约束，原 viewport 测量 / initialScrollIndex 漂移 / safe-area 重对齐一类问题整体消失；
  feed 与 review 共用同一套逻辑。

## 6. 语义对齐清单（M7 回归逐项过）

- [ ] settle 落卡=完成上一张；pointer 单调；footer 可达 wordCount；幂等 startNextRound
- [ ] setFlag：flag 时 upsert round_word（可与 reached 合并）；unflag 时 reached=false 删行 / true 清 flagged
- [ ] daily_pointer 高水位差分重建日词数；getWordsCompletedOn 跨轮取模 `((gp-1) % wordCount)+1`
- [ ] searchWords LIKE：`%`/`_`/`\`(翻倍转义) + 无通配自动 `%q%` + `|` 多选 + forms JSON 文本 LIKE + matchMeaning
- [ ] seed：word_count 相等幂等 / 不等重插清 flag 保进度
- [ ] usage 四触发点 flush + 余数保留；review 计 feed 时间
- [ ] review cursor 三 key（`day:`/`round:`/`flagged:`）单调 MAX
- [ ] recoverRoundStartedAt + stats loadRounds 兜底循环
- [ ] heatmap 阈值 `<=` 分级、跨年前导/收尾列、月标签变化检测
- [ ] 主题 11 色×2 与原 tokens 逐值一致
