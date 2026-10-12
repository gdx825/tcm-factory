package com.tcmfactory.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 用户可配置项。默认值即产品默认值。 */
data class UserSettings(
    /** 每日新卡上限。0 表示不限制。 */
    val newPerDay: Int = 20,
    /** 每日复习上限（不含学习期卡片，后者必须当日清空）。 */
    val reviewPerDay: Int = 100,
    /** 学习日边界小时。默认 04:00。 */
    val dayBoundaryHour: Int = 4,
    /** 每日提醒小时；-1 表示不提醒。 */
    val reminderHour: Int = 20,
    val reminderEnabled: Boolean = true,
    /** 是否开启四档精评（默认只有二元滑动）。 */
    val enableFourRatings: Boolean = false,
    /** 评 AGAIN 时回退档数。 */
    val lapseStep: Int = 4,
    /** SYSTEM / LIGHT / DARK */
    val themeMode: String = THEME_SYSTEM,
    /** 选中参与背诵的牌组；空集合表示「全部牌组」。 */
    val selectedDeckIds: Set<Long> = emptySet(),
    /** 滑动飞出阈值（屏幕宽度占比）。 */
    val swipeThreshold: Float = 0.22f,
    /** 卡片默认是否自动播放答案 */
    val autoReveal: Boolean = false,
    val fontScale: Float = 1.0f
) {
    companion object {
        const val THEME_SYSTEM = "SYSTEM"
        const val THEME_LIGHT = "LIGHT"
        const val THEME_DARK = "DARK"
        const val THEME_EYE_CARE = "EYE_CARE"
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tcm_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val NEW_PER_DAY = intPreferencesKey("new_per_day")
        val REVIEW_PER_DAY = intPreferencesKey("review_per_day")
        val DAY_BOUNDARY_HOUR = intPreferencesKey("day_boundary_hour")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val ENABLE_FOUR_RATINGS = booleanPreferencesKey("enable_four_ratings")
        val LAPSE_STEP = intPreferencesKey("lapse_step")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SELECTED_DECKS = stringPreferencesKey("selected_decks")
        val SWIPE_THRESHOLD = floatPreferencesKey("swipe_threshold")
        val AUTO_REVEAL = booleanPreferencesKey("auto_reveal")
        val FONT_SCALE = floatPreferencesKey("font_scale")
    }

    val settings: Flow<UserSettings> = context.settingsDataStore.data.map { it.toSettings() }

    suspend fun setNewPerDay(value: Int) = put { it[Keys.NEW_PER_DAY] = value.coerceIn(0, 500) }

    suspend fun setReviewPerDay(value: Int) = put { it[Keys.REVIEW_PER_DAY] = value.coerceIn(0, 1000) }

    suspend fun setDayBoundaryHour(value: Int) = put { it[Keys.DAY_BOUNDARY_HOUR] = value.coerceIn(0, 6) }

    suspend fun setReminderHour(value: Int) = put { it[Keys.REMINDER_HOUR] = value.coerceIn(-1, 23) }

    suspend fun setReminderEnabled(value: Boolean) = put { it[Keys.REMINDER_ENABLED] = value }

    suspend fun setEnableFourRatings(value: Boolean) = put { it[Keys.ENABLE_FOUR_RATINGS] = value }

    suspend fun setLapseStep(value: Int) = put { it[Keys.LAPSE_STEP] = value.coerceIn(1, 8) }

    suspend fun setThemeMode(value: String) = put { it[Keys.THEME_MODE] = value }

    suspend fun setSwipeThreshold(value: Float) = put { it[Keys.SWIPE_THRESHOLD] = value.coerceIn(0.1f, 0.5f) }

    suspend fun setAutoReveal(value: Boolean) = put { it[Keys.AUTO_REVEAL] = value }

    suspend fun setFontScale(value: Float) = put { it[Keys.FONT_SCALE] = value.coerceIn(0.8f, 1.4f) }

    suspend fun setSelectedDecks(ids: Set<Long>) = put {
        it[Keys.SELECTED_DECKS] = ids.joinToString(",")
    }

    suspend fun toggleDeckSelected(deckId: Long) {
        val current = settingsSnapshot().selectedDeckIds
        setSelectedDecks(if (deckId in current) current - deckId else current + deckId)
    }

    /** 一次性读取快照，供非响应式场景（如 UseCase 组装队列）使用。 */
    suspend fun settingsSnapshot(): UserSettings = context.settingsDataStore.data.first().toSettings()

    private suspend fun put(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }

    private fun Preferences.toSettings(): UserSettings = UserSettings(
        newPerDay = this[Keys.NEW_PER_DAY] ?: 20,
        reviewPerDay = this[Keys.REVIEW_PER_DAY] ?: 100,
        dayBoundaryHour = this[Keys.DAY_BOUNDARY_HOUR] ?: 4,
        reminderHour = this[Keys.REMINDER_HOUR] ?: 20,
        reminderEnabled = this[Keys.REMINDER_ENABLED] ?: true,
        enableFourRatings = this[Keys.ENABLE_FOUR_RATINGS] ?: false,
        lapseStep = this[Keys.LAPSE_STEP] ?: 4,
        themeMode = this[Keys.THEME_MODE] ?: UserSettings.THEME_SYSTEM,
        selectedDeckIds = this[Keys.SELECTED_DECKS]
            ?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.toSet()
            ?: emptySet(),
        swipeThreshold = this[Keys.SWIPE_THRESHOLD] ?: 0.22f,
        autoReveal = this[Keys.AUTO_REVEAL] ?: false,
        fontScale = this[Keys.FONT_SCALE] ?: 1.0f
    )
}
