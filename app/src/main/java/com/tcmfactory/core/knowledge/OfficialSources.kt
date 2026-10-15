package com.tcmfactory.core.knowledge

/**
 * 官方知识库数据源。
 *
 * ## 为什么要预置而不是让用户自己填
 *
 * 数据源地址是一串又长又容易写错的 URL（`https://cdn.jsdelivr.net/gh/用户名/仓库@分支/目录`），
 * 让备考的学生去填这个，等于把「支持者名单能不能更新」这件事变成一道运维题。
 * 而名单、题库这些内容的更新，本来就该是开发者推一下、用户点一下的事。
 *
 * ## 为什么是三个源
 *
 * 这三个地址指向**同一份内容**，只是可达性不同：
 *
 * | 源 | 特点 |
 * |---|---|
 * | jsDelivr | 国内可直连，但走 CDN 缓存，更新可能滞后几小时 |
 * | GitHub Pages | 部署即时生效，但 `*.github.io` 在国内时常被 DNS 污染 |
 * | raw.githubusercontent | 官方直链，国内多数网络不可达，纯兜底 |
 *
 * 客户端按顺序尝试，第一个成功的就用（见
 * [com.tcmfactory.data.network.KnowledgeSourceClient.fetchManifestFromSources]）。
 * 所以顺序不是随便排的：可达性最好的放最前面，能连上就别折腾用户。
 *
 * ## 时效问题怎么解决
 *
 * jsDelivr 对分支引用的缓存是 12 小时，但拉取索引时会带一个时间戳参数
 * （见 [com.tcmfactory.data.network.KnowledgeSourceClient.fetchManifest]），
 * 相当于每次都是新 URL，直接回源取最新，不受缓存拖累。
 * 知识包本体不加这个参数 —— 它们的文件名里带版本号，本来就是「一版一个 URL」，
 * 缓存反而是好事。
 *
 * ## 用户可以删掉
 *
 * 这三个源是**首次启动时写入一次**的普通配置项，不是写死的常量。
 * 用户在「知识库 → 数据源设置」里删掉后不会被自动加回
 * （靠 [com.tcmfactory.core.datastore.KnowledgeSettingsRepository.markDefaultsSeeded] 的标记位）。
 * 自建源的用户有权让界面干净。
 */
object OfficialSources {

    private const val GITHUB_USER = "gdx825"
    private const val GITHUB_REPO = "tcm-factory"
    private const val BRANCH = "main"

    /** 仓库内存放发布产物的目录（manifest.json + packs/）。 */
    private const val PACK_DIR = "kb"

    /** 按可达性从优到劣排列。 */
    val URLS: List<String> = listOf(
        "https://cdn.jsdelivr.net/gh/$GITHUB_USER/$GITHUB_REPO@$BRANCH/$PACK_DIR",
        "https://$GITHUB_USER.github.io/$GITHUB_REPO/$PACK_DIR",
        "https://raw.githubusercontent.com/$GITHUB_USER/$GITHUB_REPO/$BRANCH/$PACK_DIR"
    )

    /** 仓库地址，用于在界面上给用户一个「这是什么」的交代。 */
    const val REPO_URL = "https://github.com/$GITHUB_USER/$GITHUB_REPO"

    /**
     * 是否是官方源。
     *
     * 判据是**完整前缀**（含用户名），而不是「地址里出现仓库名」——
     * 后者会把 `cdn.jsdelivr.net/gh/别人的账号/tcm-factory@main/kb` 这种
     * fork 出来的源也标成「官方」。那不只是显示难看：它会让用户以为
     * 这份内容是开发者发布的，从而放下戒心。宁可漏判，不可误判。
     *
     * 分支/tag 部分不参与比较，所以 `@main` 与 `@v2.0.0` 都认。
     */
    fun isOfficial(url: String): Boolean {
        val u = url.trim().lowercase().trimEnd('/')
        return OFFICIAL_PREFIXES.any { u.startsWith(it) }
    }

    private val OFFICIAL_PREFIXES = listOf(
        "https://cdn.jsdelivr.net/gh/${GITHUB_USER.lowercase()}/${GITHUB_REPO.lowercase()}@",
        "https://${GITHUB_USER.lowercase()}.github.io/${GITHUB_REPO.lowercase()}/",
        "https://raw.githubusercontent.com/${GITHUB_USER.lowercase()}/${GITHUB_REPO.lowercase()}/"
    )

    /** 界面展示用的短名。非官方源返回 null，界面就不显示任何标签。 */
    fun displayName(url: String): String? {
        if (!isOfficial(url)) return null
        val u = url.lowercase()
        return when {
            u.contains("jsdelivr.net") -> "官方源 · jsDelivr"
            u.contains("github.io") -> "官方源 · GitHub Pages"
            u.contains("raw.githubusercontent.com") -> "官方源 · GitHub 直链"
            else -> "官方源"
        }
    }
}
