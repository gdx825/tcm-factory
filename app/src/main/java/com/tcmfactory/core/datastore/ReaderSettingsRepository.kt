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

/** 古籍阅读器的排版设置。与背诵模块的设置分开存储，互不干扰。 */
data class ReaderSettings(
    /** 字号 sp，12–32 */
    val fontSize: Int = 19,
    /** 行距倍数，1.2–2.2 */
    val lineHeight: Float = 1.75f,
    /** 段间距倍数，0–1.5 */
    val paragraphSpacing: Float = 0.6f,
    /** 页边距档位：0 窄 / 1 标准 / 2 宽 */
    val margin: Int = 1,
    /** LXGW 内置霞鹜文楷（默认）/ SERIF 系统宋体 / SANS 系统黑体 */
    val fontFamily: String = FONT_LXGW,
    /** 阅读主题 */
    val theme: String = THEME_PAPER,
    /** 首行缩进 2 字符 */
    val indent: Boolean = true,
    val keepScreenOn: Boolean = true
) {
    companion object {
        /** 内置霞鹜文楷（子集，3.9MB），古籍阅读默认字体 */
        const val FONT_LXGW = "LXGW"
        const val FONT_SERIF = "SERIF"
        const val FONT_SANS = "SANS"

        const val THEME_PAPER = "PAPER"       // 米白纸感
        const val THEME_DARK = "DARK"         // 墨灰
        const val THEME_GREEN = "GREEN"       // 豆沙绿
        const val THEME_PARCHMENT = "PARCHMENT" // 羊皮纸
    }

    /** 页边距对应到实际 dp。 */
    val horizontalPaddingDp: Int
        get() = when (margin) {
            0 -> 12
            2 -> 32
            else -> 20
        }
}

private val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(name = "reader_settings")

@Singleton
class ReaderSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val FONT_SIZE = intPreferencesKey("reader_font_size")
        val LINE_HEIGHT = floatPreferencesKey("reader_line_height")
        val PARA_SPACING = floatPreferencesKey("reader_para_spacing")
        val MARGIN = intPreferencesKey("reader_margin")
        val FONT_FAMILY = stringPreferencesKey("reader_font_family")
        val THEME = stringPreferencesKey("reader_theme")
        val INDENT = booleanPreferencesKey("reader_indent")
        val KEEP_SCREEN_ON = booleanPreferencesKey("reader_keep_screen_on")
    }

    val settings: Flow<ReaderSettings> = context.readerDataStore.data.map { it.toSettings() }

    suspend fun snapshot(): ReaderSettings = context.readerDataStore.data.first().toSettings()

    suspend fun setFontSize(value: Int) = put { it[Keys.FONT_SIZE] = value.coerceIn(12, 32) }
    suspend fun setLineHeight(value: Float) = put { it[Keys.LINE_HEIGHT] = value.coerceIn(1.2f, 2.2f) }
    suspend fun setParagraphSpacing(value: Float) = put { it[Keys.PARA_SPACING] = value.coerceIn(0f, 1.5f) }
    suspend fun setMargin(value: Int) = put { it[Keys.MARGIN] = value.coerceIn(0, 2) }
    suspend fun setFontFamily(value: String) = put { it[Keys.FONT_FAMILY] = value }
    suspend fun setTheme(value: String) = put { it[Keys.THEME] = value }
    suspend fun setIndent(value: Boolean) = put { it[Keys.INDENT] = value }
    suspend fun setKeepScreenOn(value: Boolean) = put { it[Keys.KEEP_SCREEN_ON] = value }

    private suspend fun put(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.readerDataStore.edit(block)
    }

    private fun Preferences.toSettings(): ReaderSettings = ReaderSettings(
        fontSize = this[Keys.FONT_SIZE] ?: 19,
        lineHeight = this[Keys.LINE_HEIGHT] ?: 1.75f,
        paragraphSpacing = this[Keys.PARA_SPACING] ?: 0.6f,
        margin = this[Keys.MARGIN] ?: 1,
        fontFamily = this[Keys.FONT_FAMILY] ?: ReaderSettings.FONT_LXGW,
        theme = this[Keys.THEME] ?: ReaderSettings.THEME_PAPER,
        indent = this[Keys.INDENT] ?: true,
        keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: true
    )
}
