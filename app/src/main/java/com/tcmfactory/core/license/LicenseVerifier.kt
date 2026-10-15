package com.tcmfactory.core.license

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * 激活码校验结果。
 *
 * 刻意把「格式不对」与「签名不对」区分开，因为两者的用户含义完全不同：
 * 前者通常是粘贴时混入了多余字符（可自行修正），后者说明这串码并不是本程序签发的
 * （抄错、伪造，或来自别的软件）。提示语不可能一样。
 */
sealed interface LicenseCheckResult {

    /** 校验通过。 */
    data class Valid(val payload: LicensePayload) : LicenseCheckResult

    /** 字符串结构不对（长度、字符集、前缀）。 */
    data class Malformed(val reason: String) : LicenseCheckResult

    /** 结构正确但签名不通过 —— 伪造、抄错，或来自其他软件。 */
    data object BadSignature : LicenseCheckResult

    /** 结构正确、签名有效，但版本号超出本 App 支持范围（用户装的是旧版）。 */
    data class UnsupportedVersion(val version: Int) : LicenseCheckResult

    /** 面向用户的提示语。 */
    fun message(): String = when (this) {
        is Valid -> "激活成功"
        is Malformed -> reason
        is BadSignature -> "激活码无效。请检查是否复制完整，或与卖家核对。"
        is UnsupportedVersion ->
            "这个激活码需要更高版本的应用（码版本 v$version）。请先更新应用。"
    }
}

/**
 * 激活码校验器。
 *
 * 纯 `java.security` 实现，不依赖任何 Android API，因此可以在普通 JVM 上完整单元测试。
 * 这一点很重要：验签逻辑一旦写错，表现是"正版用户激活不了"或"假码能通过"，
 * 前者是事故、后者是损失，都属于不发版就来不及补救的问题。
 */
object LicenseVerifier {

    /**
     * 校验激活码。
     *
     * @param code 用户输入或粘贴的内容，允许含空格、换行、连字符（粘贴时常会带上）
     * @param publicKeys 候选公钥。传入多个是为了支持**密钥轮换** ——
     *   将来若更换签名密钥，把新公钥加在列表最前、旧公钥留在后面，
     *   这样已购用户的老码依然有效，不会因为换钥而被锁在门外。
     */
    fun verify(
        code: String,
        publicKeys: List<String> = listOf(LicenseKeys.PUBLIC_KEY_BASE64)
    ): LicenseCheckResult {
        val normalized = normalize(code)
        if (normalized.length <= LicenseFormat.PREFIX.length) {
            return LicenseCheckResult.Malformed("激活码太短，请检查是否复制完整。")
        }
        if (!normalized.startsWith(LicenseFormat.PREFIX)) {
            return LicenseCheckResult.Malformed("激活码格式不正确，应以前缀 ${LicenseFormat.PREFIX} 开头。")
        }

        val body = normalized.substring(LicenseFormat.PREFIX.length)
        // 长度必须严格相等：Base32 无填充位时，长度变化必然意味着用户少复制或多粘贴了内容
        if (body.length != LicenseFormat.ENCODED_CHARS) {
            return LicenseCheckResult.Malformed(
                "激活码长度不正确（应为 ${LicenseFormat.ENCODED_CHARS} 个字符，实际 ${body.length} 个）。"
            )
        }

        val raw = Base32.decode(body)
            ?: return LicenseCheckResult.Malformed("激活码含有非法字符，请重新复制。")
        if (raw.size != LicenseFormat.TOTAL_BYTES) {
            return LicenseCheckResult.Malformed("激活码长度不正确，请重新复制。")
        }

        val payloadBytes = raw.copyOfRange(0, LicenseFormat.PAYLOAD_BYTES)
        val signature = raw.copyOfRange(LicenseFormat.PAYLOAD_BYTES, raw.size)

        // 先验签，再解析载荷 —— 顺序不能反。
        // 否则未经校验的载荷会被拿去解析，等于把攻击者可控的数据当成可信输入。
        val signed = publicKeys.any { verifySignature(payloadBytes, signature, it) }
        if (!signed) return LicenseCheckResult.BadSignature

        val payload = parsePayload(payloadBytes)
            ?: return LicenseCheckResult.Malformed("激活码内容无法解析，请与卖家核对。")

        if (payload.version > LicenseFormat.VERSION_CURRENT) {
            return LicenseCheckResult.UnsupportedVersion(payload.version)
        }
        if (payload.tier != LicenseFormat.TIER_STANDARD) {
            return LicenseCheckResult.Malformed("激活码档位无法识别，请与卖家核对。")
        }
        if (payload.validDays <= 0) {
            return LicenseCheckResult.Malformed("激活码有效期异常，请与卖家核对。")
        }
        return LicenseCheckResult.Valid(payload)
    }

    /** 去掉大小写差异与所有非字母数字字符（空格、换行、连字符、全角空格等）。 */
    fun normalize(code: String): String =
        code.filter { it.isLetterOrDigit() }.uppercase()

    private fun parsePayload(bytes: ByteArray): LicensePayload? {
        if (bytes.size != LicenseFormat.PAYLOAD_BYTES) return null
        val version = bytes[0].toInt() and 0xFF
        val tier = bytes[1].toInt() and 0xFF
        val validDays = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        var serial = 0L
        for (i in 4..7) serial = (serial shl 8) or (bytes[i].toLong() and 0xFF)
        val issuedDays = ((bytes[8].toInt() and 0xFF) shl 8) or (bytes[9].toInt() and 0xFF)
        // [10] 是保留位，签发工具恒写 0。这里不做强制校验：
        // 签名已经保证了内容来自私钥持有者，保留位的取值不影响安全性，
        // 而强制校验只会在将来启用该字段时造成不必要的兼容问题。
        if (version == 0) return null
        return LicensePayload(
            version = version,
            tier = tier,
            validDays = validDays,
            serial = serial,
            issuedDays = issuedDays
        )
    }

    private fun verifySignature(
        payload: ByteArray,
        rawSignature: ByteArray,
        publicKeyBase64: String
    ): Boolean = runCatching {
        val der = EcdsaSignatureCodec.rawToDer(rawSignature) ?: return false
        val keyBytes = Base64.getDecoder().decode(publicKeyBase64)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(payload)
        verifier.verify(der)
    }.getOrDefault(false)
}
