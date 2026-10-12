package com.tcmfactory.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tcmfactory.core.knowledge.SourceUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.knowledgeDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "knowledge_settings")

/**
 * 知识库数据源配置。
 */
data class KnowledgeSources(
    /**
     * 按顺序尝试的镜像地址。多个源的意义在于：国内访问 GitHub 系 CDN 不稳定，
     * 用户可以把同一份内容同时挂到 jsDelivr / Gitee / 自建服务器，
     * 客户端遇到失败自动切下一个，而不是直接报「更新失败」。
     */
    val urls: List<String> = emptyList(),
    /**
     * 是否允许明文 HTTP。默认关闭——知识库内容会直接影响学习正确性，
     * 明文传输可被中间人替换。仅当用户自建局域网源时才需要打开。
     */
    val allowCleartext: Boolean = false,
    /** 启动时静默检查是否有更新。 */
    val autoCheck: Boolean = false,
    val lastCheckAt: Long = 0L
) {
    val isConfigured: Boolean get() = urls.isNotEmpty()
}

@Singleton
class KnowledgeSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val sources: Flow<KnowledgeSources> = context.knowledgeDataStore.data.map { it.toSources() }

    suspend fun current(): KnowledgeSources = context.knowledgeDataStore.data.first().toSources()

    /**
     * 追加一个源。地址非法或已存在时返回 false。
     *
     * 注意不要用「在 edit 里改外部变量」的写法：DataStore 的 edit 在遇到
     * 并发写入时会**重放整个 lambda**，用外部 var 记录结果会得到与磁盘不一致的返回值。
     * 这里先读快照判断，再执行写入。
     */
    suspend fun addSource(raw: String): Boolean {
        val normalized = SourceUrl.normalize(raw) ?: return false
        val existing = current().urls
        if (normalized in existing) return false
        context.knowledgeDataStore.edit { prefs ->
            prefs[Keys.URLS] = (existing + normalized).joinToString("\n")
        }
        return true
    }

    suspend fun removeSource(url: String) {
        context.knowledgeDataStore.edit { prefs ->
            val remaining = prefs[Keys.URLS].orEmpty()
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != url }
            prefs[Keys.URLS] = remaining.joinToString("\n")
        }
    }

    suspend fun setAllowCleartext(value: Boolean) {
        context.knowledgeDataStore.edit { it[Keys.ALLOW_CLEARTEXT] = value }
    }

    suspend fun setAutoCheck(value: Boolean) {
        context.knowledgeDataStore.edit { it[Keys.AUTO_CHECK] = value }
    }

    suspend fun markChecked(at: Long = System.currentTimeMillis()) {
        context.knowledgeDataStore.edit { it[Keys.LAST_CHECK_AT] = at }
    }

    /** 已安装内置知识包的 App 版本号，用于避免每次启动重复解析。 */
    suspend fun bundledPacksVersion(): String? =
        context.knowledgeDataStore.data.first()[Keys.BUNDLED_PACKS_VERSION]

    suspend fun setBundledPacksVersion(version: String) {
        context.knowledgeDataStore.edit { it[Keys.BUNDLED_PACKS_VERSION] = version }
    }

    private fun Preferences.toSources() = KnowledgeSources(
        urls = this[Keys.URLS].orEmpty()
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() },
        allowCleartext = this[Keys.ALLOW_CLEARTEXT] ?: false,
        autoCheck = this[Keys.AUTO_CHECK] ?: false,
        lastCheckAt = this[Keys.LAST_CHECK_AT] ?: 0L
    )

    private object Keys {
        val URLS = stringPreferencesKey("source_urls")
        val ALLOW_CLEARTEXT = booleanPreferencesKey("allow_cleartext")
        val AUTO_CHECK = booleanPreferencesKey("auto_check")
        val LAST_CHECK_AT = longPreferencesKey("last_check_at")
        val BUNDLED_PACKS_VERSION = stringPreferencesKey("bundled_packs_version")
    }
}
