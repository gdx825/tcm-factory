package com.tcmfactory.core.license

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 激活码链路的单元测试。
 *
 * 这里的**有效激活码是用 Python 签发工具真实签发的**（`tools/make_license.py`），
 * 不是手工构造的假数据。这一点很关键：它同时验证了
 *
 *   1. Python 的 Base32 编码与 Kotlin 的解码一致
 *   2. Python 的 r||s 定长签名与 Kotlin 的 DER 转换一致
 *   3. 两侧的载荷字节序（大端）、字段偏移、长度完全一致
 *
 * 跨语言的二进制协议最容易错的就是这几处，而且错了之后在界面上只表现为
 * 「正版用户激活不了」—— 没有这份测试，几乎不可能靠肉眼发现。
 */
class LicenseVerifierTest {

    /** 真实签发：3650 天有效，序号 1001。 */
    private val code10y =
        "TCMF1AEAQ4QQAAAB6SAI7ACS6YQV6OIBQVQFCWY6SEAPRPPQ33MUCFUWVD7QR5ZW64AS" +
            "DMTKHGIXBUH2X5N5SVHFQDLWVXVC56M43IIIPCWFQ6W2MNADY3CFPJ25G"

    /** 真实签发：永久有效，序号 1002。 */
    private val codeForever =
        "TCMF1AEA777YAAAB6UAI7ADR4RVPUSPVKLDY6L2RK3TL657OMGKC3365QB2GZZJ6LFPBU" +
            "A226EKBHOF3UPK7VYPFZILIKNSSCBI7WUY3MLDYBDTBWMXBYRPBQDBE6"

    // ---------------- 正向：真实签发的码必须通过 ----------------

    @Test
    fun `真实签发的限期激活码可以验证通过`() {
        val result = LicenseVerifier.verify(code10y)
        assertTrue(result is LicenseCheckResult.Valid, "应验证通过，实际：$result")

        val payload = (result as LicenseCheckResult.Valid).payload
        assertEquals(1, payload.version)
        assertEquals(LicenseFormat.TIER_STANDARD, payload.tier)
        assertEquals(3650, payload.validDays)
        assertEquals(1001L, payload.serial)
        assertFalse(payload.isForever)
        // 签发日期应能解出且不为 0（2026-10-15 ≈ 第 287 天）
        assertTrue(payload.issuedDays > 0, "签发日期应已写入，实际 ${payload.issuedDays}")
    }

    @Test
    fun `真实签发的永久激活码可以验证通过`() {
        val result = LicenseVerifier.verify(codeForever)
        assertTrue(result is LicenseCheckResult.Valid, "应验证通过，实际：$result")

        val payload = (result as LicenseCheckResult.Valid).payload
        assertTrue(payload.isForever)
        assertEquals(1002L, payload.serial)
        // 永久授权不应算出到期时间，否则界面会显示一个错误的到期日
        assertNull(payload.expiresAt(System.currentTimeMillis()))
    }

    // ---------------- 容错：粘贴场景 ----------------

    @Test
    fun `粘贴时混入空格换行与连字符仍可识别`() {
        val messy = code10y
            .chunked(8)
            .joinToString("-")
            .lowercase()
            .chunked(40)
            .joinToString("\n  ")

        val result = LicenseVerifier.verify(messy)
        assertTrue(result is LicenseCheckResult.Valid, "应容错通过，实际：$result")
    }

    @Test
    fun `全角空格与零宽字符不影响识别`() {
        val messy = code10y
            .chunked(10)
            .joinToString("\u3000")             // 全角空格
            .replace("TCMF1", "TCMF1\u200B")    // 零宽空格

        val result = LicenseVerifier.verify(messy)
        assertTrue(result is LicenseCheckResult.Valid, "应容错通过，实际：$result")
    }

    // ---------------- 反向：伪造与篡改必须被拒绝 ----------------

    @Test
    fun `逐字符篡改全遍历——任何单个字符的改动都必须被拒绝`() {
        // 这个测试最初只抽查了 4 个位置，恰好漏掉了末尾的填充位缺陷：
        // 当时总长 72 字节不是 5 的倍数，Base32 末尾有 4 位死数据被解码器丢弃，
        // 于是改动最后一个字符可能仍然验证通过。
        // 改为全遍历后，这类"某几位不参与校验"的问题无法再漏过。
        val alphabet = LicenseFormat.BASE32_ALPHABET
        var checked = 0

        for (pos in code10y.indices) {
            val original = code10y[pos]
            val idx = alphabet.indexOf(original)
            // 换成字母表里的下一个字符，保证与原字符不同且仍是合法 Base32 字符
            val replacement = alphabet[(idx + 1) % alphabet.length]
            if (replacement == original) continue

            val tampered = code10y.substring(0, pos) + replacement + code10y.substring(pos + 1)
            val result = LicenseVerifier.verify(tampered)

            assertFalse(
                result is LicenseCheckResult.Valid,
                "位置 $pos（$original → $replacement）被篡改后不应通过，实际：$result"
            )
            checked++
        }
        // 确认真的遍历完了，避免循环条件写错导致"零次断言也算通过"
        assertEquals(code10y.length, checked, "应遍历全部 ${code10y.length} 个字符位置")
    }

    @Test
    fun `篡改载荷（把有效期改成永久）会被拒绝`() {
        // 攻击者最想做的就是把有限期改成永久。
        // 载荷第 2~3 字节（有效期）落在 Base32 的前几个字符里，改动它必然破坏签名。
        val chars = code10y.toCharArray()
        chars[6] = if (chars[6] == 'A') 'B' else 'A'
        val result = LicenseVerifier.verify(String(chars))
        assertFalse(result is LicenseCheckResult.Valid, "应被拒绝，实际：$result")
    }

    @Test
    fun `用另一把密钥签发的码无法通过`() {
        // 构造一个结构完全合法、但签名来自别的密钥对的激活码。
        // 这类码在结构校验阶段看不出任何问题，只有验签才能识破 ——
        // 这正是采用非对称签名而非对称密钥的意义。
        val payload = ByteArray(LicenseFormat.PAYLOAD_BYTES)
        payload[0] = 1                              // 版本
        payload[1] = 1                              // 档位
        payload[2] = 0x0F; payload[3] = 0xA0.toByte()   // 有效期 4000 天
        payload[7] = 0x11                           // 序号末位
        val fakeSignature = ByteArray(64) { (it * 7 % 251).toByte() }
        val code = LicenseFormat.PREFIX + Base32.encode(payload + fakeSignature)

        assertEquals(LicenseFormat.ENCODED_CHARS, code.length - LicenseFormat.PREFIX.length)
        val result = LicenseVerifier.verify(code)
        assertTrue(result is LicenseCheckResult.BadSignature, "应报签名无效，实际：$result")
    }

    @Test
    fun `长度不足的激活码报格式错误而非签名错误`() {
        val result = LicenseVerifier.verify(code10y.dropLast(10))
        assertTrue(result is LicenseCheckResult.Malformed, "应报格式错误，实际：$result")
    }

    @Test
    fun `多余字符导致长度不符时被拒绝`() {
        // 复制时多带了尾随内容（例如从聊天记录里连带复制了标点外的字母）
        val result = LicenseVerifier.verify(code10y + "ABCD")
        assertTrue(result is LicenseCheckResult.Malformed, "应报格式错误，实际：$result")
    }

    @Test
    fun `前缀不正确的激活码被拒绝`() {
        val result = LicenseVerifier.verify("XXXX1" + code10y.drop(5))
        assertTrue(result is LicenseCheckResult.Malformed)
    }

    @Test
    fun `空字符串与纯空白被拒绝`() {
        assertTrue(LicenseVerifier.verify("") is LicenseCheckResult.Malformed)
        assertTrue(LicenseVerifier.verify("   \n  ") is LicenseCheckResult.Malformed)
    }

    @Test
    fun `含非法字符（Base32 字母表之外）被拒绝`() {
        // 0、1、8、9 不在 Base32 字母表中，替换进去必然解码失败
        val tampered = code10y.substring(0, 20) + "0" + code10y.substring(21)
        val result = LicenseVerifier.verify(tampered)
        assertTrue(result is LicenseCheckResult.Malformed, "应报格式错误，实际：$result")
    }

    // ---------------- 版本门槛 ----------------

    @Test
    fun `版本号高于当前支持的激活码提示升级应用`() {
        // 手工构造一个版本号为 2 且签名有效的码是做不到的（拿不到私钥），
        // 因此这里单独验证「版本号如何影响用户提示」这一分支。
        val payload = LicensePayload(
            version = LicenseFormat.VERSION_CURRENT + 1,
            tier = LicenseFormat.TIER_STANDARD,
            validDays = 30,
            serial = 1,
            issuedDays = 0
        )
        val message = LicenseCheckResult.UnsupportedVersion(payload.version).message()
        // 提示语必须提到"需要更高版本"，否则用户只会以为码是假的
        assertTrue(message.contains("更高版本"))
    }

    // ---------------- 到期计算 ----------------

    @Test
    fun `到期时间从激活时刻起算而不是签发时刻`() {
        // 签发于 100 天前，用户今天才激活 —— 到期应从**激活当天**算起。
        // 若从签发日算起，"压箱底的码"一激活就过期，对用户不公平。
        val payload = LicensePayload(
            version = 1,
            tier = LicenseFormat.TIER_STANDARD,
            validDays = 30,
            serial = 1,
            issuedDays = 100
        )
        val day = 86_400_000L
        val activatedAt = 1_792_022_400_000L      // 2026-10-15

        val expires = assertNotNull(payload.expiresAt(activatedAt))
        assertEquals(activatedAt + 30L * day, expires)

        // 同一张码，若晚一个月才激活，到期也随之后移一个月。
        // 这一条才是"相对计算"的关键证据：绝对日期方案在这里会算出同样的结果，
        // 而这个用例会立刻失败。
        val later = activatedAt + 30L * day
        assertEquals(later + 30L * day, assertNotNull(payload.expiresAt(later)))

        // 到期应当晚于签发（正常情况下用户不会在签发前激活）
        assertTrue(expires > payload.issuedAt(), "到期时间应晚于签发时间")
    }

    @Test
    fun `永久授权不产生到期时间且不受激活时刻影响`() {
        val payload = LicensePayload(
            version = 1,
            tier = LicenseFormat.TIER_STANDARD,
            validDays = LicenseFormat.VALID_FOREVER,
            serial = 7,
            issuedDays = 0
        )
        assertTrue(payload.isForever)
        assertNull(payload.expiresAt(0L))
        assertNull(payload.expiresAt(1_792_022_400_000L))
    }

    // ---------------- 底层编解码 ----------------

    @Test
    fun `Base32 编解码往返一致`() {
        val samples = listOf(
            ByteArray(0),
            byteArrayOf(0),
            byteArrayOf(0x7F, 0x00),
            ByteArray(75) { (it * 3 % 256).toByte() },
            ByteArray(1) { 0xFF.toByte() }
        )
        for (data in samples) {
            val encoded = Base32.encode(data)
            val decoded = Base32.decode(encoded)
            assertNotNull(decoded, "解码不应失败：$encoded")
            assertTrue(
                data.contentEquals(decoded),
                "往返不一致：长度 ${data.size} → $encoded → 长度 ${decoded.size}"
            )
        }
    }

    @Test
    fun `Base32 编码不含易混字符 0 1 8 9`() {
        val encoded = Base32.encode(ByteArray(75) { (it * 11 % 256).toByte() })
        for (c in "0189") {
            assertFalse(encoded.contains(c), "Base32 不应包含易混字符 $c：$encoded")
        }
    }

    @Test
    fun `Base32 编码长度为 120 且与签发工具一致`() {
        val encoded = Base32.encode(ByteArray(LicenseFormat.TOTAL_BYTES))
        assertEquals(LicenseFormat.ENCODED_CHARS, encoded.length)
        assertEquals(120, encoded.length)
        // 总长必须是 5 的整数倍，否则会产生被丢弃的填充位
        assertEquals(0, LicenseFormat.TOTAL_BYTES % 5, "总字节数必须是 5 的整数倍")
    }

    @Test
    fun `r 与 s 的最高位为 1 时 DER 转换补前导零`() {
        // 这是 DER 编码最经典的坑：INTEGER 是有符号的，
        // r 或 s 首字节 >= 0x80 时必须补 0x00，否则会被解析成负数导致验签失败。
        val raw = ByteArray(64)
        raw[0] = 0x80.toByte()          // r 的最高位为 1
        raw[31] = 0xFF.toByte()
        raw[32] = 0x7F                  // s 的最高位为 0
        raw[63] = 0x01

        val der = assertNotNull(EcdsaSignatureCodec.rawToDer(raw))
        assertTrue(der.size >= 70)
        assertEquals(0x30.toByte(), der[0])
        assertEquals(0x02.toByte(), der[2])
        // r 因最高位为 1，长度 33，且首字节为 0x00
        assertEquals(33, der[3].toInt())
        assertEquals(0x00.toByte(), der[4])
        assertEquals(0x80.toByte(), der[5])
    }

    @Test
    fun `DER 与定长 r-s 互相转换往返一致`() {
        val raw = ByteArray(64) { ((it * 37 + 11) % 256).toByte() }
        val der = assertNotNull(EcdsaSignatureCodec.rawToDer(raw))
        val back = assertNotNull(EcdsaSignatureCodec.derToRaw(der))
        assertTrue(raw.contentEquals(back), "往返不一致")
    }

    @Test
    fun `长度不正确的签名转 DER 返回 null`() {
        assertNull(EcdsaSignatureCodec.rawToDer(ByteArray(63)))
        assertNull(EcdsaSignatureCodec.rawToDer(ByteArray(65)))
    }
}
