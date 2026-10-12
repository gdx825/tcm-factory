package com.tcmfactory.feature.recite

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcmfactory.data.repository.CardRepository
import com.tcmfactory.data.repository.KnowledgeRepository
import com.tcmfactory.domain.model.Card
import com.tcmfactory.domain.model.Deck
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CardManageUiState(
    val cards: List<Card> = emptyList(),
    val decks: List<Deck> = emptyList(),
    val selectedDeckId: Long? = null,
    val selectedState: String? = null,
    val keyword: String = "",
    val loading: Boolean = true
)

@HiltViewModel
class CardManageViewModel @Inject constructor(
    private val repository: CardRepository,
    private val knowledgeRepository: KnowledgeRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(CardManageUiState())
    val state: StateFlow<CardManageUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val decks = repository.getAllDecks()
            _state.update { it.copy(decks = decks) }
            refresh()
        }
    }

    fun setKeyword(value: String) {
        _state.update { it.copy(keyword = value) }
        refresh()
    }

    fun toggleDeckFilter(deckId: Long?) {
        _state.update { it.copy(selectedDeckId = if (it.selectedDeckId == deckId) null else deckId) }
        refresh()
    }

    fun toggleStateFilter(state: String?) {
        _state.update { it.copy(selectedState = if (it.selectedState == state) null else state) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val s = _state.value
            val cards = repository.getCards(
                deckId = s.selectedDeckId,
                state = s.selectedState,
                keyword = s.keyword,
                limit = 300,
                offset = 0
            )
            _state.update { it.copy(cards = cards, loading = false) }
        }
    }

    // ---------------- 导入 / 导出 ----------------

    /**
     * 把当前选中的牌组导出为知识包 JSON，写入用户通过系统文件选择器指定的位置。
     *
     * 导出必须落在用户自己选的位置（SAF），而不是应用私有目录 ——
     * 后者用户根本找不到，等于没导出。
     */
    fun exportDeckTo(uri: Uri) {
        val deckId = _state.value.selectedDeckId
        if (deckId == null) {
            viewModelScope.launch { toast("请先选择一个牌组，再执行导出") }
            return
        }
        viewModelScope.launch {
            runCatching {
                val json = repository.exportDeckAsPackJson(deckId)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入所选位置")
                }
                // 条数以导出后的 JSON 为准，避免与实际写出内容不一致
                Regex("\"front\"").findAll(json).count()
            }.fold(
                onSuccess = { count -> toast("已导出 $count 张卡片") },
                onFailure = { toast("导出失败：${it.message ?: "未知错误"}") }
            )
        }
    }

    /**
     * 从本地文件导入知识包。
     *
     * 复用知识库的 installFromBytes —— 与联网更新、内置包走完全同一条通道，
     * 因此同样具备 SHA 无关的幂等 upsert（同 (packId, externalId) 视为同一条，
     * 更新而不重复），不会因为反复导入把卡片翻倍。
     */
    fun importPackFrom(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("无法读取所选文件")
                }
                val name = displayNameOf(uri) ?: "导入的知识包"
                knowledgeRepository.installFromBytes(bytes, name)
            }.fold(
                onSuccess = { report ->
                    if (report.success) {
                        toast("导入完成：新增 ${report.added} 条，更新 ${report.updated} 条")
                        refresh()
                    } else {
                        toast("导入失败：${report.errorMessage.orEmpty()}")
                    }
                },
                onFailure = { toast("导入失败：${it.message ?: "未知错误"}") }
            )
        }
    }

    private suspend fun toast(message: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun displayNameOf(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()

    fun toggleSuspend(card: Card) {
        viewModelScope.launch {
            repository.setSuspended(card.id, !card.isSuspended)
            refresh()
        }
    }

    fun resetProgress(card: Card) {
        viewModelScope.launch {
            repository.resetProgress(card.id)
            refresh()
        }
    }

    fun delete(card: Card) {
        viewModelScope.launch {
            repository.deleteCard(card.id)
            refresh()
        }
    }

    fun addCard(deckId: Long, front: String, back: String, extra: String?, sourceRef: String?) {
        viewModelScope.launch {
            repository.addCard(
                deckId = deckId,
                front = front,
                back = back,
                extra = extra?.takeIf { it.isNotBlank() },
                sourceRef = sourceRef?.takeIf { it.isNotBlank() }
            )
            refresh()
        }
    }
}
