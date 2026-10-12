package com.tcmfactory.core.knowledge

/**
 * 语义化版本比较。
 *
 * 为什么自己写而不用 [java.lang.Comparable]：知识库版本号来自外部数据源，
 * 可能是 `1.2`、`1.2.3.4`、`2.0.0-beta.1`、甚至 `v1.2` 这类非规范写法。
 * 这类输入**不能抛异常**——一旦某个包版本号写得奇怪就让整个更新流程崩掉，
 * 用户只能卸载重装。所以这里的原则是「尽力解析，解析不了的段按 0 处理」。
 *
 * 比较规则：
 *  1. 先按 `-` 切开主版本与预发布标识；有预发布标识的**小于**没有的
 *     （1.0.0-beta < 1.0.0），符合 SemVer 直觉。
 *  2. 主版本按 `.` 分段逐段数值比较，缺失段视为 0（1.2 == 1.2.0）。
 *  3. 数值段相同时，段数多者大（1.2.1 > 1.2）。
 *  4. 非数字段（如 `1.2.x`）按 0 处理，不报错。
 */
object VersionComparator {

    fun compare(a: String?, b: String?): Int {
        // null / 空串视为最低版本，这样「未安装」天然小于任何已发布版本。
        if (a.isNullOrBlank() && b.isNullOrBlank()) return 0
        if (a.isNullOrBlank()) return -1
        if (b.isNullOrBlank()) return 1

        val pa = parse(a)
        val pb = parse(b)

        val maxLen = maxOf(pa.core.size, pb.core.size)
        for (i in 0 until maxLen) {
            val va = pa.core.getOrElse(i) { 0 }
            val vb = pb.core.getOrElse(i) { 0 }
            if (va != vb) return if (va > vb) 1 else -1
        }

        // 主版本相同：有预发布标识的更小。
        return when {
            pa.preRelease == null && pb.preRelease == null -> 0
            pa.preRelease == null -> 1
            pb.preRelease == null -> -1
            else -> comparePreRelease(pa.preRelease, pb.preRelease)
        }
    }

    /** candidate 是否严格新于 current（current 为空表示未安装，恒为 true）。 */
    fun isNewer(candidate: String?, current: String?): Boolean = compare(candidate, current) > 0

    /**
     * 是否需要覆盖安装：仅当候选版本**高于**已装版本。
     * 远端版本低于本地（回滚场景）时返回 false，避免把用户已更新的内容降级。
     */
    fun shouldReplace(candidate: String?, installed: String?): Boolean = compare(candidate, installed) > 0

    // ---------------- 内部实现 ----------------

    private data class Parsed(val core: List<Int>, val preRelease: String?)

    private fun parse(raw: String): Parsed {
        var s = raw.trim()
        // 容忍 v / V 前缀
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1)

        val dash = s.indexOf('-')
        val corePart: String
        val pre: String?
        if (dash >= 0) {
            corePart = s.substring(0, dash)
            pre = s.substring(dash + 1).ifBlank { null }
        } else {
            // 也容忍 `+build` 元数据
            val plus = s.indexOf('+')
            corePart = if (plus >= 0) s.substring(0, plus) else s
            pre = null
        }

        val core = corePart.split('.', '-', '_', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

        return Parsed(core.ifEmpty { listOf(0) }, pre)
    }

    private fun comparePreRelease(a: String, b: String): Int {
        val sa = a.split('.', '-', '_')
        val sb = b.split('.', '-', '_')
        val maxLen = maxOf(sa.size, sb.size)
        for (i in 0 until maxLen) {
            val xa = sa.getOrNull(i)
            val xb = sb.getOrNull(i)
            if (xa == null) return -1
            if (xb == null) return 1
            val na = xa.toIntOrNull()
            val nb = xb.toIntOrNull()
            val cmp = when {
                na != null && nb != null -> na.compareTo(nb)
                na != null -> -1   // 数字段优先于字符串段（SemVer 约定）
                nb != null -> 1
                else -> xa.compareTo(xb)
            }
            if (cmp != 0) return if (cmp > 0) 1 else -1
        }
        return 0
    }
}
