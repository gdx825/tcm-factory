package com.tcmfactory.feature.reader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcmfactory.core.reader.CleanOptions
import com.tcmfactory.data.repository.BookRepository
import com.tcmfactory.data.repository.ImportPreview
import com.tcmfactory.domain.model.Book
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 导入流程状态。原始字节留在 ViewModel 私有字段，不进 UiState（避免大对象频繁比较）。 */
data class ImportUiState(
    val fileName: String = "",
    val preview: ImportPreview? = null,
    val options: CleanOptions = CleanOptions(),
    val forcedEncoding: String? = null,
    val analyzing: Boolean = false,
    val committing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** 单文件上限 20MB —— 古籍 TXT 极少超过，超出通常是误选了 PDF 等二进制文件。 */
    private companion object {
        const val MAX_FILE_SIZE = 20L * 1024 * 1024
    }

    val books: StateFlow<List<Book>> = bookRepository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importState = MutableStateFlow<ImportUiState?>(null)
    val importState: StateFlow<ImportUiState?> = _importState.asStateFlow()

    private var pendingBytes: ByteArray? = null

    // ---------------- 导入流程 ----------------

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            val fileName = queryDisplayName(uri)
            _importState.value = ImportUiState(fileName = fileName, analyzing = true)

            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }

            if (bytes == null || bytes.isEmpty()) {
                _importState.update { it?.copy(analyzing = false, error = "无法读取文件内容") }
                return@launch
            }
            if (bytes.size > MAX_FILE_SIZE) {
                _importState.update {
                    it?.copy(analyzing = false, error = "文件超过 20MB，暂不支持导入")
                }
                return@launch
            }

            pendingBytes = bytes
            analyze()
        }
    }

    fun updateOptions(options: CleanOptions) {
        _importState.update { it?.copy(options = options) }
        analyze()
    }

    fun setEncoding(name: String?) {
        _importState.update { it?.copy(forcedEncoding = name) }
        analyze()
    }

    private fun analyze() {
        val bytes = pendingBytes ?: return
        val state = _importState.value ?: return
        viewModelScope.launch {
            _importState.update { it?.copy(analyzing = true, error = null) }
            val preview = runCatching {
                bookRepository.analyze(
                    bytes = bytes,
                    fileName = state.fileName,
                    options = state.options,
                    forcedEncoding = state.forcedEncoding
                )
            }.getOrNull()

            _importState.update {
                it?.copy(
                    preview = preview,
                    analyzing = false,
                    error = if (preview == null) "解析失败，请检查文件是否为文本格式" else null
                )
            }
        }
    }

    fun confirmImport(title: String, author: String?, dynasty: String?, onDone: (Long) -> Unit) {
        val bytes = pendingBytes ?: return
        val state = _importState.value ?: return
        viewModelScope.launch {
            _importState.update { it?.copy(committing = true) }
            val bookId = runCatching {
                bookRepository.commitImport(
                    bytes = bytes,
                    fileName = state.fileName,
                    encoding = state.preview?.encoding ?: "UTF-8",
                    options = state.options,
                    title = title,
                    author = author,
                    dynasty = dynasty
                )
            }.getOrNull()

            pendingBytes = null
            _importState.value = null

            if (bookId != null && bookId > 0) onDone(bookId)
        }
    }

    fun cancelImport() {
        pendingBytes = null
        _importState.value = null
    }

    // ---------------- 书架操作 ----------------

    fun deleteBook(bookId: Long) {
        viewModelScope.launch { bookRepository.deleteBook(bookId) }
    }

    fun renameBook(bookId: Long, title: String, author: String?, dynasty: String?) {
        viewModelScope.launch { bookRepository.renameBook(bookId, title, author, dynasty) }
    }

    private fun queryDisplayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull() ?: "导入的古籍.txt"
}
