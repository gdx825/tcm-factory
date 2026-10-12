package com.tcmfactory.core.knowledge

import java.security.MessageDigest

/**
 * SHA-256 校验工具。
 *
 * 为什么要校验：知识库来自网络，传输过程中的截断、CDN 缓存污染、
 * 甚至是恶意替换，都会让客户端静默写入错误内容。中医知识讲究准确性，
 * 一条被篡改的穴位定位比"更新失败"危险得多。因此**校验不通过一律丢弃**，
 * 没有「跳过校验」的开关。
 */
object Sha256 {

    private const val HEX = "0123456789abcdef"

    fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    fun of(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    /**
     * 常量时间比较，避免因提前返回而泄露摘要信息。
     * 同时容忍大小写差异与首尾空白（部分工具导出时会带上）。
     */
    fun matches(expected: String, bytes: ByteArray): Boolean {
        val e = expected.trim().lowercase()
        if (e.length != 64) return false
        if (!e.all { it in '0'..'9' || it in 'a'..'f' }) return false
        return constantTimeEquals(e, of(bytes))
    }

    fun isWellFormed(expected: String?): Boolean {
        if (expected == null) return false
        val e = expected.trim().lowercase()
        return e.length == 64 && e.all { it in '0'..'9' || it in 'a'..'f' }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
