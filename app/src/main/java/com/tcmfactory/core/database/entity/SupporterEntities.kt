package com.tcmfactory.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 支持者名单条目。
 *
 * ## 为什么单独建表而不是塞进 cards 或 questions
 *
 * 名单与学习内容没有任何共同字段，塞进现有表只会让那些表的语义变浑浊
 * （比如「卡片」表里躺着一个只有昵称的行）。它是完全独立的领域概念，
 * 独立成表后，清空或重建名单也不会波及任何学习数据。
 *
 * ## 为什么用 (packId, externalId) 唯一索引
 *
 * 与 cards / questions 一致的做法：名单包安装时按这个组合判断条目是否已存在，
 * 从而支持「同一份名单重复安装不产生重复行」。
 *
 * ## 名单是全量替换
 *
 * 与其他知识包不同，名单包安装时会**先删除同 packId 的旧记录再整体写入**。
 * 原因见 [com.tcmfactory.core.knowledge.PackKind.SUPPORTERS] 的注释：
 * 增量合并会让「已退出名单」的人永远留在列表里。
 */
@Entity(
    tableName = "supporters",
    indices = [Index(value = ["packId", "externalId"], unique = true)]
)
data class SupporterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 来源知识包标识。内置名单与联网名单可用不同的 packId。 */
    val packId: String? = null,

    /** 包内稳定标识，用于幂等写入。 */
    val externalId: String? = null,

    /** 展示用昵称。 */
    val name: String,

    /** 可选留言。 */
    val message: String? = null,

    /** 支持者码中的序号，便于与签发记录对应；可空。 */
    val serial: Long? = null,

    /** 展示用日期文本（如 2026-10-15）。刻意存字符串：它只用于展示，不参与逻辑。 */
    val displayDate: String? = null,

    /** 展示顺序。由打包工具按打赏时间倒序写入。 */
    val sortOrder: Int = 0,

    val updatedAt: Long
)
