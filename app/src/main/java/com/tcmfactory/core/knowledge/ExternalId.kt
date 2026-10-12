package com.tcmfactory.core.knowledge

/**
 * 条目稳定标识生成。
 *
 * 关键性质：**由内容派生，而非由位置派生**。因为知识库更新时，
 * 条目的顺序可能变、中间可能插入新条目 —— 如果用「第 N 条」做 ID，
 * 排序一变，所有条目的 ID 都会错位，更新就成了灾难：
 * 用户的「桂枝」进度会被贴到「麻黄」上。
 *
 * 用内容派生后，同一条知识无论在包里排第几，ID 恒定不变，
 * 因此「更新内容」与「保留学习进度」可以同时成立。
 */
object ExternalId {

    /**
     * 用 \u0001 作为分隔符，避免不同字段拼接后产生歧义
     * （例如 ("ab","c") 与 ("a","bc") 不能撞成同一个 ID）。
     */
    fun of(vararg parts: String): String {
        val raw = parts.joinToString("\u0001") { it.trim() }
        return Sha256.of(raw.toByteArray(Charsets.UTF_8)).take(16)
    }

    /** 内置卡片的 ID：牌组名 + 正面文本。 */
    fun forBuiltinCard(deckName: String, front: String): String = of(deckName, front)

    /** 内置题目的 ID：科目 + 题干。 */
    fun forBuiltinQuestion(subject: String, stem: String): String = of(subject, stem)
}
