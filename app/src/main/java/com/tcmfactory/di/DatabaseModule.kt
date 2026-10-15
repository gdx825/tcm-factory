package com.tcmfactory.di

import android.content.Context
import androidx.room.Room
import com.tcmfactory.core.database.TcmDatabase
import com.tcmfactory.core.database.dao.BookDao
import com.tcmfactory.core.database.dao.CardDao
import com.tcmfactory.core.database.dao.DeckDao
import com.tcmfactory.core.database.dao.KnowledgeDao
import com.tcmfactory.core.database.dao.QuizDao
import com.tcmfactory.core.database.dao.SupporterDao
import com.tcmfactory.core.database.dao.ReviewLogDao
import com.tcmfactory.core.database.dao.StatsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TcmDatabase =
        TcmDatabase.build(context)

    @Provides
    fun provideDeckDao(db: TcmDatabase): DeckDao = db.deckDao()

    @Provides
    fun provideCardDao(db: TcmDatabase): CardDao = db.cardDao()

    @Provides
    fun provideReviewLogDao(db: TcmDatabase): ReviewLogDao = db.reviewLogDao()

    @Provides
    fun provideStatsDao(db: TcmDatabase): StatsDao = db.statsDao()

    @Provides
    fun provideBookDao(db: TcmDatabase): BookDao = db.bookDao()

    @Provides
    fun provideQuizDao(db: TcmDatabase): QuizDao = db.quizDao()

    @Provides
    fun provideKnowledgeDao(db: TcmDatabase): KnowledgeDao = db.knowledgeDao()

    @Provides
    fun provideSupporterDao(db: TcmDatabase): SupporterDao = db.supporterDao()
}
