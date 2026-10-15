package com.tcmfactory.core.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官方数据源的配置正确性。
 *
 * 这些用例的价值在于：数据源地址是**写死在代码里的常量**，
 * 一旦拼错（少个斜杠、分支名写错、用户名大小写不一致），
 * 表现是「用户点了检查更新，永远说所有源都不可用」——
 * 而开发者本地往往测不出来，因为本地根本没走网络。
 */
class OfficialSourcesTest {

    @Test
    fun `每个官方源都是合法地址`() {
        OfficialSources.URLS.forEach { url ->
            assertNotNull("地址格式非法，客户端会直接跳过：$url", SourceUrl.normalize(url))
        }
    }

    @Test
    fun `官方源之间不重复`() {
        assertEquals(
            "存在重复地址，会白白多试一轮网络请求",
            OfficialSources.URLS.size,
            OfficialSources.URLS.toSet().size
        )
    }

    @Test
    fun `主源是 jsDelivr`() {
        // 顺序即优先级：客户端用第一个成功的源。jsDelivr 在国内可达性最好，
        // 所以它必须排在最前面 —— 排错了不会报错，只会让所有用户都走那条
        // 打不开的路，然后掉到备源上去，白白慢几秒。
        assertTrue(
            "第一顺位应为 jsDelivr，实际：${OfficialSources.URLS.first()}",
            OfficialSources.URLS.first().contains("cdn.jsdelivr.net")
        )
    }

    @Test
    fun `三个源指向同一份内容目录`() {
        // 同一个仓库、同一个分支、同一个子目录，只是域名不同。
        OfficialSources.URLS.forEach { url ->
            assertTrue("地址里应包含仓库名：$url", url.lowercase().contains("tcm-factory"))
            assertTrue("地址应以 kb 结尾：$url", url.endsWith("/kb"))
        }
    }

    @Test
    fun `官方源被识别为官方`() {
        OfficialSources.URLS.forEach { url ->
            assertTrue("应识别为官方：$url", OfficialSources.isOfficial(url))
            assertNotNull("应给出中文短名：$url", OfficialSources.displayName(url))
        }
    }

    @Test
    fun `分支名或大小写不同仍算官方`() {
        assertTrue(OfficialSources.isOfficial("https://cdn.jsdelivr.net/gh/gdx825/tcm-factory@v2.0.1/kb"))
        assertTrue(OfficialSources.isOfficial("https://cdn.jsdelivr.net/gh/GDX825/TCM-Factory@main/kb"))
        assertTrue(OfficialSources.isOfficial("https://cdn.jsdelivr.net/gh/gdx825/tcm-factory@main/kb/"))
        assertTrue(OfficialSources.isOfficial("  https://gdx825.github.io/tcm-factory/kb  "))
    }

    @Test
    fun `别人 fork 出来的源不能算官方`() {
        // 这是最需要防的一种误判：第三方把同样结构的仓库挂到 jsDelivr，
        // 界面若标成「官方源」，用户会以为内容出自开发者，从而放下戒心。
        assertFalse(
            OfficialSources.isOfficial("https://cdn.jsdelivr.net/gh/someone-else/tcm-factory@main/kb")
        )
        assertFalse(
            OfficialSources.isOfficial("https://someone-else.github.io/tcm-factory/kb")
        )
        assertFalse(
            OfficialSources.isOfficial("https://raw.githubusercontent.com/someone-else/tcm-factory/main/kb")
        )
        // 连前后缀都要对得上，"gdx825evil" 这种更不行
        assertFalse(
            OfficialSources.isOfficial("https://cdn.jsdelivr.net/gh/gdx825evil/tcm-factory@main/kb")
        )
    }

    @Test
    fun `无关地址既不认官方也不给短名`() {
        listOf(
            "https://example.com/tcm-kb",
            "https://cdn.jsdelivr.net/npm/some-package",
            "http://192.168.1.8:8080/kb"
        ).forEach { url ->
            assertFalse("不应识别为官方：$url", OfficialSources.isOfficial(url))
            assertNull("不应给出短名：$url", OfficialSources.displayName(url))
        }
    }

    @Test
    fun `三种源各有可读的短名`() {
        assertEquals("官方源 · jsDelivr", OfficialSources.displayName(OfficialSources.URLS[0]))
        assertEquals("官方源 · GitHub Pages", OfficialSources.displayName(OfficialSources.URLS[1]))
        assertEquals("官方源 · GitHub 直链", OfficialSources.displayName(OfficialSources.URLS[2]))
    }
}
