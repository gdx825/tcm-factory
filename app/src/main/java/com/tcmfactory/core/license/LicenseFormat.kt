package com.tcmfactory.core.license

/**
 * 激活码格式定义与底层编解码。
 *
 * ## 为什么是「离线签名」而不是「短码 + 联网验证」
 *
 * 本产品主打完全离线可用，激活这一步也不应该例外。若采用短码联网校验，
 * 就必须维护一台服务器，一旦服务器欠费或下线，所有已购用户会被自己的激活机制锁在门外
 * —— 对买断制产品来说这是不可接受的。因此激活码自带签名，App 本地验签即可判定真伪。
 *
 * ## 为什么签名用 ECDSA P-256 而不是 Ed25519
 *
 * Ed25519 更现代，但 Android 直到 API 33 才提供原生支持，而本项目 minSdk 26。
 * P-256 自 API 1 起就由 `java.security` 支持，且 JVM 与 Android 行为一致，
 * 这意味着**验签逻辑可以在普通 JVM 上跑完整的单元测试**，不需要真机或 Robolectric。
 *
 * ## 编码布局
 *
 * ```
 * 字符串   TCMF1 + Base32(载荷 11 字节 || 签名 64 字节)    共 125 字符
 * 载荷     [0]     版本
 *          [1]     授权档位
 *          [2..3]  有效期天数（大端 uint16，0xFFFF 表示永久）
 *          [4..7]  序号（大端 uint32，用于追溯泄露源）
 *          [8..9]  签发日期（自 2026-01-01 的天数，大端 uint16）
 *          [10]    保留位（恒为 0）
 * 签名     r || s，各 32 字节定长
 * ```
 *
 * ## 为什么载荷是 11 字节这种"不整齐"的长度
 *
 * 因为 **11 + 64 = 75 字节，恰好是 5 的整数倍**，Base32 编码后没有任何填充位。
 *
 * 这不是审美问题，而是一个真实缺陷的修复：最初载荷定为 8 字节时，总长 72 字节，
 * Base32 编出来 116 字符共 580 位，而数据只需 576 位 —— **末尾有 4 位是死数据**，
 * 解码时被丢弃。后果是篡改激活码的最后一个字符**可能仍然验证通过**，
 * 与"改一个字符就失效"的直觉不符。单元测试发现后，改为让长度整除来根治：
 * 现在每一个字符都携带有效数据，改动任何一位都会破坏签名。
 *
 * ## 为什么签名存 r||s 而不是直接存 DER
 *
 * `Signature.verify()` 只接受 DER 编码的 ECDSA 签名，而 DER 长度会在 70~72 字节之间浮动，
 * 会让激活码长度不一致、不利于人工核对。定长 64 字节则保证任何激活码都是同一长度。
 */
object LicenseFormat {

    const val PREFIX = "TCMF1"

    const val PAYLOAD_BYTES = 11
    const val SIGNATURE_BYTES = 64
    const val TOTAL_BYTES = PAYLOAD_BYTES + SIGNATURE_BYTES

    /** Base32 编码后的字符数（无填充位，因为 [TOTAL_BYTES] 是 5 的整数倍）。 */
    const val ENCODED_CHARS = TOTAL_BYTES / 5 * 8

    /** Base32 编码所用的字母表，仅用于文档与测试断言。 */
    const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** 签发日期的计时起点（2026-01-01 UTC），与签发工具保持一致。 */
    const val EPOCH_DAY_MS = 1_767_225_600_000L

    /** 当前支持的载荷版本。 */
    const val VERSION_CURRENT = 1

    /** 标准版（目前只有一档，保留字段以便日后推出不同档位）。 */
    const val TIER_STANDARD = 1

    /** 有效期天数为此值时表示永久授权。 */
    const val VALID_FOREVER = 0xFFFF

    /** 冒号后为版本的展示用分钟数，无实义，仅用于生成人类可读的说明。 */
    fun tierLabel(tier: Int): String = when (tier) {
        TIER_STANDARD -> "标准版"
        else -> "未知档位($tier)"
    }
}

/**
 * 激活码中承载的授权信息。
 *
 * 注意：[validDays] 是**相对天数**而不是绝对到期日 —— 因为激活码在售出时生成，
 * 而用户可能过很久才激活。用绝对日期会让"压箱底的码"一激活就过期，
 * 用相对天数则从**首次激活时刻**起算，对用户更公平。
 */
data class LicensePayload(
    val version: Int,
    val tier: Int,
    val validDays: Int,
    val serial: Long,
    /** 签发日期（自 [LicenseFormat.EPOCH_DAY_MS] 的天数），用于排查"这个码是什么时候发的"。 */
    val issuedDays: Int
) {
    /** 是否为永久授权。 */
    val isForever: Boolean get() = validDays == LicenseFormat.VALID_FOREVER

    /** 从激活时刻起算的到期时间（毫秒）。永久授权返回 null。 */
    fun expiresAt(activatedAtMs: Long): Long? =
        if (isForever) null else activatedAtMs + validDays.toLong() * 86_400_000L

    /** 签发日期（毫秒时间戳），仅供展示与排查。 */
    fun issuedAt(): Long = LicenseFormat.EPOCH_DAY_MS + issuedDays.toLong() * 86_400_000L
}

/**
 * Base32 编解码（RFC 4648 字母表，无填充）。
 *
 * 选 Base32 而非 Base64 的唯一理由是**易混字符**：Base32 的字母表是 A–Z 与 2–7，
 * 不含 0/1/8/9，用户对着屏幕手抄时不会把 O 和 0、l 和 1 搞混。
 * 激活码以复制粘贴为主，但手抄场景一旦发生，这个选择就能救命。
 */
object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val sb = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0L
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toLong() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[((buffer shr (bits - 5)) and 0x1F).toInt()])
                bits -= 5
            }
            // 及时裁掉已消费的高位，避免 Long 累积溢出
            buffer = buffer and ((1L shl bits) - 1)
        }
        if (bits > 0) {
            sb.append(ALPHABET[((buffer shl (5 - bits)) and 0x1F).toInt()])
        }
        return sb.toString()
    }

    /**
     * 解码。
     *
     * 空输入返回**空数组**而不是 null —— 保持与 [encode] 的往返一致性
     * （encode(空) 得到空串，decode(空串) 应还原为空数组）。
     * 空串在 [LicenseVerifier] 里会被长度检查提前拦下，不会流入后续逻辑。
     */
    fun decode(text: String): ByteArray? {
        val input = text.uppercase()
        if (input.isEmpty()) return ByteArray(0)

        val out = java.io.ByteArrayOutputStream(input.length * 5 / 8 + 1)
        var buffer = 0L
        var bits = 0
        for (c in input) {
            val idx = ALPHABET.indexOf(c)
            if (idx < 0) return null
            buffer = (buffer shl 5) or idx.toLong()
            bits += 5
            if (bits >= 8) {
                out.write(((buffer shr (bits - 8)) and 0xFF).toInt())
                bits -= 8
                buffer = buffer and ((1L shl bits) - 1)
            }
        }
        // 长度是 5 的整数倍时不会残留位；若残留，说明长度不合法。
        // 这里严格拒绝而不是静默丢弃 —— 静默丢弃正是"改一位仍通过"那次缺陷的成因。
        if (bits != 0 && buffer != 0L) return null
        return out.toByteArray()
    }
}

/**
 * 定长 r||s 签名与 DER 签名之间的转换。
 *
 * `java.security.Signature.verify()` 只吃 DER，而激活码里存的是定长 r||s，
 * 所以验签前必须转回来。这段代码看着啰嗦，但它是整条链路里最容易出错的一环
 * （前导零、最高位符号位），因此单独成对象并配了专门的单元测试。
 */
object EcdsaSignatureCodec {

    /**
     * r||s（各 32 字节，大端）→ DER 编码。
     * 输入长度不为 64 时返回 null。
     */
    fun rawToDer(raw: ByteArray): ByteArray? {
        if (raw.size != 64) return null
        val r = derInteger(raw.copyOfRange(0, 32))
        val s = derInteger(raw.copyOfRange(32, 64))
        val content = r + s
        return byteArrayOf(0x30) + derLength(content.size) + content
    }

    /**
     * DER 编码 → r||s（各 32 字节）。
     * 主要用于签发工具的自检，正常验签流程不需要。
     */
    fun derToRaw(der: ByteArray): ByteArray? {
        var pos = 0
        if (der.size < 2 || der[0] != 0x30.toByte()) return null
        pos = 1
        val (seqLen, next) = readLength(der, pos) ?: return null
        pos = next
        if (pos + seqLen > der.size) return null

        val (rBytes, afterR) = readInteger(der, pos) ?: return null
        val (sBytes, afterS) = readInteger(der, afterR) ?: return null
        if (afterS > der.size) return null

        val out = ByteArray(64)
        // 左侧补零到 32 字节；若超过 32 字节（异常数据）则截取低 32 字节
        copyRightAligned(rBytes, out, 0)
        copyRightAligned(sBytes, out, 32)
        return out
    }

    private fun copyRightAligned(src: ByteArray, dst: ByteArray, offset: Int) {
        if (src.size >= 32) {
            System.arraycopy(src, src.size - 32, dst, offset, 32)
        } else {
            System.arraycopy(src, 0, dst, offset + (32 - src.size), src.size)
        }
    }

    private fun derInteger(value: ByteArray): ByteArray {
        var start = 0
        while (start < value.size - 1 && value[start] == 0.toByte()) start++
        var body = value.copyOfRange(start, value.size)
        // DER 的 INTEGER 是有符号的：最高位为 1 时必须前置 0x00，否则会被解析成负数
        if (body.isNotEmpty() && (body[0].toInt() and 0x80) != 0) {
            body = byteArrayOf(0) + body
        }
        return byteArrayOf(0x02) + derLength(body.size) + body
    }

    private fun derLength(len: Int): ByteArray =
        if (len < 0x80) byteArrayOf(len.toByte())
        else byteArrayOf(0x81.toByte(), (len and 0xFF).toByte())

    private fun readLength(der: ByteArray, pos: Int): Pair<Int, Int>? {
        if (pos >= der.size) return null
        val first = der[pos].toInt() and 0xFF
        return if (first < 0x80) {
            first to pos + 1
        } else {
            val count = first and 0x7F
            if (count == 0 || count > 2 || pos + count >= der.size) return null
            var len = 0
            for (i in 1..count) len = (len shl 8) or (der[pos + i].toInt() and 0xFF)
            len to pos + count + 1
        }
    }

    private fun readInteger(der: ByteArray, pos: Int): Pair<ByteArray, Int>? {
        if (pos >= der.size || der[pos] != 0x02.toByte()) return null
        val (len, next) = readLength(der, pos + 1) ?: return null
        if (next + len > der.size) return null
        return der.copyOfRange(next, next + len) to (next + len)
    }
}
