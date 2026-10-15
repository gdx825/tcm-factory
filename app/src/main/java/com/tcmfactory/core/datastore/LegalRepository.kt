package com.tcmfactory.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tcmfactory.core.legal.LegalTexts
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.legalDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "legal_state")

/**
 * 用户对免责条款的接受状态。
 *
 * ## 为什么要记录「版本」而不只是一个布尔值
 *
 * 只记「已同意」的话，条款将来修订后，老用户会被**默认视为已同意新版**，这在争议场景下站不住脚。
 * 记录版本号之后，一旦 [LegalTexts.VERSION] 递增，老用户的同意状态自动失效，
 * 应用会重新弹出确认 —— 这是"知情同意"应有的样子，而不是走个形式。
 */
@Singleton
class LegalRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val ACCEPTED_VERSION = intPreferencesKey("accepted_version")
        val ACCEPTED_AT = intPreferencesKey("accepted_at_days")
    }

    /** 已同意的条款版本；从未同意过为 0。 */
    val acceptedVersion: Flow<Int> = context.legalDataStore.data
        .map { it[Keys.ACCEPTED_VERSION] ?: 0 }

    /** 是否已同意**当前**版本的条款。 */
    val isCurrentVersionAccepted: Flow<Boolean> =
        acceptedVersion.map { it >= LegalTexts.VERSION }

    suspend fun accept(version: Int = LegalTexts.VERSION) {
        context.legalDataStore.edit { prefs ->
            prefs[Keys.ACCEPTED_VERSION] = version
        }
    }

    /** 供非响应式场景一次性读取。 */
    suspend fun isAcceptedNow(): Boolean = isCurrentVersionAccepted.first()
}
