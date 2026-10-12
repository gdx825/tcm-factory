package com.tcmfactory.core.knowledge

/**
 * 本地已安装知识包的快照，用于更新决策。
 * 刻意与 Room 实体解耦，让决策逻辑可以在纯 JVM 上单测。
 */
data class InstalledPackInfo(
    val packId: String,
    val packName: String,
    val version: String,
    val kind: String,
    val source: String
)

/** 单个包的更新决策结果。 */
sealed class PlanItem {
    abstract val entry: ManifestEntry

    /** 本地没有，首次安装。 */
    data class Install(override val entry: ManifestEntry) : PlanItem()

    /** 远端版本更高，覆盖更新。 */
    data class Update(
        override val entry: ManifestEntry,
        val fromVersion: String,
        /** 已装包来源。FILE 表示用户手动导入，覆盖前应在界面上明确提示。 */
        val fromSource: String
    ) : PlanItem()

    /** 已是最新，无需动作。 */
    data class UpToDate(
        override val entry: ManifestEntry,
        val installedVersion: String
    ) : PlanItem()

    /** 索引项本身不可信（缺 sha256、URL 非法、重名等），拒绝处理。 */
    data class Rejected(
        override val entry: ManifestEntry,
        val reason: String
    ) : PlanItem()
}

data class UpdatePlan(
    val manifest: KnowledgeManifest,
    val items: List<PlanItem>,
    /**
     * 实际解析成功的那个数据源地址。
     *
     * 必须带回来：检查更新时若第一个源不可达会自动回退到下一个，
     * 但下载阶段如果还去用「列表里的第一个」，就会出现
     * 「检查成功、下载必然失败」这种自相矛盾的结果。
     */
    val resolvedBaseUrl: String = "",
    /** manifest 要求的应用版本高于当前版本时为 true，此时 [items] 为空。 */
    val blockedByAppVersion: Boolean = false,
    val blockReason: String? = null
) {
    val installs: List<PlanItem.Install> get() = items.filterIsInstance<PlanItem.Install>()
    val updates: List<PlanItem.Update> get() = items.filterIsInstance<PlanItem.Update>()
    val upToDate: List<PlanItem.UpToDate> get() = items.filterIsInstance<PlanItem.UpToDate>()
    val rejected: List<PlanItem.Rejected> get() = items.filterIsInstance<PlanItem.Rejected>()

    /** 需要实际下载的包。 */
    val pending: List<PlanItem> get() = installs + updates

    val hasWork: Boolean get() = pending.isNotEmpty()

    val pendingItemCount: Int get() = pending.sumOf { it.entry.itemCount }

    val pendingBytes: Long get() = pending.sumOf { it.entry.bytes }
}

/**
 * 更新决策器：把「远端索引」与「本地已装」比对成一份可执行计划。
 *
 * 这是纯函数，不碰网络也不碰数据库 —— 更新逻辑最容易出错的地方正是
 * 「该不该下、要不要覆盖、覆盖谁」，把它抽出来单测，比在真机上试便宜得多。
 */
object UpdatePlanner {

    fun plan(
        manifest: KnowledgeManifest,
        installed: List<InstalledPackInfo>,
        currentAppVersion: String
    ): UpdatePlan {
        // 1) 应用版本门槛
        if (!manifest.minAppVersion.isNullOrBlank() &&
            VersionComparator.compare(manifest.minAppVersion, currentAppVersion) > 0
        ) {
            return UpdatePlan(
                manifest = manifest,
                items = emptyList(),
                blockedByAppVersion = true,
                blockReason = "该知识源要求应用版本不低于 ${manifest.minAppVersion}，当前为 $currentAppVersion。请先升级「中医工厂」App。"
            )
        }

        // 2) 索引自检：不合法条目直接拒绝，避免带病下载
        val byId = HashMap<String, InstalledPackInfo>(installed.size)
        installed.forEach { byId[it.packId] = it }

        val seen = HashMap<String, ManifestEntry>()
        val items = ArrayList<PlanItem>(manifest.packs.size)

        manifest.packs.forEach { entry ->
            val reject = rejectReason(entry)
            if (reject != null) {
                items += PlanItem.Rejected(entry, reject)
                return@forEach
            }

            val duplicated = seen[entry.packId]
            if (duplicated != null) {
                // 同一索引里同 packId 出现多次：保留版本高者，另一个拒绝。
                if (VersionComparator.compare(entry.version, duplicated.version) > 0) {
                    val idx = items.indexOfFirst { it.entry === duplicated }
                    if (idx >= 0) items[idx] = PlanItem.Rejected(duplicated, "索引中 packId 重复，已保留更高版本")
                    seen[entry.packId] = entry
                } else {
                    items += PlanItem.Rejected(entry, "索引中 packId 重复，已保留更高版本")
                    return@forEach
                }
            } else {
                seen[entry.packId] = entry
            }

            val local = byId[entry.packId]
            when {
                local == null -> items += PlanItem.Install(entry)
                VersionComparator.shouldReplace(entry.version, local.version) ->
                    items += PlanItem.Update(entry, local.version, local.source)
                else -> items += PlanItem.UpToDate(entry, local.version)
            }
        }

        return UpdatePlan(manifest = manifest, items = items)
    }

    private fun rejectReason(entry: ManifestEntry): String? = when {
        entry.packId.isBlank() -> "packId 为空"
        !PACK_ID_PATTERN.matches(entry.packId) -> "packId 含非法字符（只允许小写字母、数字、点、下划线、连字符）"
        entry.version.isBlank() -> "version 为空"
        entry.packName.isBlank() -> "packName 为空"
        entry.kind !in PackKind.ALL -> "未知的 kind：${entry.kind}"
        entry.url.isBlank() -> "url 为空"
        !Sha256.isWellFormed(entry.sha256) -> "sha256 缺失或格式非法（应为 64 位十六进制）"
        entry.bytes < 0 -> "bytes 为负数"
        entry.itemCount < 0 -> "itemCount 为负数"
        else -> null
    }

    private val PACK_ID_PATTERN = Regex("^[a-z0-9][a-z0-9._-]{1,63}$")
}
