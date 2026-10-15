package com.tcmfactory.data.network

import com.tcmfactory.core.datastore.KnowledgeSettingsRepository
import com.tcmfactory.core.knowledge.KnowledgeManifest
import com.tcmfactory.core.knowledge.KnowledgePack
import com.tcmfactory.core.knowledge.ManifestEntry
import com.tcmfactory.core.knowledge.PackCodec
import com.tcmfactory.core.knowledge.SourceUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 知识库数据源客户端。
 *
 * 职责 = [HttpFetcher] 的抓取能力 + **传输安全策略**。
 * 所有「要不要装、装到哪」的判断都在 UpdatePlanner / KnowledgeRepository，
 * 这里保持无状态，方便替换与测试。
 */
@Singleton
class KnowledgeSourceClient @Inject constructor(
    private val fetcher: HttpFetcher,
    private val settings: KnowledgeSettingsRepository
) {

    /** 索引文件体积上限。索引只含元信息，超过说明源有问题。 */
    private val manifestLimit = 2L * 1024 * 1024

    /** 单个知识包体积上限。古籍整书包可能较大，留到 16MB。 */
    private val packHardLimit = 16L * 1024 * 1024

    /**
     * 依次尝试多个数据源，返回第一个成功的 (baseUrl, manifest)。
     *
     * 为什么要多源：国内网络环境下个别 CDN 会阶段性不可达，
     * 与其给用户一句「更新失败」，不如自动换下一个镜像。
     *
     * @param fresh 为 true 时在索引地址上附加时间戳，绕开 CDN 缓存。
     *   用户手动点「检查更新」时用 true —— 他刚在后台发布了新名单，
     *   期待的是立刻看到，而不是「12 小时后才会出现」。
     *   后台自动检查用 false，走缓存更快也更省流量。
     */
    suspend fun fetchManifestFromSources(
        urls: List<String>,
        fresh: Boolean = false
    ): Result<Pair<String, KnowledgeManifest>> {
        if (urls.isEmpty()) {
            return Result.failure(IllegalStateException("尚未配置知识库数据源"))
        }
        val failures = StringBuilder()
        for (raw in urls) {
            val base = SourceUrl.normalize(raw)
            if (base == null) {
                failures.append("· $raw：地址格式非法\n")
                continue
            }
            val result = fetchManifest(base, fresh = fresh)
            if (result.isSuccess) {
                return Result.success(base to result.getOrThrow())
            }
            val message = result.exceptionOrNull()?.message ?: "未知错误"
            failures.append("· ${SourceUrl.hostOf(base)}：$message\n")
        }
        return Result.failure(
            IllegalStateException("所有数据源均不可用：\n${failures.toString().trimEnd()}")
        )
    }

    /** 拉取索引。默认路径为源根目录下的 manifest.json。 */
    suspend fun fetchManifest(
        baseUrl: String,
        path: String = "manifest.json",
        fresh: Boolean = false
    ): Result<KnowledgeManifest> {
        val resolved = SourceUrl.resolve(baseUrl, path)
            ?: return Result.failure(IllegalArgumentException("无法解析索引地址：$baseUrl / $path"))
        val url = if (fresh) SourceUrl.withCacheBuster(resolved) else resolved
        return getBytes(url, manifestLimit, null).mapCatching { PackCodec.parseManifest(it) }
    }

    /** 下载单个知识包。 */
    suspend fun downloadPack(
        baseUrl: String,
        entry: ManifestEntry,
        onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
    ): Result<ByteArray> {
        val url = SourceUrl.resolve(baseUrl, entry.url)
            ?: return Result.failure(IllegalArgumentException("无法解析包地址：${entry.url}"))
        // 上限基于索引声明的体积放宽一倍，并有 4MB 下限。
        // 下限不能太小：若第三方源没填 bytes，1MB 的上限会让正常的古籍包直接失败。
        val limit = (entry.bytes * 2 + 2L * 1024 * 1024).coerceIn(4L * 1024 * 1024, packHardLimit)
        return getBytes(url, limit, onProgress)
    }

    // ---- 解析委托给 PackCodec（纯逻辑，可单测） ----

    fun parseManifest(bytes: ByteArray): KnowledgeManifest = PackCodec.parseManifest(bytes)
    fun parsePack(bytes: ByteArray): KnowledgePack = PackCodec.parsePack(bytes)
    fun parseManifestSafe(bytes: ByteArray): Result<KnowledgeManifest> = PackCodec.parseManifestSafe(bytes)
    fun parsePackSafe(bytes: ByteArray): Result<KnowledgePack> = PackCodec.parsePackSafe(bytes)

    // ================= 内部 =================

    private suspend fun getBytes(
        url: String,
        maxBytes: Long,
        onProgress: ((Long, Long) -> Unit)?
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            assertTransportAllowed(url)
            fetcher.getBytes(url, maxBytes, onProgress)
        }
    }

    /**
     * 传输安全策略。默认只允许 HTTPS；明文 HTTP 必须由用户在设置里显式开启，
     * 因为知识库内容直接影响学习正确性，可被中间人替换的风险不能默认接受。
     */
    private suspend fun assertTransportAllowed(url: String) {
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw IllegalStateException("仅支持 http(s) 协议的数据源：$url")
        }
        if (SourceUrl.isCleartext(url) && !settings.current().allowCleartext) {
            throw IllegalStateException(
                "该数据源使用明文 HTTP 传输，默认已阻止。若这是你自建的局域网源，" +
                    "请在「知识库 → 数据源设置」中开启「允许明文传输」。"
            )
        }
    }
}
