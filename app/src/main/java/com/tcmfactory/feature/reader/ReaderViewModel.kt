package com.tcmfactory.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcmfactory.core.datastore.ReaderSettings
import com.tcmfactory.core.datastore.ReaderSettingsRepository
import com.tcmfactory.data.repository.BookRepository
import com.tcmfactory.data.repository.CardRepository
import com.tcmfactory.domain.model.Book
import com.tcmfactory.domain.model.BookChapter
import com.tcmfactory.domain.model.BookMark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val book: Book? = null,
    val chapters: List<BookChapter> = emptyList(),
    val currentChapterIndex: Int = 0,
    /** 首帧需要滚动到的章节（卡片「查看原文」反向定位用） */
    val initialScrollIndex: Int = 0,
    val loading: Boolean = true,
    val error: String? = null
) {
    val chapterCount: Int get() = chapters.size
    val currentChapter: BookChapter? get() = chapters.getOrNull(currentChapterIndex)
    val progressText: String
        get() = if (chapterCount == 0) "0%" else "${((currentChapterIndex + 1) * 100 / chapterCount)}%"
}

/** 制卡请求（由阅读页长按段落触发，交给制卡面板处理）。 */
data class CardDraft(
    val front: String,
    val back: String = "",
    val sourceBookId: Long,
    val sourceBookTitle: String,
    val sourceChapterTitle: String,
    val charStart: Int,
    val charEnd: Int,
    val clozeText: String? = null
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val cardRepository: CardRepository,
    private val readerSettingsRepository: ReaderSettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val bookId: Long = savedStateHandle.get<Long>("bookId") ?: 0L

    /** 从卡片「查看原文」跳转时传入的原文偏移；-1 表示普通打开。 */
    private val highlightOffset: Int = savedStateHandle.get<Int>("offset") ?: -1

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    val settings: StateFlow<ReaderSettings> = readerSettingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderSettings())

    val marks: StateFlow<List<BookMark>> = bookRepository.observeMarks(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 当前待处理的制卡草稿；非空时界面弹出制卡面板。 */
    private val _cardDraft = MutableStateFlow<CardDraft?>(null)
    val cardDraft: StateFlow<CardDraft?> = _cardDraft.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val book = bookRepository.getBook(bookId)
            if (book == null) {
                _uiState.update { it.copy(loading = false, error = "书籍不存在") }
                return@launch
            }
            val chapters = bookRepository.getChapters(bookId)
            val maxIndex = (chapters.size - 1).coerceAtLeast(0)

            // 反向跳转定位优先于「上次阅读位置」
            val jumpIndex = if (highlightOffset >= 0) {
                chapters.indexOfFirst {
                    highlightOffset in it.startCharOffset until it.endCharOffset
                }
            } else -1
            val startIndex = if (jumpIndex >= 0) {
                jumpIndex
            } else {
                book.lastReadChapterIndex.coerceIn(0, maxIndex)
            }

            _uiState.value = ReaderUiState(
                book = book,
                chapters = chapters,
                currentChapterIndex = startIndex,
                initialScrollIndex = startIndex,
                loading = false
            )
        }
    }

    // ---------------- 阅读进度 ----------------

    fun onChapterVisible(index: Int, charStart: Int) {
        val state = _uiState.value
        if (state.chapters.isEmpty()) return
        val current = state.chapters.getOrNull(index) ?: return
        if (index != state.currentChapterIndex) {
            _uiState.update { it.copy(currentChapterIndex = index) }
        }
        viewModelScope.launch {
            bookRepository.saveProgress(
                bookId = bookId,
                chapterIndex = index,
                charOffset = current.startCharOffset
            )
        }
    }

    // ---------------- 排版设置 ----------------

    fun setFontSize(value: Int) = viewModelScope.launch { readerSettingsRepository.setFontSize(value) }
    fun setLineHeight(value: Float) = viewModelScope.launch { readerSettingsRepository.setLineHeight(value) }
    fun setParagraphSpacing(value: Float) = viewModelScope.launch { readerSettingsRepository.setParagraphSpacing(value) }
    fun setMargin(value: Int) = viewModelScope.launch { readerSettingsRepository.setMargin(value) }
    fun setFontFamily(value: String) = viewModelScope.launch { readerSettingsRepository.setFontFamily(value) }
    fun setTheme(value: String) = viewModelScope.launch { readerSettingsRepository.setTheme(value) }
    fun setIndent(value: Boolean) = viewModelScope.launch { readerSettingsRepository.setIndent(value) }
    fun setKeepScreenOn(value: Boolean) = viewModelScope.launch { readerSettingsRepository.setKeepScreenOn(value) }

    // ---------------- 书签 ----------------

    fun addBookmark(chapterIndex: Int, charOffset: Int, selectedText: String?) {
        viewModelScope.launch {
            bookRepository.addMark(
                bookId = bookId,
                charOffset = charOffset,
                chapterIndex = chapterIndex,
                selectedText = selectedText
            )
            _toast.value = "已添加书签"
        }
    }

    fun removeBookmark(markId: Long) {
        viewModelScope.launch { bookRepository.removeMark(markId) }
    }

    // ---------------- 制卡 ----------------

    fun requestMakeCard(chapterIndex: Int, paragraph: String, charStart: Int, charEnd: Int) {
        val state = _uiState.value
        val book = state.book ?: return
        val chapterTitle = state.chapters.getOrNull(chapterIndex)?.title.orEmpty()
        _cardDraft.value = CardDraft(
            front = paragraph,
            sourceBookId = bookId,
            sourceBookTitle = book.title,
            sourceChapterTitle = chapterTitle,
            charStart = charStart,
            charEnd = charEnd,
            clozeText = buildCloze(paragraph)
        )
    }

    fun dismissCardDraft() {
        _cardDraft.value = null
    }

    fun saveCard(front: String, back: String, deckId: Long?) {
        val draft = _cardDraft.value ?: return
        viewModelScope.launch {
            val targetDeck = deckId ?: cardRepository.ensureDeck("古籍摘录", "CLASSICS")
            cardRepository.addCard(
                deckId = targetDeck,
                front = front,
                back = back.ifBlank { "（原文摘录）" },
                sourceRef = "${draft.sourceBookTitle} · ${draft.sourceChapterTitle}",
                type = "BASIC",
                sourceBookId = draft.sourceBookId,
                sourceCharStart = draft.charStart,
                sourceCharEnd = draft.charEnd
            )
            _cardDraft.value = null
            _toast.value = "已加入背诵队列"
        }
    }

    fun consumeToast() {
        _toast.value = null
    }

    fun chapterTitleOf(index: Int): String =
        _uiState.value.chapters.getOrNull(index)?.title.orEmpty()

    /** 供章节块按需读取正文 —— 只有章节进入可视区域时才会真正触发 IO。 */
    suspend fun loadSlice(start: Int, end: Int): String =
        bookRepository.loadSlice(bookId, start, end)

    /**
     * 自动生成挖空文本：把方剂名（以汤/散/丸/饮/煎/膏/丹 结尾的 2–4 字词）
     * 替换为 {{c1::…}}。用户零输入即可得到一张高质量挖空卡。
     */
    private fun buildCloze(text: String): String? = runCatching {
        var counter = 0
        FORMULA_PATTERN.replace(text) { match ->
            counter++
            "{{c$counter::${match.value}}}"
        }.takeIf { counter > 0 }
    }.getOrNull()

    private companion object {
        val FORMULA_PATTERN = Regex("""[\u4e00-\u9fa5]{2,5}(?:汤|散|丸|饮|煎|膏|丹)""")
    }
}
