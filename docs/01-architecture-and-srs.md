# 中医工厂 · 第一阶段方案设计
## 整体架构 + 数据库设计 + 背诵卡片与遗忘曲线模块

> 项目：中医工厂（TCM Factory）
> 定位：面向中医学专业大学生的中医考研 / 执业医师资格考试备考工具
> 开发者：厂吴
> 阶段：第一阶段方案评审稿（待确认后进入编码）
> 日期：2026-10-12

---

## 0. 第一性原理拆解：这款软件到底在解决什么

先把"需求清单"还原成"本质问题"，再倒推设计。中医备考的核心矛盾只有一个：

> **考生面对的是 3000+ 个高度相似、极易混淆的离散知识点（中药功效、方剂组成、穴位定位、经典条文），而人脑的自然遗忘速度远超重复速度。**

由此推导出三条不可动摇的设计公理：

| 公理 | 推论 | 对架构的约束 |
|---|---|---|
| **A1 记忆的本质是"在即将遗忘时复习"** | 系统必须精确掌握每一条知识的遗忘临界点 | 调度算法（SRS）必须是一等公民，独立成模块、可单测、可配置 |
| **A2 学习动作必须零摩擦** | 拿起手机 3 秒内能开始学，单张卡片决策成本 < 1 秒 | 卡片交互只允许二元决策（左/右滑动），高级功能必须藏在长按/二级页面 |
| **A3 知识必须可溯源、可校验** | 中医知识点错一个字就是医疗事故级别的错误 | 每条知识都带 `sourceRef` 字段（如 `GB/T 12346-2021 §5.1`），数据入库有校验流水线 |

**这三条公理直接决定了：** Room 本地库是唯一事实源（离线优先）、SRS 用纯 Kotlin 无 Android 依赖实现（可在 JVM 上跑单元测试验证算法正确性）、卡片表必须内联 SRS 状态字段（避免 JOIN 拖慢每次滑动）。

**明确不做的事（第一阶段）：** 不做账号体系、不做云同步、不做社交、不做 AI 生成题目。这些都会稀释核心体验，留到后续迭代评估。

---

## 1. 技术栈选型与理由

### 1.1 最终选型

| 层 | 选型 | 版本基线 |
|---|---|---|
| 语言 | Kotlin | 2.0+ |
| UI | Jetpack Compose + Material 3 | Compose BOM 2024.x |
| 架构 | MVVM + 单向数据流（UDF）+ Clean 分层 | — |
| 依赖注入 | Hilt | 2.51+ |
| 本地数据库 | Room（SQLite） | 2.6+，KSP 编译 |
| 键值配置 | DataStore Preferences | 1.1+ |
| 后台任务 | WorkManager | 2.9+ |
| 序列化 | kotlinx.serialization | 1.6+ |
| 导航 | Navigation Compose | 2.7+ |
| 构建 | Gradle 8.x + AGP 8.x + JDK 17 | — |
| SDK 基线 | minSdk 26 / targetSdk 35 / compileSdk 35 | Android 8.0 起 |

### 1.2 为什么是 Kotlin + Compose（而不是 Flutter / 原生 View）

1. **手势即状态**。背诵模块的核心体验是"卡片跟手滑动 + 松手飞出"。Compose 里这一整套是 `Modifier.pointerInput` + `Animatable` + `graphicsLayer`，代码量约 120 行，且状态与视觉强绑定、不会出现帧不同步。传统 View 走 `RecyclerView + ItemTouchHelper` 要处理 ViewHolder 复用与动画冲突，是这个需求里最经典的坑。
2. **古籍排版可控性**。中医古籍需要大量中文字形、竖排（后续）、自定义字号/行距/段间距。Compose 的 `Text` + `ParagraphStyle` 比 Flutter 的 TextPainter 更容易做精细控制，也方便接 `可变字体`。
3. **离线包体积可控**。Compose 在 R8 后对未使用组件完全裁剪；Flutter 引擎固定增加约 8–10MB，对一个"内置几千条知识点 + 古籍文本"的 APK 来说是纯浪费。
4. **这是用户明确的倾向**，且与该 App 的技术画像（单机、离线、重算法、重排版）高度匹配。

### 1.3 一个务实的架构决策：**第一阶段用单 Gradle 模块 + 强包边界**

很多架构方案一上来就拆 8 个 Gradle module，对单人/小团队是纯粹的效率损耗（构建变慢、跨模块改代码要改 build.gradle、IDE 索引变重）。

**本方案：单 `:app` 模块，但通过包结构强制分层，并保证依赖方向单向向下。**

```text
app/src/main/java/com/cn/tcmfactory/
├── TcmApp.kt                    # Application，Hilt 入口
├── MainActivity.kt              # 唯一 Activity，承载 Compose NavHost
├── di/                          # Hilt Module：DatabaseModule / RepoModule / SrsModule
│
├── core/                        # 无业务语义的基础设施
│   ├── common/                  # Result 封装、DispatcherProvider、时间/字符串扩展
│   ├── designsystem/            # 主题、配色、字体、通用组件（Card/EmptyState/ProgressRing）
│   ├── database/                # Room：entity / dao / migration / PrebuiltDatabase
│   ├── datastore/               # SettingsRepository（今日上限、日界、主题、字号…）
│   └── srs/                     # ★ 遗忘曲线算法：纯 Kotlin，零 Android 依赖
│
├── data/
│   ├── repository/              # CardRepository / BookRepository / QuizRepository / StatsRepository
│   ├── importer/                # CardImporter / QuestionImporter / BookImporter（JSON/TXT/CSV）
│   ├── exporter/                # 进度导出、卡片包导出（JSON）
│   └── mapper/                  # Entity ↔ Domain Model
│
├── domain/
│   ├── model/                   # Card / Deck / SrsSnapshot / StudyQueue / Question …
│   └── usecase/                 # BuildStudyQueueUseCase / ReviewCardUseCase /
│                                # UndoReviewUseCase / GetTodayProgressUseCase …
│
└── feature/                     # 每个包 = 一个页面族（UiState + ViewModel + Screen）
    ├── home/                    # 底部导航骨架、今日概览
    ├── recite/                  # ★ 第一阶段：牌组页 / 背卡页 / 会话完成页 / 卡片管理
    ├── reader/                  # 第二阶段：古籍书架 / 阅读器 / 书签
    ├── quiz/                    # 第三阶段：刷题 / 错题本 / 模拟考
    ├── stats/                   # 复习热力图、正确率趋势
    └── settings/                # 设置（含"开发者：厂吴"备注）
```

**依赖方向铁律：** `feature → domain → data → core`，禁止反向引用。用 Detekt 的 `ForbiddenImport` 规则在 CI/构建时强制拦截，防范架构腐化。

**未来拆分路径：** 当 `:core:srs` 和 `:feature:*` 稳定后，可零成本平移为独立 Gradle module（包名即模块名，无需重构业务代码）。

---

## 2. 整体架构设计

### 2.1 分层与数据流

```text
┌──────────────────────────── Compose UI 层 ────────────────────────────┐
│  Screen (Stateless)  ⇄  ViewModel (StateFlow<UiState>)                 │
│  · 单向数据流：UI 发 Event → VM 处理 → 新 UiState → UI 重绘            │
│  · 卡片滑动手势是「本地即时状态」，松手后才提交 Event，保证 60fps       │
└───────────────────────────────┬───────────────────────────────────────┘
                                │ Event / UiState
┌───────────────────────────────▼───────────────────────────────────────┐
│  Domain 层（纯 Kotlin，无 Android 依赖，全部可 JVM 单测）              │
│  UseCase: BuildStudyQueue / ReviewCard / UndoReview / GetTodayProgress │
│  SrsScheduler: 输入 SrsSnapshot + Rating → 输出新 SrsSnapshot          │
└───────────────────────────────┬───────────────────────────────────────┘
                                │
┌───────────────────────────────▼───────────────────────────────────────┐
│  Data 层                                                               │
│  Repository ──► Room DAO（Flow 响应式查询）                            │
│            └─► DataStore（设置）                                       │
│            └─► Importer/Exporter（JSON / TXT / CSV 文件流）             │
└───────────────────────────────┬───────────────────────────────────────┘
                                │
                    ┌───────────▼───────────┐
                    │  SQLite (Room) 唯一事实源 │
                    └───────────────────────┘
                    ↑ 首次启动从 assets 拷贝预置库
```

### 2.2 关键设计原则

**原则 1：不预生成"学习任务表"。**
绝大多数同类 App 会在每天凌晨批量写入一张 `daily_tasks` 表，结果是要处理任务过期、时区变更、算法升级后旧任务与新算法冲突等无穷无尽的脏数据问题。

**本方案改为「实时查询组装」**：每次进入背诵页，从 `cards` 表按 `srsDueAt` 直接查询到期卡片，用 `LIMIT` 施加每日上限。好处是：算法改了历史数据自动按新规则重新解释；不需要"重建任务"逻辑；表少一张，事务少一半。

**原则 2：所有 SRS 状态内联在 `cards` 表。**
不单独建 `srs_states` 表。理由：1:1 关系下独立表只会带来 JOIN 开销和事务一致性负担；而"导出卡片包时不带进度"这个诉求，用 Room 的 `@Embedded` + 导出时构造 DTO 即可解决，不需要在数据库层拆表。

**原则 3：复习日志是"不可变的追加日志"。**
`review_logs` 只增不改不删。它是后续一切数据洞察（正确率曲线、学习时段分析、算法效果调优）的唯一数据源，也是"撤销上一步"的实现基础。

### 2.3 核心交互流程（背诵会话）

```text
用户点「开始背诵」
   │
   ├─► BuildStudyQueueUseCase
   │     1. 读取设置：newPerDay / reviewPerDay / 日界小时 / 选中牌组
   │     2. 查询 LEARNING & RELEARNING 卡片（当日必须完成，不受上限约束）
   │     3. 查询到期的 REVIEW 卡片，按 srsDueAt 升序（逾期越久越靠前），LIMIT reviewPerDay
   │     4. 查询 NEW 卡片，按牌组顺序 + sortOrder，LIMIT (newPerDay - 今日已学新卡数)
   │     5. 交错排序：短间隔卡 → 逾期复习卡 → 新卡（每 5 张新卡插入 1 张复习卡，防疲劳）
   │     6. 返回 StudyQueue
   │
   ├─► 背卡页逐张呈现（本地状态：当前索引、卡片偏移量、是否已翻面）
   │
   └─► 每次滑动 → ReviewCardUseCase
         1. 事务内：更新 cards 的 SRS 字段
         2. 追加 review_logs
         3. 更新 study_daily_stats
         4. 快照写入 undo 缓冲区（内存，仅保留最近 1 步）
```

---

## 3. 数据库设计（Room / SQLite）

### 3.1 ER 总览

```text
decks ──1:N──► cards ──1:N──► review_logs
                 │                  │
                 │                  └── 统计聚合 ──► study_daily_stats
                 │
                 └──N:M──► questions?（无直接关系，独立模块）

books ──1:N──► book_chapters
      └─1:N──► book_marks

questions ──1:N──► quiz_records ──► quiz_sessions
          └─1:1──► wrong_questions
```

### 3.2 表结构定义（第一阶段实现：前 4 张；其余表一次设计到位，避免后续 Migration 地狱）

#### `decks` — 牌组 / 科目

| 列 | 类型 | 说明 |
|---|---|---|
| id | INTEGER PK AI | |
| name | TEXT NOT NULL | 如「中药学 · 解表药」 |
| subject | TEXT NOT NULL | 枚举：`TCM_BASIC`/`TCM_DIAG`/`MATERIA_MEDICA`/`FORMULA`/`INTERNAL_MED`/`ACUPUNCTURE`/`CLASSICS`/`CUSTOM` |
| parentId | INTEGER NULL FK(decks.id) | 支持二级分类（科目 → 章节） |
| sortOrder | INTEGER NOT NULL DEFAULT 0 | |
| isBuiltin | INTEGER NOT NULL DEFAULT 0 | 内置牌组不可删除，只能停用 |
| createdAt | INTEGER NOT NULL | epoch millis |

> `subject` 枚举严格对齐 **307 中医综合** 的科目划分（中基、中诊、中药、方剂、中内、针灸 + 经典），未来接执业医师考试时可直接复用。

#### `cards` — 背诵卡片（★ 核心表，内联 SRS 状态）

| 列 | 类型 | 说明 |
|---|---|---|
| id | INTEGER PK AI | |
| deckId | INTEGER NOT NULL FK(decks.id) | |
| type | TEXT NOT NULL DEFAULT 'BASIC' | `BASIC` / `CLOZE` / `QA` / `ACUPOINT` |
| front | TEXT NOT NULL | 正面（题干）。CLOZE 类型用 `{{c1::藏}}` 语法 |
| back | TEXT NOT NULL | 背面（答案） |
| extra | TEXT NULL | 补充说明 / 延伸 / 对比记忆点 |
| tags | TEXT NULL | JSON 数组，如 `["高频","易混淆"]` |
| sourceRef | TEXT NULL | ★ 权威出处，如 `GB/T 12346-2021 §5.1`、`《中药学》人卫版` |
| sortOrder | INTEGER NOT NULL DEFAULT 0 | |
| isSuspended | INTEGER NOT NULL DEFAULT 0 | 暂停（不进入队列） |
| note | TEXT NULL | 用户个人笔记 |
| createdAt / updatedAt | INTEGER NOT NULL | |
| **srsState** | TEXT NOT NULL DEFAULT 'NEW' | `NEW` / `LEARNING` / `REVIEW` / `RELEARNING` |
| **srsLevel** | INTEGER NOT NULL DEFAULT 0 | 巩固等级 0–11，指向阶梯表索引 |
| **srsEase** | REAL NOT NULL DEFAULT 2.5 | 难度因子 EF，钳制在 [1.3, 2.8] |
| **srsIntervalMin** | REAL NOT NULL DEFAULT 0 | 当前间隔（分钟，REAL 以支持 0.5 天这类值） |
| **srsDueAt** | INTEGER NOT NULL | ★ 下次到期时间戳（epoch millis） |
| **srsLastReviewAt** | INTEGER NULL | |
| **srsReps** | INTEGER NOT NULL DEFAULT 0 | 总复习次数 |
| **srsLapses** | INTEGER NOT NULL DEFAULT 0 | 遗忘次数（衡量难卡） |

**索引策略（直接决定滑动是否卡顿）：**

```sql
CREATE INDEX idx_cards_due   ON cards(srsState, srsDueAt);            -- 队列主查询
CREATE INDEX idx_cards_deck  ON cards(deckId, sortOrder);             -- 牌组页
CREATE INDEX idx_cards_new   ON cards(deckId) WHERE srsState='NEW';   -- 新卡计数（部分索引）
```

> 说明：`dueAt` 用 **epoch millis** 而非"第几天"整数。理由是 5min / 30min / 12h 这三个亚日级节点必须精确到分钟；同时天然免疫时区切换。日界（默认凌晨 04:00）只在"每日上限计数"时使用，不影响调度精度。

#### `review_logs` — 复习日志（只增不改）

| 列 | 类型 | 说明 |
|---|---|---|
| id | INTEGER PK AI | |
| cardId | INTEGER NOT NULL FK(cards.id) | |
| reviewedAt | INTEGER NOT NULL | |
| rating | TEXT NOT NULL | `AGAIN` / `HARD` / `GOOD` / `EASY` |
| prevLevel → nextLevel | INTEGER, INTEGER | 便于回放算法决策 |
| prevIntervalMin → nextIntervalMin | REAL, REAL | |
| srsEaseAfter | REAL NOT NULL | |
| elapsedMs | INTEGER NOT NULL | 单卡决策耗时（用于剔除"手滑"样本） |
| mode | TEXT NOT NULL | `DAILY` / `QUIZ` / `CRAM` |
| deckId | INTEGER NOT NULL | 冗余存储，避免统计时 JOIN |

```sql
CREATE INDEX idx_logs_date ON review_logs(reviewedAt);
CREATE INDEX idx_logs_card ON review_logs(cardId, reviewedAt);
```

#### `study_daily_stats` — 每日统计（免去每次现算）

| 列 | 类型 |
|---|---|
| date | TEXT PK（`yyyy-MM-dd`，按自定义日界切割） |
| newCards / reviewCards / quizCount / correctCount | INTEGER |
| studyMinutes | INTEGER |
| remembered / forgotten | INTEGER |

#### `books` / `book_chapters` / `book_marks` — 古籍（第二阶段启用，先建表）

- `books`：id, title, author, dynasty, format(`TXT`/`EPUB`), fileName, filePath, fileSize, encoding, charCount, chapterCount, lastReadCharOffset, lastReadAt, importedAt, isBuiltin
- `book_chapters`：id, bookId, index, title, startCharOffset, endCharOffset, charCount
- `book_marks`：id, bookId, charOffset, chapterIndex, selectedText, note, colorTag, createdAt

> 设计要点：古籍正文**整体作为一个字符串存储于文件系统**（不拆行入库），数据库只存**字符偏移量索引**。这是古籍阅读器的性能关键——几 MB 的文本入库后逐行查询会拖垮滚动性能。

#### `questions` / `quiz_sessions` / `quiz_records` / `wrong_questions` — 刷题（第三阶段启用，先建表）

- `questions`：id, subject, chapter, type(`SINGLE`/`MULTI`/`JUDGE`/`FILL`), stem, optionsJson, answerJson, explanation, difficulty, sourceRef, tags, packId
- `quiz_sessions`：id, mode(`SEQUENTIAL`/`RANDOM`/`CHAPTER`/`WRONG`/`MOCK`), subject, chapter, startedAt, finishedAt, totalCount, correctCount, elapsedMs
- `quiz_records`：id, sessionId, questionId, userAnswerJson, isCorrect, answeredAt, elapsedMs
- `wrong_questions`：questionId PK, wrongCount, correctStreak, lastWrongAt, status(`ACTIVE`/`MASTERED`/`REMOVED`), note, addedAt

> `correctStreak` 用于"连对 2 次自动移出错题本"的温和策略（可配置）。

### 3.3 预置数据分发策略（重要）

内置知识库（中药 / 方剂 / 穴位 / 经典条文）体量预估：**3000–5000 张卡片**，对应 JSON 约 2–4 MB。两种方案：

| 方案 | 做法 | 取舍 |
|---|---|---|
| **A. assets 放预编译 SQLite** | 用桌面端脚本生成 `.db`，放 `assets/`，首启时 `createFromAsset()` 拷贝 | ✅ 首启即用、无解析耗时、体积最小<br>❌ 迭代数据需重新出包 |
| **B. assets 放 JSON，首启解析入库** | 首次启动在 `WorkManager` 中解析入库，带进度条 | ✅ 便于热更新与人工校对<br>❌ 首启需 3–8 秒 |

**决定：采用方案 A 为主 + 方案 B 作为增量更新通道。**
即：主知识库预编译进 APK；后续如果发布知识库更新包（JSON），走方案 B 增量导入，并通过 `knowledge_pack_versions` 表记录已装版本。这样既保证首次体验，又保留长期迭代能力。

---

## 4. 遗忘曲线算法设计（★ 本阶段核心）

### 4.1 为什么不用标准 SM-2

用户给的节点是 **5min / 30min / 12h / 1d / 2d / 4d / 7d / 15d** —— 这是一条**确定性的固定阶梯**。标准 SM-2 每次按 `interval × EF` 递推，会产生 1d → 2.5d → 6.2d → 15.6d 这种非整数、不可预测的间隔，与用户预期不符，也不利于"我今天要复习几天后的内容"这种计划感。

**因此本方案采用「固定阶梯（Leitner 阶梯）+ 难度因子微调（EF）」的混合模型**，命名为 **TCM-SRS**。

### 4.2 阶梯表（LADDER）

| Level | 间隔 | 语义 | 状态 |
|---|---|---|---|
| 0 | — | 尚未学习的新卡（立即可学） | `NEW` |
| 1 | 5 min | 首次学习后的第一次巩固 | LEARNING |
| 2 | 30 min | 当天巩固 | LEARNING |
| 3 | 12 h | 跨睡眠巩固 | LEARNING |
| 4 | 1 d | **毕业进入长期记忆** | REVIEW |
| 5 | 2 d | | REVIEW |
| 6 | 4 d | | REVIEW |
| 7 | 7 d | | REVIEW |
| 8 | 15 d | | REVIEW |
| 9 | 30 d | | REVIEW |
| 10 | 60 d | | REVIEW |
| 11 | 120 d | | REVIEW |
| 12 | 180 d | 上限（此后维持，可配置） | REVIEW |

> **`level` 与阶梯严格一一对应**：卡片处于 level L 时，下次复习的基准间隔就是 `LADDER[L]`。
> level 0 是特殊档，专指"尚未学习"，所以新卡第一次记住后落到 level 1 → 5 分钟后复习，与艾宾浩斯最初节点一致。
> Level 1–3 = 「学习期」（5min / 30min / 12h），level 4+ = 「复习期」（1 天起）。

### 4.3 状态机与评分规则

**默认 UI 只暴露二元决策**（左滑=未记住，右滑=已记住），映射为 `AGAIN` / `GOOD`。
**长按卡片（或上滑展开）** 可进入四档精评：`AGAIN` / `HARD` / `GOOD` / `EASY`。

| 评分 | level 变化 | 下次间隔 | EF 变化 | 状态迁移 |
|---|---|---|---|---|
| **AGAIN**（未记住） | `max(1, level - 4)` | `now + 5 min` | `-0.20` | → `RELEARNING`；若原状态为 `NEW` 则 → `LEARNING` |
| **HARD**（模糊） | `max(1, level - 1)` | `now + LADDER[level] × 0.6` | `-0.15` | 保持 |
| **GOOD**（已记住） | `level + 1`（上限 11） | `now + LADDER[level]` | `+0.05` | level ≥ 3 时 → `REVIEW` |
| **EASY**（熟练） | `level + 2`（上限 11） | `now + LADDER[level]` | `+0.15` | level ≥ 3 时 → `REVIEW` |

**EF 微调规则（仅对 level ≥ 4 生效）：**

```text
efFactor    = clamp(srsEase / 2.5, 0.70, 1.60)
finalInterval = LADDER[level] × efFactor        // 仅 level ≥ 4
```

> 为什么短阶梯不做 EF 微调？因为这会把 "5 分钟" 变成 "4.2 分钟" 这种毫无意义的扰动，反而增加复杂度。个性化调优只在 ≥ 2 天量级才有统计意义。

**抖动（Jitter）防堆积：**
天级间隔（level ≥ 4）追加 `±5%` 的随机抖动（确定性种子 = `cardId`，保证同一卡片每次计算结果一致，可复现）。作用是避免同一批导入的卡片永远在同一天同时到期，导致某天复习量爆炸。

### 4.4 算法伪代码（纯 Kotlin，零 Android 依赖）

```kotlin
enum class SrsRating { AGAIN, HARD, GOOD, EASY }
enum class SrsState { NEW, LEARNING, REVIEW, RELEARNING }

object TcmSrsScheduler {

    // 单位：分钟
    private val LADDER_MIN = doubleArrayOf(
        0.0,                            // 0     新卡（尚未学习）
        5.0, 30.0, 720.0,               // 1,2,3 学习期：5min / 30min / 12h
        1440.0, 2880.0, 5760.0,         // 4,5,6 1d / 2d / 4d
        10080.0, 21600.0,               // 7,8   7d / 15d
        43200.0, 86400.0,               // 9,10  30d / 60d
        172800.0, 259200.0              // 11,12 120d / 180d
    )
    private const val MAX_LEVEL = 12
    private const val GRADUATION_LEVEL = 4
    private const val AGAIN_STEP_MIN = 5 * 60_000L    // 忘记后 5 分钟重学

    fun schedule(
        card: SrsSnapshot,
        rating: SrsRating,
        now: Long,
        config: SrsConfig = SrsConfig()
    ): SrsSnapshot {
        var level = card.level
        var ease = card.ease
        val prevState = card.state

        when (rating) {
            SrsRating.AGAIN -> {
                level = maxOf(1, level - config.lapseStep)   // 默认 4
                ease  = maxOf(config.easeMin, ease - 0.20)
                return card.copy(
                    level = level,
                    ease  = ease,
                    state = if (prevState == SrsState.NEW) SrsState.LEARNING else SrsState.RELEARNING,
                    intervalMin = AGAIN_STEP_MIN / 60_000.0,
                    dueAt = now + AGAIN_STEP_MIN,
                    lapses = card.lapses + 1,
                    reps = card.reps + 1,
                    lastReviewAt = now
                )
            }
            SrsRating.HARD -> {
                level = maxOf(1, level - 1)
                ease  = maxOf(config.easeMin, ease - 0.15)
            }
            SrsRating.GOOD -> {
                level = minOf(MAX_LEVEL, level + 1)
                ease  = minOf(config.easeMax, ease + 0.05)
            }
            SrsRating.EASY -> {
                level = minOf(MAX_LEVEL, level + 2)
                ease  = minOf(config.easeMax, ease + 0.15)
            }
        }

        var intervalMin = LADDER_MIN[level]
        // EF 微调仅对 level >= 4 生效
        if (level >= 4) {
            intervalMin *= (ease / 2.5).coerceIn(0.70, 1.60)
            intervalMin *= jitterFactor(card.cardId)   // ±5%，由 cardId 决定，可复现
        }

        return card.copy(
            level = level,
            ease = ease,
            state = if (level >= GRADUATION_LEVEL) SrsState.REVIEW else SrsState.LEARNING,
            intervalMin = intervalMin,
            dueAt = now + (intervalMin * 60_000).toLong(),
            reps = card.reps + 1,
            lastReviewAt = now
        )
    }

    /** 返回"下次复习时间"的可读文案，用于滑动后的即时反馈 */
    fun describeNext(intervalMin: Double): String = when {
        intervalMin < 60 -> "${intervalMin.toInt()} 分钟后"
        intervalMin < 1440 -> "${(intervalMin / 60).toInt()} 小时后"
        intervalMin < 43200 -> "${(intervalMin / 1440).roundToInt()} 天后"
        else -> "${(intervalMin / 43200).roundToInt()} 个月后"
    }
}
```

### 4.5 队列组装 SQL（性能关键路径）

```sql
-- ① 学习期卡片：不受上限约束，必须当日完成
SELECT * FROM cards
WHERE isSuspended = 0 AND srsState IN ('LEARNING','RELEARNING')
ORDER BY srsDueAt ASC;

-- ② 到期复习卡：逾期越久越靠前
SELECT * FROM cards
WHERE isSuspended = 0 AND srsState = 'REVIEW' AND srsDueAt <= :now
  AND deckId IN (:selectedDeckIds)
ORDER BY srsDueAt ASC
LIMIT :reviewPerDay;

-- ③ 新卡：按牌组顺序 + sortOrder
SELECT * FROM cards
WHERE isSuspended = 0 AND srsState = 'NEW'
  AND deckId IN (:selectedDeckIds)
ORDER BY deckId, sortOrder
LIMIT :remainingNewToday;
```

三条查询结果在内存中按 `1 张复习 : 4 张新卡` 的比例交错（interleave），避免连续做 20 张新卡造成认知疲劳。

### 4.6 可配置项（设置页）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| 每日新卡上限 | 20 | 0 表示不限制新卡 |
| 每日复习上限 | 100 | 不含学习期卡片 |
| 学习日边界 | 04:00 | 熬夜到凌晨 3 点学的仍算"昨天" |
| 短阶梯自定义 | 5min/30min/12h | 用户可改 |
| 忘记回退步数 lapseStep | 4 | 2–6 可选 |
| 初始 EF | 2.5 | |
| 每日提醒时间 | 20:00 | WorkManager 精确通知，可关闭 |
| 是否开启四档评分 | 关 | 默认只显示二元滑动 |

### 4.7 正确性保障：可测试性设计

因为 `TcmSrsScheduler` 是**零 Android 依赖的纯函数**，可以写完整 JVM 单元测试：

1. **单调性测试**：连续 11 次 `GOOD`，断言 level 严格递增且间隔序列等于阶梯表。
2. **收敛性测试**：模拟 3 年复习（1000 次迭代），断言所有卡片间隔收敛到 180d 且不溢出 `Long`。
3. **遗忘恢复测试**：level=11 时 AGAIN，断言 level 落到 7、state 变 RELEARNING、due ≈ now + 5min。
4. **边界测试**：level=0 时 AGAIN 不产生负数；EF 始终在 [1.3, 2.8] 内。
5. **抖动可复现测试**：同一 cardId 两次调用结果完全一致。

---

## 5. 背诵模块界面原型描述

### 5.1 整体导航结构

```text
┌──────────────────────────────────────────────┐
│              MainActivity (NavHost)          │
└───────────────────┬──────────────────────────┘
                    │
        ┌───────────▼────────────┐
        │  底部导航（4 Tab）       │
        │  背诵 │ 古籍 │ 刷题 │ 我的 │
        └────────────────────────┘
```

四个 Tab：**背诵**（本阶段）/ **古籍**（二阶段）/ **刷题**（三阶段）/ **我的**（设置、统计、数据管理）。

### 5.2 背诵首页（DeckHomeScreen）

```text
┌──────────────────────────────────────┐
│  下午好，厂吴             [🔥 12天]   │   ← 顶部：问候 + 连续打卡
├──────────────────────────────────────┤
│  ╭────────────────────────────────╮  │
│  │      ╭──────────────╮          │  │
│  │      │    ◜◝◜◝◜◝    │          │  │   ← 进度环：今日完成度
│  │      │     68%      │          │  │
│  │      │   34/50 张   │          │  │
│  │      ╰──────────────╯          │  │
│  │                                │  │
│  │   新卡 12/20   复习 22/50      │  │
│  │   预计还需 14 分钟              │  │
│  │                                │  │
│  │      ┌──────────────────┐      │  │
│  │      │   ▶  开始背诵     │      │  │   ← 主行动按钮，主色填充
│  │      └──────────────────┘      │  │
│  ╰────────────────────────────────╯  │
├──────────────────────────────────────┤
│  我的牌组                        [管理]│
│  ┌────────────────────────────────┐  │
│  │ ● 中药学 · 解表药               │  │
│  │   新 8 · 待复习 12 · 共 156     │  │
│  │   ▓▓▓▓▓▓▓░░░░░░░░░  38%        │  │
│  ├────────────────────────────────┤  │
│  │ ● 方剂学 · 解表剂               │  │
│  │   新 4 · 待复习 6 · 共 92       │  │
│  │   ▓▓▓▓▓▓▓▓▓▓░░░░░░░░  54%      │  │
│  ├────────────────────────────────┤  │
│  │ ● 针灸学 · 特定穴               │  │
│  │   新 0 · 待复习 4 · 共 210      │  │
│  │   ▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░  72%      │  │
│  └────────────────────────────────┘  │
├──────────────────────────────────────┤
│   背诵     古籍     刷题     我的      │
└──────────────────────────────────────┘
```

**关键交互：**
- 进度环用 `Animatable` 做数字滚动动画（0 → 68%），给完成任务的正反馈。
- 长按牌组卡片 → 弹出「快速开始 / 只学新卡 / 只复习」快捷菜单。
- 右上角热力图入口（后续统计页）。

### 5.3 背卡页（CardReviewScreen）★ 核心界面

**正面（未翻面）：**

```text
┌──────────────────────────────────────┐
│  ✕                    ▓▓▓▓▓░░░░░░  35/54│  ← 退出 + 细进度条
├──────────────────────────────────────┤
│                                      │
│   ╭──────────────────────────────╮   │
│   │  [中药学 · 解表药]            │   │  ← 来源角标（pill）
│   │                              │   │
│   │                              │   │
│   │     麻 黄 的 功 效 是 ？       │   │  ← 题干，28sp，行高 1.6
│   │                              │   │
│   │                              │   │
│   │                              │   │
│   │              《中药学》人卫版  │   │  ← sourceRef 出处（小字灰色）
│   ╰──────────────────────────────╯   │
│                                      │
│         ── 点击卡片显示答案 ──         │
├──────────────────────────────────────┤
│   ← 未记住        已记住 →            │  ← 底部操作提示（半透明）
└──────────────────────────────────────┘
```

**翻面后：** 卡片 3D Y 轴翻转（`rotationY` 0→180，`cameraDistance` 12f），背面背景色略深，显示效果/主治/用法/备注，`sourceRef` 支持点击展开引用详情。

**滑动手势状态：**

```text
未记住 ←  [卡片跟随手指左移 30%，逆时针旋转 8°]     已记住 →
         ┌────────────────────────────┐
         │  ▲ 背景红色（#D9534F 20% 透明）│
         │  卡片内容仍清晰可读            │
         └────────────────────────────┘
```

- 位移超过屏幕宽度 22% 时松手 → 卡片飞出（300ms，`FastOutLinearInEasing`）
- 位移不足 → 弹回原位（200ms，`spring`）
- 飞出后底部浮出 **Snackbar 式提示**：「已记住 · 下次复习：12 小时后」，停留 1.2s
- **撤销**：飞行后进度条左侧出现「↺ 撤销」胶囊，可回滚最近一次决策（读写 `review_logs` 最后一条并反写 `cards`）

**颜色规范（护眼前提下保证语义清晰）：**

| 语义 | 日间 | 夜间 |
|---|---|---|
| 已记住 / 正确 | 竹青 `#4A7C59` | `#6FA97E` |
| 未记住 / 错误 | 赭红 `#C1584A` | `#D97B6B` |
| 页面背景 | 米白纸感 `#FAF8F3` | 墨灰 `#1C1B19` |
| 卡片背景 | 纯白 `#FFFFFF` | `#262522` |
| 主文字 | `#2B2A28` | `#E8E4DA` |

> 全部避免纯白 `#FFFFFF` 大底与纯黑 `#000000` 文字，降低长时间阅读的眩光与对比疲劳。

### 5.4 会话完成页（SessionSummaryScreen）

```text
┌──────────────────────────────────────┐
│                                      │
│              ✓                       │
│         今日任务完成！                 │
│                                      │
│   ┌────────┬────────┬────────┐      │
│   │  34    │  28    │   6    │      │
│   │ 已复习  │ 已记住  │ 未记住  │      │
│   └────────┴────────┴────────┘      │
│   ┌────────┬────────┬────────┐      │
│   │ 12m38s │  6.8s  │  92%   │      │
│   │ 总用时  │ 平均单卡 │ 正确率  │      │
│   └────────┴────────┴────────┘      │
│                                      │
│   下次复习：明天 04:00 后共 26 张      │
│                                      │
│      ┌────────────┐  ┌───────────┐   │
│      │  再来一组   │  │ 返回首页   │   │
│      └────────────┘  └───────────┘   │
└──────────────────────────────────────┘
```

### 5.5 卡片管理页（CardManageScreen）

- 顶部搜索框（全文检索 front/back/tags）
- 筛选 Chips：`全部` / `新卡` / `学习中` / `复习中` / `已暂停` / `难卡(lapses≥3)`
- 列表项：正面（单行省略）、分类、下次复习时间、难度指示条
- 批量操作：暂停 / 重置进度 / 移动到牌组 / 删除
- 右下 FAB：手动新建卡片
- 顶栏菜单：导入 JSON 卡片包 / 导出卡片包 / 导出进度

---

## 6. 知识库数据来源与校验策略

### 6.1 权威数据源清单

| 模块 | 权威来源 | 适用范围 | 状态 |
|---|---|---|---|
| 中药 | 《中国药典》2025 年版一部（2025-10-01 施行，收载中药 3069 种） | 药物性味、功效、用法用量、毒性 | ✅ 官方公开 |
| 中药 / 方剂 | 中国中医药出版社「十四五」规划教材《中药学》《方剂学》 | 考点口径（考试就是按教材命题） | ⚠️ 有版权，见 6.3 |
| 针灸穴位 | **GB/T 12346-2021《经穴名称与定位》**（2021-11-26 实施，替代 GB/T 12346-2006，规定 362 个经穴） | 穴位名称、归经、体表定位 | ✅ 国家标准 |
| 经外奇穴 | GB/T 40997《经外奇穴名称与定位》 | 经外奇穴 | ✅ 国家标准 |
| 经络循行 | GB/T 12346-2021 + 教材《针灸学》 | 十二正经、奇经八脉循行路线 | ✅ / ⚠️ |
| 经典条文 | 《黄帝内经》《伤寒论》《金匮要略》《温病条辨》《神农本草经》等 | 条文原文与出处 | ✅ 公有领域 |
| 方剂组成 | 《中国药典》一部成方制剂 + 教材《方剂学》 | 组成、功用、主治、配伍 | ⚠️ 部分有版权 |

> ⚠️ **注意**：调研中发现不同来源对 307 科目分值占比的表述存在差异（中基/中诊/中药/方剂 在 13%–17% 之间，中内在 20%–28% 之间）。**数据建模时不硬编码占比，只用 `subject` 枚举做分类**，具体权重以当年官方大纲为准。

### 6.2 数据入库校验流水线（三道关卡）

```text
原始资料（PDF / 标准文本 / 古籍）
        │
        ▼  关卡 1：结构化提取
   半自动转换脚本（Python）
   · 提取为统一 JSON Schema
   · 每条必须含 sourceRef
        │
        ▼  关卡 2：机器校验
   · JSON Schema 校验（必填字段、枚举值、长度）
   · 唯一性校验（同牌组内 front 去重）
   · 交叉一致性校验（穴位归经必须与 GB/T 12346-2021 一致；
     中药性味必须与药典一致；方剂组成药味必须能在中药表命中）
   · 数值范围校验（如穴位定位中的"寸"值、药典用量上限）
        │
        ▼  关卡 3：人工抽检
   · 每批随机抽 10% 人工复核
   · 高危项（药典毒性药、用量、配伍禁忌「十八反十九畏」）100% 复核
        │
        ▼
   构建期产物：knowledge_base.db（预编译 SQLite）
   同时输出校验报告：总数 / 通过 / 驳回 / 驳回原因分布
```

### 6.3 版权红线（必须明确）

这是本项目最容易踩的法律风险，明确以下三条策略：

1. **可以内置**：公有领域古籍原文（内经、伤寒论等，作者逝世远超 50 年）；国家标准中的**事实性数据**（穴位名称、归经、定位描述此类事实不受版权保护）。
2. **谨慎处理**：现行规划教材的**表达性内容**（成段的功效描述、解析文字）。策略是 **自行组织语言重写为考点式条目，而非复制教材原文段落**，并在 `sourceRef` 中标注依据版本。
3. **不内置、改为用户导入**：任何需要完整原文照搬的当代教辅、真题题库、机构讲义。App 提供**导入器**，用户自行导入自己合法持有的资料，责任边界清晰。

> 设置页需保留一段"数据来源与免责声明"，说明内置数据均来自公开权威渠道或公有领域，不承担用户自行导入内容的版权责任。

### 6.4 卡片 JSON 交换格式（第一阶段即冻结此契约）

```json
{
  "schemaVersion": 1,
  "packName": "中药学·解表药",
  "packVersion": "1.0.0",
  "subject": "MATERIA_MEDICA",
  "sourceRef": "《中国药典》2025年版一部",
  "cards": [
    {
      "deck": "中药学/解表药/辛温解表药",
      "type": "BASIC",
      "front": "麻黄的功效是什么？",
      "back": "发汗解表，宣肺平喘，利水消肿。",
      "extra": "与桂枝鉴别：麻黄发汗力强，桂枝发汗力缓而能温通经脉。",
      "tags": ["高频", "易混淆"],
      "sourceRef": "《中国药典》2025年版一部·麻黄"
    },
    {
      "deck": "针灸学/特定穴/五输穴",
      "type": "ACUPOINT",
      "front": "合谷穴（LI4）的定位是？",
      "back": "在手背，第 2 掌骨桡侧的中点处。",
      "extra": "孕妇慎用。",
      "tags": ["高频"],
      "sourceRef": "GB/T 12346-2021 §5.2"
    }
  ]
}
```

**导入器行为：** 自动创建不存在的牌组（按 `deck` 路径 `A/B/C` 逐级建 parent）；`front` 重复时跳过并计入报告；导入结果以对话框展示（新增 X、跳过 Y、失败 Z + 失败详情可导出）。

---

## 7. 本阶段交付物与实施计划

### 7.1 代码交付清单

| # | 交付项 | 说明 |
|---|---|---|
| 1 | 可运行工程骨架 | Gradle 配置、Hilt、Compose 主题（日/夜/护眼三套色板）、Navigation、底部导航 |
| 2 | `:core:database` 完整实现 | 12 张表 Entity + DAO + 索引 + 迁移框架 + 预置库加载 |
| 3 | `:core:srs` 算法模块 | `TcmSrsScheduler` + `SrsConfig` + **全套 JVM 单元测试（≥ 20 个用例）** |
| 4 | 背诵模块 UI | 首页 / 背卡页（滑动手势）/ 完成页 / 卡片管理页 |
| 5 | 卡片导入导出 | JSON 卡片包导入（含报告）+ 导出 |
| 6 | 设置页 | 每日上限、日界、阶梯自定义、四档开关、提醒时间、**开发者备注：厂吴**、数据来源声明 |
| 7 | 种子数据 | 演示用小程序库（约 100 张，覆盖中药/方剂/穴位/条文各 25 张），用于验证流程 |
| 8 | APK 产物 | 可安装的 debug + release（自签名 keystore）APK |
| 9 | 构建说明 | 如何在本地/沙箱构建（Android SDK 35 + JDK 17 + Gradle 8.x） |

### 7.2 实施顺序（编码阶段）

```text
Step 1  工程骨架 + 主题体系 + 导航        （可运行的空壳，先看到界面）
Step 2  Room 数据库全量建表 + DAO + 预置库  （数据地基）
Step 3  :core:srs 算法 + 单元测试          （先验证算法正确性，再上 UI）
Step 4  种子数据 + 导入器                  （有内容才能交互）
Step 5  背卡页（手势为核心）★ 最高风险项    （先打通一张卡片的完整闭环）
Step 6  首页 + 完成页 + 统计
Step 7  卡片管理页 + 设置页
Step 8  打磨（动画、空状态、边界情况）+ 构建 APK
```

> Step 5 是整个第一阶段的风险中心。策略是：**先做一个"只有一张卡片、能滑、能写入数据库、能算出下次间隔"的最小闭环，跑通后再做堆叠卡片与动画**。

### 7.3 风险评估

| 风险 | 影响 | 应对 |
|---|---|---|
| 滑动手势与 Compose 滚动/翻面动画冲突 | 高 | 最小闭环先行；`pointerInput` 中先判断主轴方向再决定是否消费事件 |
| 中文字体渲染（古籍需楷体/宋体） | 中 | 内置开源字体 **霞鹜文楷 LXGW WenKai**（OFL 协议，可免费商用嵌入），古籍模块统一用它 |
| 预置数据库体积膨胀 | 中 | 卡片文本做适度压缩；评估 `SQLite` 页大小与 `VACUUM`；WAL 模式 |
| 知识库数据准确性 | **高** | 6.2 的三道校验关卡；高危项 100% 人工复核；每条可溯源 |
| 版权风险 | **高** | 严格遵守 6.3 三条策略，教材表达性内容重写不入原文 |
| 沙箱内 Android 构建环境 | 中 | 需下载 cmdline-tools + platform 35 + build-tools；已确认网络可达，构建耗时可预期（首次约 10–20 分钟） |

---

## 8. 待确认决策清单（请逐条批复）

| # | 决策点 | 我的建议 | 备选 |
|---|---|---|---|
| D1 | **遗忘曲线算法** | 固定阶梯 + EF 微调（TCM-SRS），节点完全对齐 5min/30min/12h/1d/2d/4d/7d/15d | 标准 SM-2（间隔不可预测）；纯 Leitner（无个性化） |
| D2 | **评分档位** | 默认二元滑动（未记住/已记住），长按展开四档精评 | 始终四档（提升算法精度但增加操作成本） |
| D3 | **遗忘回退步数** | `AGAIN` 时 level 回退 4 档，并立即进入 5 分钟重学 | 回退 2 档（温和）；直接回 level 1（Anki 式重学） |
| D4 | **工程结构** | 单 `:app` 模块 + 强包边界（依赖方向用 Detekt 强制） | 多 Gradle 模块（更规范但开发效率低） |
| D5 | **知识库分发** | 预编译 SQLite 进 APK + JSON 增量更新通道 | 纯 JSON 首启解析（首启慢但易更新） |
| D6 | **内置数据范围** | 只内置公有领域古籍 + 国家标准事实数据 + 自组织考点条目；教材原文与题库走用户导入 | 内置教材内容（有版权风险，不建议） |
| D7 | **题库格式** | JSON 为主（冻结 schema，见 6.4）；CSV 作为辅助 | 增加 Excel(xlsx) 直读（需引入 POI 系库，APK 增大约 3MB） |
| D8 | **古籍字体** | 内置霞鹜文楷 LXGW WenKai（OFL，可商用） | 仅用系统字体（体积小但古籍观感差） |
| D9 | **最低版本** | minSdk 26（Android 8.0，覆盖 98%+ 设备） | minSdk 24（多覆盖 1%，但需兼容处理增多） |

---

**确认以上方案后，我将进入编码阶段，按 7.2 的实施顺序推进，并在完成 Step 1–2 时先给你一个可编译运行的中间版本。**

---

*文档结束 · 中医工厂 v1.0 第一阶段方案 · 开发者：厂吴*
