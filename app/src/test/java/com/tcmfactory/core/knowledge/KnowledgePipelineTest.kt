package com.tcmfactory.core.knowledge

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 知识库更新管线测试。
 *
 * 重点覆盖「界面上看不出来、但会导致错误内容写进用户学习资料」的分支：
 * 版本比较、完整性校验、更新决策、包内容校验。
 */
class KnowledgePipelineTest {

    // ==================================================================
    //  版本比较
    // ==================================================================

    @Test
    fun `版本比较遵守数值语义而非字典序`() {
        // 用字符串比较时 "1.10.0" < "1.9.0"，这是最经典的陷阱
        assertTrue(VersionComparator.compare("1.10.0", "1.9.0") > 0)
        assertTrue(VersionComparator.compare("2.0.0", "1.99.99") > 0)
        assertTrue(VersionComparator.compare("1.0.0", "1.0.0") == 0)
    }

    @Test
    fun `缺失的版本段按零处理`() {
        assertEquals(0, VersionComparator.compare("1.2", "1.2.0"))
        // 段数更多但数值相同时，段数多者更大
        assertTrue(VersionComparator.compare("1.2.1", "1.2") > 0)
    }

    @Test
    fun `预发布版本小于正式版本`() {
        assertTrue(VersionComparator.compare("2.0.0", "2.0.0-beta.1") > 0)
        assertTrue(VersionComparator.compare("1.0.0-beta", "1.0.0-alpha") > 0)
    }

    @Test
    fun `异常版本号不抛异常且行为可预期`() {
        // 知识库版本号来自外部，绝不能因为写法奇怪就让更新流程崩溃
        assertEquals(0, VersionComparator.compare("", ""))
        assertTrue(VersionComparator.compare("1.0.0", "") > 0)
        assertTrue(VersionComparator.compare("", "1.0.0") < 0)
        assertTrue(VersionComparator.compare("1.0.0", null) > 0)
        // 非数字段按 0 处理，不崩
        assertEquals(0, VersionComparator.compare("1.x", "1.0"))
        // v 前缀、构建元数据都能容忍
        assertEquals(0, VersionComparator.compare("v1.2.0", "1.2.0"))
        assertEquals(0, VersionComparator.compare("1.2.0+build7", "1.2.0"))
    }

    @Test
    fun `未安装时任何版本都应视为更新`() {
        assertTrue(VersionComparator.isNewer("1.0.0", null))
        assertTrue(VersionComparator.isNewer("1.0.0", ""))
        assertFalse(VersionComparator.isNewer(null, "1.0.0"))
    }

    @Test
    fun `低版本不覆盖高版本避免内容回退`() {
        // 数据源回滚时不能把用户已更新的内容降级
        assertFalse(VersionComparator.shouldReplace("1.0.0", "2.0.0"))
        assertTrue(VersionComparator.shouldReplace("2.0.1", "2.0.0"))
    }

    // ==================================================================
    //  SHA-256
    // ==================================================================

    @Test
    fun `sha256 计算结果符合已知向量`() {
        // "abc" 的 SHA-256 是公开测试向量
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.of("abc".toByteArray())
        )
    }

    @Test
    fun `sha256 校验能识别内容被篡改`() {
        val bytes = "桂枝汤".toByteArray()
        val good = Sha256.of(bytes)
        assertTrue(Sha256.matches(good, bytes))
        assertTrue(Sha256.matches(good.uppercase(), bytes))
        assertFalse(Sha256.matches(good, "桂枝加葛根汤".toByteArray()))
    }

    @Test
    fun `sha256 校验能识别格式非法的摘要`() {
        val bytes = "abc".toByteArray()
        assertFalse(Sha256.matches("", bytes))
        assertFalse(Sha256.matches("abc", bytes))
        // 长度对但含非十六进制字符
        assertFalse(Sha256.matches("z".repeat(64), bytes))
        assertFalse(Sha256.isWellFormed(null))
        assertFalse(Sha256.isWellFormed("xyz"))
        assertTrue(Sha256.isWellFormed("a".repeat(64)))
    }

    // ==================================================================
    //  稳定 ID
    // ==================================================================

    @Test
    fun `externalId 由内容派生因此跨版本稳定`() {
        val a = ExternalId.forBuiltinCard("中药学", "桂枝的功效")
        val b = ExternalId.forBuiltinCard("中药学", "桂枝的功效")
        assertEquals(a, b)
        assertEquals(16, a.length)
    }

    @Test
    fun `externalId 对不同内容不冲突`() {
        val a = ExternalId.forBuiltinCard("中药学", "桂枝的功效")
        val b = ExternalId.forBuiltinCard("中药学", "麻黄的功效")
        assertFalse(a == b)
    }

    @Test
    fun `externalId 拼接不会因字段边界产生歧义`() {
        // 若用普通拼接，("ab","c") 与 ("a","bc") 会得到同一个串
        val a = ExternalId.of("ab", "c")
        val b = ExternalId.of("a", "bc")
        assertFalse(a == b)
    }

    // ==================================================================
    //  数据源地址
    // ==================================================================

    @Test
    fun `地址规范化去掉尾部斜杠`() {
        assertEquals("https://example.com/kb", SourceUrl.normalize("https://example.com/kb/"))
        assertEquals("https://example.com/kb", SourceUrl.normalize("  https://example.com/kb//  "))
    }

    @Test
    fun `非法地址被拒绝`() {
        assertNull(SourceUrl.normalize(""))
        assertNull(SourceUrl.normalize("   "))
        assertNull(SourceUrl.normalize("example.com/kb"))
        assertNull(SourceUrl.normalize("ftp://example.com/kb"))
        assertNull(SourceUrl.normalize("https://exam ple.com"))
        assertNull(SourceUrl.normalize("https://"))
    }

    @Test
    fun `相对路径能正确拼接为绝对地址`() {
        assertEquals(
            "https://example.com/kb/packs/a.json",
            SourceUrl.resolve("https://example.com/kb", "packs/a.json")
        )
        // 前导斜杠不应产生双斜杠
        assertEquals(
            "https://example.com/kb/packs/a.json",
            SourceUrl.resolve("https://example.com/kb", "/packs/a.json")
        )
        // 绝对地址原样返回
        assertEquals(
            "https://cdn.other.com/a.json",
            SourceUrl.resolve("https://example.com/kb", "https://cdn.other.com/a.json")
        )
        assertNull(SourceUrl.resolve("https://example.com/kb", "   "))
    }

    @Test
    fun `明文传输能被识别`() {
        assertTrue(SourceUrl.isCleartext("http://192.168.1.5/kb"))
        assertFalse(SourceUrl.isCleartext("https://example.com/kb"))
        assertEquals("192.168.1.5", SourceUrl.hostOf("http://192.168.1.5/kb/manifest.json"))
    }

    @Test
    fun `时间戳参数用问号追加`() {
        assertEquals(
            "https://cdn.jsdelivr.net/gh/u/r@main/kb/manifest.json?_=1700000000000",
            SourceUrl.withCacheBuster(
                "https://cdn.jsdelivr.net/gh/u/r@main/kb/manifest.json",
                stamp = 1700000000000L
            )
        )
    }

    @Test
    fun `地址已带参数时改用与号追加`() {
        // 拼成两个问号的地址虽然多数服务器也认，但属于明显写错的痕迹，
        // 而这类错误只会出现在"用户自定义源带参数"的场景里，很难被发现。
        assertEquals(
            "https://example.com/kb/manifest.json?v=2&_=1700000000000",
            SourceUrl.withCacheBuster(
                "https://example.com/kb/manifest.json?v=2",
                stamp = 1700000000000L
            )
        )
    }

    @Test
    fun `加时间戳后地址不被判为非法`() {
        // normalize 只拒绝"缺主机名 / 含空格 / scheme 不对"，参数不在其列。
        // 这里是在守一个更基本的事实：绕缓存这个动作不能把地址本身弄坏。
        val busted = SourceUrl.withCacheBuster("https://example.com/kb/manifest.json", 1L)
        assertNotNull(SourceUrl.normalize(busted))
        assertTrue(busted.startsWith("https://example.com/kb/manifest.json"))
    }

    @Test
    fun `不同时刻产生不同地址`() {
        val url = "https://example.com/kb/manifest.json"
        assertNotEquals(SourceUrl.withCacheBuster(url, 1L), SourceUrl.withCacheBuster(url, 2L))
    }

    // ==================================================================
    //  更新决策
    // ==================================================================

    private fun entry(
        packId: String = "tcm.materia.core",
        version: String = "1.1.0",
        kind: String = PackKind.CARDS,
        sha: String = "a".repeat(64),
        url: String = "packs/materia.json",
        itemCount: Int = 100,
        bytes: Long = 20_000
    ) = ManifestEntry(
        packId = packId,
        packName = "中药学核心",
        kind = kind,
        version = version,
        itemCount = itemCount,
        bytes = bytes,
        sha256 = sha,
        url = url
    )

    @Test
    fun `本地没有的包应判定为安装`() {
        val plan = UpdatePlanner.plan(
            manifest = KnowledgeManifest(packs = listOf(entry())),
            installed = emptyList(),
            currentAppVersion = "1.3.0"
        )
        assertEquals(1, plan.installs.size)
        assertTrue(plan.hasWork)
        assertEquals(100, plan.pendingItemCount)
    }

    @Test
    fun `远端版本更高应判定为更新`() {
        val plan = UpdatePlanner.plan(
            manifest = KnowledgeManifest(packs = listOf(entry(version = "1.2.0"))),
            installed = listOf(InstalledPackInfo("tcm.materia.core", "中药学核心", "1.1.0", PackKind.CARDS, PackSource.NETWORK)),
            currentAppVersion = "1.3.0"
        )
        val update = plan.updates.single()
        assertEquals("1.1.0", update.fromVersion)
        assertEquals("1.2.0", update.entry.version)
    }

    @Test
    fun `版本相同或更低都不产生下载动作`() {
        val installed = listOf(
            InstalledPackInfo("tcm.materia.core", "中药学核心", "1.2.0", PackKind.CARDS, PackSource.NETWORK)
        )
        val same = UpdatePlanner.plan(
            KnowledgeManifest(packs = listOf(entry(version = "1.2.0"))), installed, "1.3.0"
        )
        assertFalse(same.hasWork)
        assertEquals(1, same.upToDate.size)

        val lower = UpdatePlanner.plan(
            KnowledgeManifest(packs = listOf(entry(version = "1.0.0"))), installed, "1.3.0"
        )
        assertFalse(lower.hasWork, "远端版本更低时不应触发下载")
    }

    @Test
    fun `更新决策会带上已装包的来源用于提示覆盖风险`() {
        val plan = UpdatePlanner.plan(
            manifest = KnowledgeManifest(packs = listOf(entry(version = "2.0.0"))),
            installed = listOf(
                InstalledPackInfo("tcm.materia.core", "我的整理", "1.0.0", PackKind.CARDS, PackSource.FILE)
            ),
            currentAppVersion = "1.3.0"
        )
        assertEquals(PackSource.FILE, plan.updates.single().fromSource)
    }

    @Test
    fun `索引项格式非法时应被拒绝而不是尝试下载`() {
        val plan = UpdatePlanner.plan(
            manifest = KnowledgeManifest(
                packs = listOf(
                    entry(sha = "not-a-hash"),
                    entry(packId = "BadID"),
                    entry(url = "")
                )
            ),
            installed = emptyList(),
            currentAppVersion = "1.3.0"
        )
        assertEquals(3, plan.rejected.size)
        assertFalse(plan.hasWork)
        assertEquals(3, plan.rejected.count { it.reason.isNotBlank() })
    }

    @Test
    fun `应用版本不满足门槛时整体阻塞且不产生任何下载`() {
        val plan = UpdatePlanner.plan(
            manifest = KnowledgeManifest(
                minAppVersion = "2.0.0",
                packs = listOf(entry())
            ),
            installed = emptyList(),
            currentAppVersion = "1.3.0"
        )
        assertTrue(plan.blockedByAppVersion)
        assertFalse(plan.hasWork)
        assertTrue(plan.items.isEmpty())
        assertNotNull(plan.blockReason)
    }

    @Test
    fun `索引中重复 packId 时保留高版本`() {
        // 高版本在前
        val a = UpdatePlanner.plan(
            KnowledgeManifest(packs = listOf(entry(version = "2.0.0"), entry(version = "1.0.0"))),
            emptyList(), "1.3.0"
        )
        assertEquals(1, a.installs.size)
        assertEquals("2.0.0", a.installs.single().entry.version)
        assertEquals(1, a.rejected.size)

        // 高版本在后
        val b = UpdatePlanner.plan(
            KnowledgeManifest(packs = listOf(entry(version = "1.0.0"), entry(version = "2.0.0"))),
            emptyList(), "1.3.0"
        )
        assertEquals(1, b.installs.size)
        assertEquals("2.0.0", b.installs.single().entry.version)
        assertEquals(1, b.rejected.size)
    }

    // ==================================================================
    //  包内容校验
    // ==================================================================

    // ---------------- 支持者名单 ----------------

    private fun supporterJson(
        name: String,
        serial: Long? = null,
        date: String? = null,
        message: String? = null
    ) = buildJsonObject {
        put("name", name)
        serial?.let { put("serial", it) }
        date?.let { put("date", it) }
        message?.let { put("message", it) }
    }

    @Test
    fun `名单包可以正常解析`() {
        val pack = packOf(
            PackKind.SUPPORTERS,
            listOf(
                supporterJson("张三", serial = 1001, message = "加油"),
                supporterJson("李四", serial = 1002)
            )
        )
        val parsed = PackValidator.parse(pack)
        assertEquals(2, parsed.validCount)
        assertEquals(0, parsed.errors.size)
        assertEquals(listOf("张三", "李四"), parsed.supporters.map { it.name })
    }

    @Test
    fun `空名单包被放行而不是判为损坏`() {
        // 「还没人打赏」是正常状态。若沿用「零条目即整包拒绝」的规则，
        // 初始的空名单包会安装失败，用户看到的是「安装失败」而不是「还没有人留名」。
        val pack = packOf(PackKind.SUPPORTERS, emptyList(), packId = "tcm.supporters")
        assertNull(PackValidator.rejectReason(pack, "tcm.supporters"))
        assertFalse(PackValidator.shouldRejectAsWhole(PackValidator.parse(pack)))
    }

    @Test
    fun `其他类型的空包仍然被拒绝`() {
        // 放行空包只针对名单，卡片/题库的空包依然没有意义
        val cardPack = packOf(PackKind.CARDS, emptyList())
        assertTrue(PackValidator.shouldRejectAsWhole(PackValidator.parse(cardPack)))

        val questionPack = packOf(PackKind.QUESTIONS, emptyList())
        assertTrue(PackValidator.shouldRejectAsWhole(PackValidator.parse(questionPack)))
    }

    @Test
    fun `名单重名时通过日期或序号区分而不被丢弃`() {
        // 名单里重名很常见（多个"匿名"）。若 externalId 只用昵称派生，
        // 第二条同名人会被判为「ID 重复」而整条丢弃 —— 用户会以为自己的名字没上墙。
        val pack = packOf(
            PackKind.SUPPORTERS,
            listOf(
                supporterJson("匿名", serial = 1),
                supporterJson("匿名", serial = 2),
                supporterJson("匿名", date = "2026-10-01"),
                supporterJson("匿名", date = "2026-10-02")
            )
        )
        val parsed = PackValidator.parse(pack)
        assertEquals(4, parsed.validCount, "四条同名条目应全部保留")
        assertEquals(4, parsed.supporters.map { it.externalId }.toSet().size, "externalId 应互不相同")
    }

    @Test
    fun `名单昵称为空时该条被跳过`() {
        val pack = packOf(
            PackKind.SUPPORTERS,
            listOf(supporterJson(""), supporterJson("  "), supporterJson("正常"))
        )
        val parsed = PackValidator.parse(pack)
        assertEquals(1, parsed.validCount)
        assertEquals(2, parsed.errors.size)
    }

    private fun cardJson(id: String, front: String = "桂枝的功效", back: String = "发汗解肌") =
        buildJsonObject {
            put("externalId", id)
            put("front", front)
            put("back", back)
        }

    private fun packOf(
        kind: String,
        items: List<kotlinx.serialization.json.JsonElement>,
        packId: String = "tcm.test.core"
    ) = KnowledgePack(
        packId = packId,
        packName = "测试包",
        kind = kind,
        version = "1.0.0",
        items = items
    )

    @Test
    fun `结构合法的知识包能通过整包校验`() {
        val pack = packOf(PackKind.CARDS, listOf(cardJson("a"), cardJson("b")))
        assertNull(PackValidator.rejectReason(pack, "tcm.test.core"))
        assertEquals(2, PackValidator.parse(pack).validCount)
    }

    @Test
    fun `packId 与索引不一致时应整体拒绝`() {
        val pack = packOf(PackKind.CARDS, listOf(cardJson("a")))
        // 防张冠李戴：索引说是 A 包，内容却是 B 包
        assertNotNull(PackValidator.rejectReason(pack, "tcm.other.pack"))
    }

    @Test
    fun `不支持的高 schema 版本应整体拒绝`() {
        val pack = packOf(PackKind.CARDS, listOf(cardJson("a"))).copy(schemaVersion = 99)
        val reason = PackValidator.rejectReason(pack)
        assertNotNull(reason)
        assertTrue(reason!!.contains("schema"))
    }

    @Test
    fun `空白或未知类型的包被拒绝`() {
        assertNotNull(PackValidator.rejectReason(packOf(PackKind.CARDS, emptyList())))
        assertNotNull(PackValidator.rejectReason(packOf("unknown-kind", listOf(cardJson("a")))))
        assertNotNull(PackValidator.rejectReason(packOf(PackKind.CARDS, listOf(cardJson("a"))).copy(version = " ")))
    }

    @Test
    fun `个别坏条目被跳过而不影响整包`() {
        val pack = packOf(
            PackKind.CARDS,
            listOf(
                cardJson("ok1"),
                buildJsonObject { put("externalId", "bad"); put("front", ""); put("back", "") },
                cardJson("ok2")
            )
        )
        val parsed = PackValidator.parse(pack)
        assertEquals(2, parsed.validCount)
        assertEquals(1, parsed.errors.size)
        assertFalse(PackValidator.shouldRejectAsWhole(parsed), "错误率低时不应整包拒绝")
    }

    @Test
    fun `externalId 缺失时按内容自动派生而非判错`() {
        // 手写几千个 ID 不现实，缺失时应自动派生
        val pack = packOf(
            PackKind.CARDS,
            listOf(
                buildJsonObject { put("deck", "中药学"); put("front", "桂枝的功效"); put("back", "发汗解肌") },
                buildJsonObject { put("deck", "中药学"); put("front", "麻黄的功效"); put("back", "发汗散寒") }
            )
        )
        val parsed = PackValidator.parse(pack)
        assertEquals(2, parsed.validCount)
        assertEquals(0, parsed.errors.size)
        assertTrue(parsed.warnings.any { it.message.contains("自动派生") })
    }

    @Test
    fun `同一内容在不同位置派生出相同 externalId`() {
        // 这是「更新不产生重复条目」的前提
        val a = packOf(
            PackKind.CARDS,
            listOf(buildJsonObject { put("deck", "中药学"); put("front", "桂枝的功效"); put("back", "发汗解肌") })
        )
        val b = packOf(
            PackKind.CARDS,
            listOf(
                buildJsonObject { put("deck", "中药学"); put("front", "新条目"); put("back", "x") },
                buildJsonObject { put("deck", "中药学"); put("front", "桂枝的功效"); put("back", "发汗解肌") }
            )
        )
        val idA = PackValidator.parse(a).cards.single().externalId
        val idB = PackValidator.parse(b).cards.last().externalId
        assertEquals(idA, idB)
    }

    @Test
    fun `externalId 重复的条目被跳过`() {
        val pack = packOf(
            PackKind.CARDS,
            listOf(
                cardJson("dup"),
                cardJson("dup"),
                cardJson("unique")
            )
        )
        val parsed = PackValidator.parse(pack)
        assertEquals(2, parsed.validCount)
        assertEquals(1, parsed.errors.size)
    }

    @Test
    fun `损坏过半的包应整体拒绝`() {
        // 内容整体可疑时宁可回退，也不要把半坏的知识灌进学习资料
        val items = (1..9).map { i ->
            if (i <= 5) buildJsonObject { put("externalId", "bad$i"); put("front", ""); put("back", "") }
            else cardJson("ok$i")
        }
        val parsed = PackValidator.parse(packOf(PackKind.CARDS, items))
        assertEquals(4, parsed.validCount)
        assertTrue(PackValidator.shouldRejectAsWhole(parsed))
    }

    @Test
    fun `全部条目损坏的包应整体拒绝`() {
        val items = (1..3).map { i ->
            buildJsonObject { put("externalId", "bad$i"); put("front", ""); put("back", "") }
        }
        val parsed = PackValidator.parse(packOf(PackKind.CARDS, items))
        assertEquals(0, parsed.validCount)
        assertTrue(PackValidator.shouldRejectAsWhole(parsed))
    }

    @Test
    fun `题目包缺少答案字段会被跳过`() {
        val noAnswer = buildJsonObject {
            put("externalId", "q1")
            put("stem", "桂枝的功效是")
        }
        val withAnswer = buildJsonObject {
            put("externalId", "q2")
            put("stem", "麻黄的功效是")
            put("answer", "A")
        }
        val parsed = PackValidator.parse(packOf(PackKind.QUESTIONS, listOf(noAnswer, withAnswer)))
        assertEquals(1, parsed.questions.size)
        assertEquals(1, parsed.errors.size)
    }

    @Test
    fun `古籍包缺少正文会被跳过`() {
        val bad = buildJsonObject {
            put("externalId", "b1")
            put("title", "伤寒论")
        }
        val good = buildJsonObject {
            put("externalId", "b2")
            put("title", "金匮要略")
            put("content", "脏腑经络先后病脉证第一")
        }
        val parsed = PackValidator.parse(packOf(PackKind.BOOK, listOf(bad, good)))
        assertEquals(1, parsed.books.size)
        assertEquals(1, parsed.errors.size)
    }

    @Test
    fun `包内容与声明类型不匹配时给出警告`() {
        // 声明为题目包，但内容其实是卡片结构
        val parsed = PackValidator.parse(packOf(PackKind.QUESTIONS, listOf(cardJson("a"))))
        assertEquals(0, parsed.questions.size)
        assertTrue(parsed.warnings.isNotEmpty() || parsed.errors.isNotEmpty())
    }

    // ==================================================================
    //  编解码
    // ==================================================================

    @Test
    fun `带 BOM 的 JSON 也能正常解析`() {
        val text = """
            {"schemaVersion":1,"packs":[]}
        """.trimIndent()
        val withBom = ("\uFEFF" + text).toByteArray(Charsets.UTF_8)
        val manifest = PackCodec.parseManifest(withBom)
        assertEquals(1, manifest.schemaVersion)
    }

    @Test
    fun `索引中的未知字段不会导致解析失败`() {
        // 服务端将来加字段时，旧客户端必须还能用
        val json = """
            {"schemaVersion":1,"generatedAt":"2026-01-01T00:00:00Z","packs":[],"futureField":"x"}
        """.trimIndent().toByteArray()
        val manifest = PackCodec.parseManifestSafe(json)
        assertTrue(manifest.isSuccess)
    }

    @Test
    fun `索引合法条目能被完整解析`() {
        val json = """
            {
              "schemaVersion": 1,
              "minAppVersion": "1.3.0",
              "packs": [{
                "packId": "tcm.materia.core",
                "packName": "中药学核心",
                "kind": "cards",
                "version": "1.1.0",
                "itemCount": 120,
                "bytes": 48213,
                "sha256": "${"b".repeat(64)}",
                "url": "packs/materia-1.1.0.json"
              }]
            }
        """.trimIndent().toByteArray()

        val manifest = PackCodec.parseManifest(json)
        val e = manifest.packs.single()
        assertEquals("tcm.materia.core", e.packId)
        assertEquals(120, e.itemCount)
        assertTrue(Sha256.isWellFormed(e.sha256))
    }

    @Test
    fun `损坏的 JSON 解析失败但不抛到调用方`() {
        val result = PackCodec.parsePackSafe("{ 这不是 JSON".toByteArray())
        assertTrue(result.isFailure)
    }
}
