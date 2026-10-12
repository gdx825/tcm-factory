package com.tcmfactory.data.network

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream

/**
 * 纯 HTTP 抓取层：只负责「把 URL 的内容取回来，并限制体积」。
 *
 * 刻意不含任何 Android 依赖与传输安全判定 —— 前者让它可以跑在 JVM 单测里，
 * 后者属于策略、由调用方决定。这样下载链路（超时、截断保护、体积上限、
 * HTTP 错误码）都能用本地 MockWebServer 完整验证，而不必依赖外网。
 */
class HttpFetcher(private val client: OkHttpClient) {

    /**
     * 同步抓取。调用方负责切到 IO 线程。
     *
     * @param maxBytes 硬上限。边读边计数，超限立即中断 —— 不能先读完整再判断，
     *                 否则一个恶意的超大响应就能把进程内存吃光。
     * @param onProgress 已下载字节数 / 总字节数（总长未知时为 0）。
     */
    fun getBytes(
        url: String,
        maxBytes: Long,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json, text/plain, */*")
            .header("User-Agent", "TcmFactory-Android")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val message = response.message.ifBlank { "" }
                throw IllegalStateException("HTTP ${response.code} $message".trim())
            }
            val body = response.body ?: throw IllegalStateException("响应内容为空")

            val contentLength = body.contentLength()
            if (contentLength > maxBytes) {
                throw IllegalStateException(
                    "内容体积 ${formatBytes(contentLength)} 超过上限 ${formatBytes(maxBytes)}"
                )
            }
            val total = if (contentLength > 0) contentLength else 0L

            val output = ByteArrayOutputStream(
                if (contentLength in 1..(4L * 1024 * 1024)) contentLength.toInt() else 64 * 1024
            )
            val buffer = ByteArray(32 * 1024)
            var read = 0L

            body.byteStream().use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    read += n
                    if (read > maxBytes) {
                        throw IllegalStateException(
                            "实际下载体积超过上限 ${formatBytes(maxBytes)}，已中止"
                        )
                    }
                    output.write(buffer, 0, n)
                    onProgress?.invoke(read, total)
                }
            }
            if (read == 0L) throw IllegalStateException("响应内容为空")
            return output.toByteArray()
        }
    }

    private fun formatBytes(value: Long): String = when {
        value >= 1024 * 1024 -> "%.1f MB".format(value / 1024.0 / 1024.0)
        value >= 1024 -> "%.1f KB".format(value / 1024.0)
        else -> "$value B"
    }
}
