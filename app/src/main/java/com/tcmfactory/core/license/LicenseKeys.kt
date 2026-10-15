package com.tcmfactory.core.license

/**
 * 内置的验签公钥。
 *
 * ## 为什么公钥可以明放在 App 里
 *
 * 激活码用的是**非对称签名**：App 只持有公钥，它能验证"这个码确实由私钥签发"，
 * 但**无法据此生成新的激活码**。因此即便有人反编译 APK 取出这串公钥，也毫无用处。
 *
 * 这正是选择非对称方案的核心原因。若改用对称方案（App 内藏一个 HMAC 密钥），
 * 一旦被逆向提取，网上立刻会出现"激活码生成器"，风险是全网性的。
 *
 * ## 私钥在哪
 *
 * 私钥**不在这个仓库里**，也不在 APK 里，只保存在开发者本地。
 * 它一旦泄露，等同于任何人都能签发有效激活码 —— 这是整个商业化方案里最需要保管的东西。
 *
 * ## 如果将来要更换密钥
 *
 * 换公钥意味着**老激活码全部失效**。因此更换时必须同时保留旧公钥并让验证器依次尝试，
 * 否则已购用户会被挡在门外。见 [LicenseVerifier.verify] 的 `publicKeys` 参数。
 */
object LicenseKeys {

    /**
     * X.509 SubjectPublicKeyInfo 格式的 P-256 公钥，Base64 编码。
     * 该格式可被 `java.security.KeyFactory` 的 `X509EncodedKeySpec` 直接解析，
     * 无需任何 ASN.1 手工解析。
     */
    const val PUBLIC_KEY_BASE64: String =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEiPRDNDseIK56Z9fOb06ER4lee0TuNO" +
            "VhJ2Dp9w/fSIevH+a8J5oAgSpT1C3+ntTHxC++3TSo33n4u89Mn9RdDQ=="

    /** 供界面展示的密钥指纹（前 8 位），换钥时用于人工核对。 */
    val fingerprint: String get() = PUBLIC_KEY_BASE64.take(8)
}
