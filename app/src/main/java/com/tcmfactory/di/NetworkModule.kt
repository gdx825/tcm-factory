package com.tcmfactory.di

import com.tcmfactory.data.network.HttpFetcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * 知识库下载专用客户端。
     *
     * 超时取值偏保守：弱网或跨国访问 CDN 时，连接慢是常态，
     * 但也不能无限等 —— 用 callTimeout 兜底，避免用户点了更新后
     * 界面永远停在「下载中」。
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    /** 纯抓取层，无 Android 依赖，可由 JVM 单测直接驱动 MockWebServer 验证。 */
    @Provides
    @Singleton
    fun provideHttpFetcher(client: OkHttpClient): HttpFetcher = HttpFetcher(client)
}
