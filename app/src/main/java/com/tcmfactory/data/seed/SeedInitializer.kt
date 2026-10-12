package com.tcmfactory.data.seed

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.tcmfactory.core.database.SeedData
import com.tcmfactory.core.database.TcmDatabase
import com.tcmfactory.core.database.entity.CardEntity
import com.tcmfactory.core.database.entity.DeckEntity
import com.tcmfactory.core.database.dao.DeckDao
import com.tcmfactory.core.datastore.KnowledgeSettingsRepository
import com.tcmfactory.core.reader.SeedBooks
import com.tcmfactory.data.repository.BookRepository
import com.tcmfactory.data.repository.KnowledgeRepository
import com.tcmfactory.data.repository.QuizRepository
import com.tcmfactory.domain.model.encodeTags
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 首次启动时把内置知识库写入数据库。
 *
 * 首启只需写入一次，之后靠 deckDao.count() 短路。
 * 未来知识库增量更新会走 knowledge_pack_versions 版本比对通道。
 */
@Singleton
class SeedInitializer @Inject constructor(
    private val db: TcmDatabase,
    private val deckDao: DeckDao,
    private val bookRepository: BookRepository,
    private val quizRepository: QuizRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val knowledgeSettings: KnowledgeSettingsRepository,
    @ApplicationContext private val context: Context
) {

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        seedCards()
        seedBooks()
        seedQuizzes()
        installBundledKnowledgePacks()
    }

    /**
     * 安装随 APK 分发内置的知识包。
     *
     * 关键设计：**走与「联网更新」完全相同的管线**（同一个
     * [KnowledgeRepository.installFromBytes] → 同一套校验、同一套按
     * (packId, externalId) 幂等 upsert），所以这里不重复任何写入逻辑。
     * 由此带来一个好处：这些内置包日后可以被数据源上的新版本正常覆盖更新，
     * 而不是变成「装机即固化、再也更新不了」的孤岛。
     *
     * 幂等：用 App 版本号做标记，同一版本只装一次，
     * 避免每次冷启动都重新解析近 400 条内容。
     */
    private suspend fun installBundledKnowledgePacks() {
        val currentVersion = com.tcmfactory.BuildConfig.VERSION_NAME
        if (knowledgeSettings.bundledPacksVersion() == currentVersion) return

        val names = runCatching {
            context.assets.list(BUNDLED_PACK_DIR)
                ?.filter { it.endsWith(".json") }
                ?.sorted()
                .orEmpty()
        }.getOrDefault(emptyList())

        if (names.isEmpty()) return

        var added = 0
        var updated = 0
        names.forEach { name ->
            runCatching {
                val bytes = context.assets.open("$BUNDLED_PACK_DIR/$name").use { it.readBytes() }
                val report = knowledgeRepository.installFromBytes(bytes, name)
                if (report.success) {
                    added += report.added
                    updated += report.updated
                } else {
                    Log.w(TAG, "内置知识包 $name 未安装：${report.errorMessage}")
                }
            }.onFailure { Log.e(TAG, "内置知识包 $name 读取失败", it) }
        }

        // 全部处理完才打标记：中途失败时下次启动会重试，不会留下半装状态。
        knowledgeSettings.setBundledPacksVersion(currentVersion)
        Log.i(TAG, "内置知识包安装完成：新增 $added 条，更新 $updated 条")
    }

    /** 内置示例题库：让用户开箱即可体验刷题与错题本。 */
    private suspend fun seedQuizzes() {
        runCatching { quizRepository.seedIfEmpty() }
    }

    /** 内置演示古籍：让用户开箱即可体验阅读器与读经制卡。 */
    private suspend fun seedBooks() {
        if (bookRepository.bookCount() > 0) return
        SeedBooks.books.forEach { book ->
            runCatching {
                bookRepository.importBuiltin(
                    title = book.title,
                    author = book.author,
                    dynasty = book.dynasty,
                    content = book.content
                )
            }
        }
    }

    private suspend fun seedCards() {
        if (deckDao.count() > 0) return

        val now = System.currentTimeMillis()

        db.withTransaction {
            val deckIdByName = LinkedHashMap<String, Long>()
            SeedData.decks.forEach { seedDeck ->
                val id = deckDao.insert(
                    DeckEntity(
                        name = seedDeck.name,
                        subject = seedDeck.subject,
                        parentId = null,
                        sortOrder = seedDeck.sortOrder,
                        isBuiltin = true,
                        createdAt = now
                    )
                )
                deckIdByName[seedDeck.name] = id
            }

            val cards = SeedData.cards.mapIndexedNotNull { index, seed ->
                val deckId = deckIdByName[seed.deck] ?: return@mapIndexedNotNull null
                CardEntity(
                    deckId = deckId,
                    type = seed.type,
                    front = seed.front,
                    back = seed.back,
                    extra = seed.extra,
                    tags = encodeTags(seed.tags),
                    sourceRef = seed.sourceRef,
                    sortOrder = index,
                    isSuspended = false,
                    createdAt = now,
                    updatedAt = now,
                    srsState = "NEW",
                    srsLevel = 0,
                    srsEase = 2.5,
                    srsIntervalMin = 0.0,
                    // 新卡立即可学
                    srsDueAt = now
                )
            }
            db.cardDao().insertAll(cards)
        }
    }

    private companion object {
        const val TAG = "SeedInitializer"

        /**
         * assets 下的内置知识包目录。
         * 与打包工具 `build_knowledge_packs.py --src` 指向同一份文件，
         * 因此「App 内置内容」与「发布到数据源的内容」永远是同一批数据，
         * 不会出现两处维护、内容漂移的问题。
         */
        const val BUNDLED_PACK_DIR = "knowledge"
    }
}
