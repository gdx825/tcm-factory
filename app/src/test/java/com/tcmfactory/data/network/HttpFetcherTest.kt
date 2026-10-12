package com.tcmfactory.data.network

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 下载链路的真实 HTTP 验证（本地 MockWebServer，不依赖外网）。
 *
 * 这些用例覆盖的都是「弱网 / 脏数据服务端」才会触发、但一旦触发就会
 * 让用户拿到半截内容或吃光内存的分支，靠手工点按界面几乎测不到。
 */
class HttpFetcherTest {

    private lateinit var server: MockWebServer
    private lateinit var fetcher: HttpFetcher

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        fetcher = HttpFetcher(
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `正常下载返回响应原始字节`() {
        server.enqueue(MockResponse().setBody("""{"schemaVersion":1}"""))

        val bytes = fetcher.getBytes(server.url("/manifest.json").toString(), 1024 * 1024)

        assertEquals("""{"schemaVersion":1}""", bytes.decodeToString())
    }

    @Test
    fun `HTTP 错误码抛出带状态码的异常`() {
        server.enqueue(MockResponse().setResponseCode(404))

        val error = assertFailsWith<IllegalStateException> {
            fetcher.getBytes(server.url("/missing.json").toString(), 1024)
        }

        assertTrue(error.message.orEmpty().contains("404"))
    }

    @Test
    fun `服务端返回 500 时明确失败`() {
        server.enqueue(MockResponse().setResponseCode(500))

        assertFailsWith<IllegalStateException> {
            fetcher.getBytes(server.url("/boom.json").toString(), 1024)
        }
    }

    @Test
    fun `声明体积超过上限时提前拒绝`() {
        // Content-Length 已知，应在读取前就拒绝，避免无谓传输
        server.enqueue(MockResponse().setBody("x".repeat(5000)))

        assertFailsWith<IllegalStateException> {
            fetcher.getBytes(server.url("/big.json").toString(), 1000)
        }
    }

    @Test
    fun `分块传输时靠边读边计数兜底中断`() {
        // 不提供 Content-Length，模拟恶意或异常的服务端：
        // 若先读完整再判断体积，进程内存会被吃光
        server.enqueue(MockResponse().setChunkedBody("y".repeat(8000), 1024))

        assertFailsWith<IllegalStateException> {
            fetcher.getBytes(server.url("/chunked.json").toString(), 1000)
        }
    }

    @Test
    fun `进度回调报告已下载与总字节数`() {
        val payload = "z".repeat(2048)
        server.enqueue(MockResponse().setBody(payload))

        var lastDownloaded = 0L
        var lastTotal = 0L
        val bytes = fetcher.getBytes(server.url("/p.json").toString(), 1024 * 1024) { done, total ->
            lastDownloaded = done
            lastTotal = total
        }

        assertEquals(2048, bytes.size)
        assertEquals(2048L, lastDownloaded)
        assertEquals(2048L, lastTotal)
    }

    @Test
    fun `空响应体被视为错误而不是成功返回空内容`() {
        // 否则会得到一个 0 字节的「知识包」，校验阶段才失败，错误信息更难懂
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        assertFailsWith<IllegalStateException> {
            fetcher.getBytes(server.url("/empty.json").toString(), 1024)
        }
    }

    @Test
    fun `中文内容按 UTF-8 往返不损坏`() {
        val text = """{"packName":"中药学 · 核心药材","note":"桂枝汤"}"""
        server.enqueue(MockResponse().setBody(text))

        val bytes = fetcher.getBytes(server.url("/cn.json").toString(), 1024 * 1024)

        assertEquals(text, bytes.decodeToString())
    }
}
