package com.tcmfactory.core.knowledge

/**
 * 数据源地址处理。
 *
 * 抽成纯函数是为了能单测 —— URL 拼接错一个斜杠就会 404，
 * 而这类错误在真机上只能靠用户反馈发现，成本很高。
 */
object SourceUrl {

    private const val HTTP = "http://"
    private const val HTTPS = "https://"

    /**
     * 规范化基础地址：去空白、去尾部斜杠。
     * 非法地址（无 scheme、缺主机名、含空格、scheme 非 http(s)）返回 null。
     */
    fun normalize(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        val lower = s.lowercase()
        if (!lower.startsWith(HTTP) && !lower.startsWith(HTTPS)) return null
        if (s.any { it.isWhitespace() }) return null

        // 必须真的有主机名。否则 "https://" 会被规范化成 "https:"，
        // 交给 HTTP 客户端后才失败，报错信息对用户毫无意义。
        val afterScheme = s.substringAfter("://", "")
        val host = afterScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        if (host.isEmpty()) return null
        if (host.all { it == ':' }) return null

        // 去掉末尾所有斜杠，拼接时统一补一个
        val trimmed = s.trimEnd('/')
        if (trimmed.substringAfter("://", "").isEmpty()) return null
        return trimmed
    }

    /** 把索引里的相对路径解析为绝对地址；已是绝对地址则原样返回。 */
    fun resolve(baseUrl: String, path: String): String? {
        val p = path.trim()
        if (p.isEmpty()) return null
        val lower = p.lowercase()
        if (lower.startsWith(HTTP) || lower.startsWith(HTTPS)) return p
        val base = normalize(baseUrl) ?: return null
        return "$base/${p.trimStart('/')}"
    }

    /** 是否为明文 HTTP（用于安全策略判定）。 */
    fun isCleartext(url: String): Boolean = url.trim().lowercase().startsWith(HTTP)

    /** 主机名，仅用于界面展示。 */
    fun hostOf(url: String): String {
        val afterScheme = url.substringAfter("://", "")
        return afterScheme.substringBefore('/').ifBlank { url }
    }
}
