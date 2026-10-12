package com.tcmfactory.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tcmfactory.core.database.dao.BookDao
import com.tcmfactory.core.database.dao.CardDao
import com.tcmfactory.core.database.dao.DeckDao
import com.tcmfactory.core.database.dao.KnowledgeDao
import com.tcmfactory.core.database.dao.QuizDao
import com.tcmfactory.core.database.dao.ReviewLogDao
import com.tcmfactory.core.database.dao.StatsDao
import com.tcmfactory.core.database.entity.BookChapterEntity
import com.tcmfactory.core.database.entity.BookEntity
import com.tcmfactory.core.database.entity.BookMarkEntity
import com.tcmfactory.core.database.entity.CardEntity
import com.tcmfactory.core.database.entity.DeckEntity
import com.tcmfactory.core.database.entity.KnowledgePackVersionEntity
import com.tcmfactory.core.database.entity.QuestionEntity
import com.tcmfactory.core.database.entity.QuizRecordEntity
import com.tcmfactory.core.database.entity.QuizSessionEntity
import com.tcmfactory.core.database.entity.ReviewLogEntity
import com.tcmfactory.core.database.entity.StudyDailyStatsEntity
import com.tcmfactory.core.database.entity.WrongQuestionEntity

/**
 * 中医工厂本地数据库。
 *
 * 12 张表一次设计到位（含第二阶段古籍、第三阶段刷题所需表），
 * 目的是避免后续反复写 Migration —— 那是最容易出错、最难测试的代码。
 */
@Database(
    entities = [
        // 背诵模块（第一阶段启用）
        DeckEntity::class,
        CardEntity::class,
        ReviewLogEntity::class,
        StudyDailyStatsEntity::class,
        KnowledgePackVersionEntity::class,
        // 古籍模块（第二阶段启用，第一阶段已建表）
        BookEntity::class,
        BookChapterEntity::class,
        BookMarkEntity::class,
        // 刷题模块（第三阶段启用，第一阶段已建表）
        QuestionEntity::class,
        QuizSessionEntity::class,
        QuizRecordEntity::class,
        WrongQuestionEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class TcmDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun statsDao(): StatsDao
    abstract fun bookDao(): BookDao
    abstract fun quizDao(): QuizDao
    abstract fun knowledgeDao(): KnowledgeDao

    companion object {
        const val DB_NAME = "tcm_factory.db"

        /**
         * v1 → v2：知识库联网更新所需的溯源列。
         *
         * **为什么必须写迁移而不是沿用 fallbackToDestructiveMigration：**
         * v1.2.0 已经交付并安装到真实设备上，用户此刻正在积累背诵进度
         * （SRS 等级、到期时间、遗忘次数）。破坏性重建会把几个月的学习数据
         * 一次性清空，这是不可逆的损失。所以从 v2 起改为显式迁移。
         *
         * 所有新增列一律**可空且不带 DEFAULT**，与 Entity 中声明的可空字段严格对应。
         * Room 启动时会逐列比对可空性与默认值，只要有一处对不上就会直接抛异常。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ---- cards：知识库溯源列 ----
                db.execSQL("ALTER TABLE cards ADD COLUMN packId TEXT")
                db.execSQL("ALTER TABLE cards ADD COLUMN externalId TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_cards_pack_external " +
                        "ON cards(packId, externalId)"
                )

                // ---- questions：除 packId（v1 已有）外补 externalId ----
                db.execSQL("ALTER TABLE questions ADD COLUMN externalId TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_questions_pack_external " +
                        "ON questions(packId, externalId)"
                )

                // ---- knowledge_pack_versions：补齐更新元数据 ----
                db.execSQL("ALTER TABLE knowledge_pack_versions ADD COLUMN kind TEXT")
                db.execSQL("ALTER TABLE knowledge_pack_versions ADD COLUMN source TEXT")
                db.execSQL("ALTER TABLE knowledge_pack_versions ADD COLUMN sourceUrl TEXT")
                db.execSQL("ALTER TABLE knowledge_pack_versions ADD COLUMN sha256 TEXT")
                db.execSQL("ALTER TABLE knowledge_pack_versions ADD COLUMN itemCount INTEGER")
                db.execSQL("ALTER TABLE knowledge_pack_versions ADD COLUMN updatedAt INTEGER")
                db.execSQL("ALTER TABLE knowledge_pack_versions ADD COLUMN description TEXT")
                db.execSQL("ALTER TABLE knowledge_pack_versions ADD COLUMN license TEXT")
                db.execSQL("ALTER TABLE knowledge_pack_versions ADD COLUMN subject TEXT")
            }
        }

        fun build(context: Context, name: String = DB_NAME): TcmDatabase =
            Room.databaseBuilder(context.applicationContext, TcmDatabase::class.java, name)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // 级联删除依赖外键约束，而该开关默认关闭，必须显式打开。
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                })
                .build()
    }
}
