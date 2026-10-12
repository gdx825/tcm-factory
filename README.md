# 中医工厂 · TCM Factory

> 面向中医学专业学生的中医考研（307 中医综合）与执业医师资格考试备考工具。
> **完全离线可用** · 内置 497 条权威来源知识 · 无账号、无广告、不收集任何数据。

[![Platform](https://img.shields.io/badge/Android-8.0%2B-brightgreen)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue)]()
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue)]()
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

---

## 为什么做这个

中医考研要背的东西又多又像——三千多个知识点，功效、组成、定位彼此高度相似。
市面上背单词的 App 很成熟，但**针对中医知识体系、并且愿意把数据完全放在用户自己手机上的工具几乎没有**。

所以这个项目有三条原则：

1. **离线优先**：核心功能不联网也能完整使用，知识库更新是可选增强项
2. **不收集数据**：没有账号、没有云同步、没有统计上报，学习记录只存在你自己的手机里
3. **来源可溯**：每条知识都标注出处，欢迎任何人校对纠错

---

## 下载安装

前往本仓库的 [Releases](../../releases) 页面下载最新版 APK，在手机上安装即可。

> 首次安装需要允许「安装未知来源应用」。
> 应用完全开源，你可以自行编译核对，也可以直接使用提供好的安装包。

**国内网络加速下载**（jsDelivr 加速，指向仓库内的同一份 APK）：

```
https://cdn.jsdelivr.net/gh/gdx825/tcm-factory@main/apk/tcm-factory-v1.5.1.apk
```

---

## 功能

### 📇 每日背诵

- **卡片式复习**：左右滑动分别代表「已记住」和「未记住」，点击翻面看答案，1 秒内完成一次决策
- **内置遗忘曲线**：按 5min → 30min → 12h → 1d → 2d → 4d → 7d → 15d → 30d → 60d → 120d → 180d 的阶梯自动排期
- **每日任务可调**：新学数量与复习上限自行设定，日界时间也可改（默认凌晨 4 点，适合熬夜复习）
- **卡片管理**：搜索、按科目/状态筛选、暂停、重置进度、批量导出

### 📜 古籍阅读

- **TXT 导入**：自动识别编码（UTF-8 / GB18030 / Big5 / UTF-16）、智能重排被硬换行切碎的段落、自动切分卷篇章条
- **排版可调**：字号、行距、段距、页边距、四套配色（米白 / 墨灰 / 豆沙绿 / 羊皮纸）、内置**霞鹜文楷**古籍字体
- **读经制卡闭环**：读到好句子长按选中 → 一键变成背诵卡 → 背诵时点「查看原文」跳回古籍原处高亮

### ✍️ 刷题与错题本

- **自定义题库导入**：JSON 格式（含题干、选项、答案、解析、章节、难度），支持单选 / 多选 / 判断
- **多种练习模式**：顺序、随机、按章节、错题重做
- **错题本**：答错自动归集，连续答对 2 次自动移出，也可手动移除

### 📊 学习统计

- 学习热力图（近 147 天）、连续学习天数、近 30 天学习量趋势、累计概览

### 🔄 知识库更新（可选）

- 内置内容随 APK 发布，开箱即用
- 配置数据源后可**联网增量更新**，也可以从本地文件导入知识包
- 更新只替换内容，**你的背诵进度完整保留**

---

## 内置知识库

共 **497 条**，全部标注来源：

| 知识包 | 条目 | 内容 |
|---|---:|---|
| 中药学 | 173 | 20 类功效：解表、清热、泻下、祛风湿、化湿、利水渗湿、温里、理气、消食、止血、活血化瘀、化痰止咳平喘、安神、平肝息风、开窍、补虚、收涩 |
| 方剂学 | 58+24 | 13 类方剂：泻下、和解、清热、温里、补益、固涩、安神、理气、理血、治风、祛湿、祛痰、消食 |
| 针灸学 | 58+30 | 高频腧穴定位归经 + 十二正经与奇经八脉循行 |
| 经典条文 | 34+25 | 《伤寒论》《金匮要略》《黄帝内经》《温病条辨》 |
| 中基中诊 | 39 | 阴阳五行、藏象、气血津液、病因病机、治则、四诊、八纲、脏腑辨证 |
| 练习题 | 36+24 | 六科综合练习，含单选 / 多选 / 判断 |

### 内容来源

| 内容 | 来源 |
|---|---|
| 中药功效 | 《中国药典》2025 年版一部 |
| 腧穴定位 | GB/T 12346-2021《经穴名称与定位》 |
| 经典条文 | 公有领域古籍原文 |
| 方剂、中基、中诊 | 依规划教材通行内容整理为考点式条目 |
| 练习题 | 编者自编 |

详细说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

### ⚠️ 关于准确性

知识条目依据上述权威来源整理，但**文字是编者整理的通行表述，并非标准原文的逐字引用**；
《伤寒论》条号依宋本通行编号，不同版本或有出入。

**如有疑义，请以最新版教材与国家标准原文为准。**

发现错误请提 [内容纠错](../../issues/new?template=content_error.md)——这是对本项目最大的帮助。

---

## ⚠️ 免责声明

本软件为**学习辅助工具**，内置医学知识仅供备考复习参考。

**不构成任何医疗建议、诊断或治疗方案，严禁用于临床决策。**

使用本软件产生的任何后果由使用者自行承担。

---

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + 单向数据流 + 分层（feature → domain → data → core） |
| 依赖注入 | Hilt |
| 本地存储 | Room（SQLite，WAL）+ DataStore |
| 网络 | OkHttp（仅用于知识库更新） |
| 构建 | AGP 8.7.3 + Gradle 8.11.1 + JDK 17 |
| SDK | minSdk 26 / targetSdk 35 |

**一条硬性约定**：算法、文本处理、数据解析全部写成**零 Android 依赖的纯 Kotlin**，
放在 `core/` 下，保证能在 JVM 上完整单元测试。这条约定已经抓出 9 个
「界面上完全看不出来」的缺陷。

---

## 遗忘曲线算法

**没有采用标准 SM-2。** 本产品要求的复习节点是一条确定性阶梯
（5min / 30min / 12h / 1d / 2d / 4d / 7d / 15d …），而 SM-2 递推会得到
2.5d / 6.2d / 15.6d 这类不可预期的间隔。

因此采用 **固定 12 级阶梯 + 难度因子（EF）微调**：

| Level | 间隔 | 说明 |
|---|---|---|
| 0–2 | 5min / 30min / 12h | **学习期**，不受每日复习上限约束（集中巩固优于分散） |
| 3 | 1d | **毕业**，进入长期记忆复习期 |
| 4–7 | 2d / 4d / 7d / 15d | 复习期，开始叠加 EF 微调 |
| 8–11 | 30d / 60d / 120d / 180d | 长期保持 |

- 未记住 → 等级回退 4 档 + 5 分钟后重学 + EF −0.20
- 已记住 → 等级 +1
- 天级间隔追加 ±5% 抖动（种子 = cardId），避免同批卡片永远同日到期
- 实现见 `core/srs/TcmSrs.kt`，配套 19 个单元测试

---

## 知识库扩展

内置内容与联网更新使用**同一套格式**，因此：

- `app/src/main/assets/knowledge/` 既是 APK 内置内容，也是发布源的唯一数据源
- 自己整理的知识包可以打包后上传到任意静态托管，供他人订阅

```bash
# 打包（生成 manifest.json + 分包 + sha256 校验值）
python3 tools/build_knowledge_packs.py --src app/src/main/assets/knowledge --out ./dist
```

格式说明与发布流程见脚本文件开头的注释。

**更新机制**：分包增量下载 → SHA-256 完整性校验 → 事务写入；
更新只替换内容，**背诵进度原样保留**。详见 [docs/04-knowledge-updates.md](docs/04-knowledge-updates.md)。

---

## 自己构建

### 环境要求

- JDK 17
- Android SDK：platform 35 + build-tools 35.0.0

### 命令行构建

```bash
git clone https://github.com/gdx825/tcm-factory.git
cd tcm-factory

# 若 sdk.dir 未配置，创建 local.properties：
#   sdk.dir=/path/to/android-sdk

# 跑单元测试
./gradlew :app:testDebugUnitTest

# 构建
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

**不需要配置签名**就能构建：缺少 `keystore.properties` 时会自动回退到 debug 签名。

### 发布正式版（签名）

复制 `keystore.properties.example` 为 `keystore.properties`，填入你的密钥信息：

```bash
keytool -genkeypair -keystore app/my-release.jks -alias mykey \
  -keyalg RSA -keysize 2048 -validity 10000
```

```properties
storeFile=app/my-release.jks
storePassword=你的口令
keyAlias=mykey
keyPassword=你的口令
```

> ⚠️ **`keystore.properties` 与密钥库文件都已在 `.gitignore` 中。**
> 一旦泄露，他人就能签出与你完全相同的签名，伪造「官方更新」覆盖安装你的用户。
> 请务必只在本地保管。

---

## 目录结构

```
app/src/main/java/com/tcmfactory/
├── core/                  # 基础设施，纯逻辑优先
│   ├── srs/TcmSrs.kt            # 遗忘曲线算法（纯 Kotlin）
│   ├── reader/                  # 文本编码探测 / 智能重排 / 章节切分
│   ├── quiz/                    # 题库格式 / 答案规范化
│   ├── knowledge/               # 知识包格式 / 版本比较 / 更新决策
│   ├── database/                # Room 实体与 DAO
│   ├── notify/                  # 通知
│   └── designsystem/            # 主题与通用组件
├── data/                  # 仓库层 / 网络 / 定时任务 / 种子数据
├── domain/                # 领域模型与用例
└── feature/               # 界面：recite / reader / quiz / stats / knowledge / settings

app/src/main/assets/knowledge/  # 内置知识包（6 个 / 398 条）
tools/build_knowledge_packs.py  # 知识库打包发布工具
docs/                           # 各阶段设计文档（[索引](docs/README.md)）
```

---

## 测试

```bash
./gradlew :app:testDebugUnitTest
```

**118 个单元测试**，覆盖：

| 模块 | 用例数 |
|---|---:|
| 知识库（版本比较 / 更新决策 / 包校验） | 40 |
| 遗忘曲线算法 | 19 |
| 答案解析（多格式容错 + 往返一致性） | 18 |
| 文本管线（编码探测 / 重排 / 切分） | 17 |
| 题库解析 | 16 |
| 网络下载（MockWebServer，含体积上限与篡改检测） | 8 |

---

## 待办

- [ ] 继续扩充知识库（优先补中医内科学、方剂歌诀、诊断学问答）
- [ ] 错题本接上遗忘曲线，做真正的错题复习计划
- [ ] 开启 R8 混淆瘦身（规则已备好，见 `app/proguard-rules.pro`）
- [ ] 知识包签名（Ed25519），为第三方内容生态做准备

---

## 反馈与贡献

- 🐛 [报告问题](../../issues/new?template=bug_report.md)
- 📖 [**内容纠错**](../../issues/new?template=content_error.md) —— 最有价值的贡献
- 💡 [功能建议](../../issues/new?template=feature_request.md)
- 📦 分享自己整理的知识包 —— 见 [CONTRIBUTING.md](CONTRIBUTING.md)

参与方式详见 [CONTRIBUTING.md](CONTRIBUTING.md)。

---

## 开源协议

[MIT License](LICENSE) © 2026 厂吴

第三方组件与数据来源声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

---

## 开发者

**厂吴** —— 一名中医学生，为了自己考研顺手做的工具，希望能帮到同样在备考的人。

如果这个工具对你有帮助，欢迎 Star ⭐ 或把它分享给同学。
使用时发现任何问题，都请提 Issue 告诉我。
