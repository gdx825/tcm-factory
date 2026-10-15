package com.tcmfactory.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tcmfactory.core.license.LicenseCheckResult
import com.tcmfactory.core.license.LicenseVerifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.supporterDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "supporter_state")

/**
 * 支持者状态。
 *
 * ## 为什么这不叫「授权」或「激活」
 *
 * 本软件**完全免费，所有功能无限制**。打赏不换取任何东西，
 * 因此这里记录的不是"权限"，而是一种**凭证**：证明这个人曾经支持过这个项目。
 * 命名上刻意避开"授权/激活/解锁"这类词，是为了不在代码层面暗示存在付费墙 ——
 * 将来若有人误把这里当成权限门，读到 [isSupporter] 的注释就会明白它不该拦任何功能。
 */
data class SupporterStatus(
    val isSupporter: Boolean = false,
    /** 支持者码中的序号，可用于在名单里对应到具体的人。 */
    val serial: Long? = null,
    /** 本机记录的支持时刻。 */
    val since: Long = 0L
) {
    companion object {
        val NONE = SupporterStatus()
    }
}

@Singleton
class SupporterRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val SERIAL = longPreferencesKey("supporter_serial")
        val SINCE = longPreferencesKey("supporter_since")
        /** 保留原始码，便于日后核对"这个码是谁的"（例如怀疑泄露时）。 */
        val CODE = stringPreferencesKey("supporter_code")
    }

    val status: Flow<SupporterStatus> = context.supporterDataStore.data.map { prefs ->
        val serial = prefs[Keys.SERIAL]
        if (serial == null) {
            SupporterStatus.NONE
        } else {
            SupporterStatus(
                isSupporter = true,
                serial = serial,
                since = prefs[Keys.SINCE] ?: 0L
            )
        }
    }

    /**
     * 输入支持者码。
     *
     * 校验通过即记录；不通过则原样返回错误，由界面提示用户。
     * 注意：**校验失败不会清除已有状态** —— 支持过的人再次输错码，不应因此失去徽标。
     */
    suspend fun redeem(code: String): LicenseCheckResult {
        val result = LicenseVerifier.verify(code)
        if (result is LicenseCheckResult.Valid) {
            context.supporterDataStore.edit { prefs ->
                prefs[Keys.SERIAL] = result.payload.serial
                prefs[Keys.SINCE] = System.currentTimeMillis()
                prefs[Keys.CODE] = LicenseVerifier.normalize(code)
            }
        }
        return result
    }

    /** 清除支持者状态（仅用于用户主动撤回或测试）。 */
    suspend fun clear() {
        context.supporterDataStore.edit { it.clear() }
    }
}
