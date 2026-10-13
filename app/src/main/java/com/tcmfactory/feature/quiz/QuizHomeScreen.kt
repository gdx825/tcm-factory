package com.tcmfactory.feature.quiz

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tcmfactory.core.designsystem.TagPill
import com.tcmfactory.data.repository.ImportSummary
import com.tcmfactory.data.repository.QuizRepository
import com.tcmfactory.domain.model.Question
import com.tcmfactory.domain.model.QuizMode
import com.tcmfactory.domain.model.SubjectQuestionCount
import com.tcmfactory.domain.model.subjectLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class QuizHomeUiState(
    val subjectCounts: List<SubjectQuestionCount> = emptyList(),
    val wrongCount: Int = 0,
    val totalQuestions: Int = 0,
    val selectedSubject: String? = null,
    val chapters: List<Pair<String, Int>> = emptyList(),
    val wrongQuestions: List<Question> = emptyList(),
    val tab: Int = 0,
    val loading: Boolean = true,
    val lastImport: ImportSummary? = null,
    val importError: String? = null
) {
    val hasQuestions: Boolean get() = totalQuestions > 0
}

@HiltViewModel
class QuizHomeViewModel @Inject constructor(
    private val quizRepository: QuizRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private companion object {
        const val MAX_PACK_SIZE = 20L * 1024 * 1024
    }

    private val _uiState = MutableStateFlow(QuizHomeUiState())
    val uiState: StateFlow<QuizHomeUiState> = _uiState.asStateFlow()

    init {
        // 内置题库已统一由知识包在 App 启动时分发（见 SeedInitializer），
        // 这里不再需要「题库为空就写入示例题」的兜底逻辑。
        refresh()
        viewModelScope.launch {
            quizRepository.observeWrongCount().collect { count ->
                _uiState.update { it.copy(wrongCount = count) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            // 题库变化频率极低，取一次快照即可，不需要长期订阅
            val counts = quizRepository.observeSubjectCounts().first()
            _uiState.update {
                it.copy(
                    subjectCounts = counts,
                    totalQuestions = counts.sumOf { c -> c.count },
                    loading = false
                )
            }
        }
    }

    fun selectSubject(subject: String?) {
        _uiState.update { it.copy(selectedSubject = subject, chapters = emptyList()) }
        if (subject == null) return
        viewModelScope.launch {
            val chapters = quizRepository.chapterCounts(subject)
            _uiState.update { it.copy(chapters = chapters) }
        }
    }

    fun setTab(index: Int) {
        _uiState.update { it.copy(tab = index) }
        if (index == 1) loadWrongQuestions()
    }

    fun loadWrongQuestions() {
        viewModelScope.launch {
            val list = quizRepository.getWrongQuestions()
            _uiState.update { it.copy(wrongQuestions = list) }
        }
    }

    fun removeWrong(questionId: Long) {
        viewModelScope.launch {
            quizRepository.removeFromWrong(questionId)
            loadWrongQuestions()
        }
    }

    fun dismissImportResult() {
        _uiState.update { it.copy(lastImport = null, importError = null) }
    }

    fun importPack(uri: Uri) {
        viewModelScope.launch {
            val name = queryDisplayName(uri)
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes()
                    }
                }.getOrNull()
            }

            if (raw == null || raw.isEmpty()) {
                _uiState.update { it.copy(importError = "无法读取文件：$name") }
                return@launch
            }
            if (raw.size > MAX_PACK_SIZE) {
                _uiState.update { it.copy(importError = "题库文件超过 20MB") }
                return@launch
            }

            val text = String(raw, Charsets.UTF_8)
            val summary = runCatching { quizRepository.importPack(text) }.getOrNull()

            if (summary == null) {
                _uiState.update { it.copy(importError = "格式无法识别，请确认是标准题库 JSON") }
            } else {
                _uiState.update { it.copy(lastImport = summary) }
                refresh()
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull() ?: "题库文件"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizHomeScreen(
    onStartSession: (QuizMode, String?, String?) -> Unit,
    viewModel: QuizHomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importPack(uri)
    }

    Column(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 28.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "刷题",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (state.hasQuestions) {
                        "题库共 ${state.totalQuestions} 题" +
                            if (state.wrongCount > 0) " · 错题 ${state.wrongCount}" else ""
                    } else {
                        "还没有题库"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { picker.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "导入题库",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            SegmentedButton(
                selected = state.tab == 0,
                onClick = { viewModel.setTab(0) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("练习") }
            SegmentedButton(
                selected = state.tab == 1,
                onClick = { viewModel.setTab(1) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text(if (state.wrongCount > 0) "错题本 (${state.wrongCount})" else "错题本") }
        }

        if (state.tab == 0) {
            PracticeTab(
                state = state,
                onStartSession = onStartSession,
                onSelectSubject = viewModel::selectSubject,
                onImportClick = { picker.launch(arrayOf("application/json", "text/plain", "*/*")) }
            )
        } else {
            WrongBookTab(
                state = state,
                onStartWrongSession = { onStartSession(QuizMode.WRONG, null, null) },
                onRemove = viewModel::removeWrong
            )
        }
    }

    state.lastImport?.let { summary ->
        ImportResultDialog(summary = summary, onDismiss = viewModel::dismissImportResult)
    }
    state.importError?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissImportResult,
            title = { Text("导入失败") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissImportResult) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun PracticeTab(
    state: QuizHomeUiState,
    onStartSession: (QuizMode, String?, String?) -> Unit,
    onSelectSubject: (String?) -> Unit,
    onImportClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        if (!state.hasQuestions) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "导入你的题库",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "支持标准题库 JSON：题干、选项、答案、解析、\n章节分类。答案可写字母或数字，判断题可省略选项。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onImportClick, shape = RoundedCornerShape(14.dp)) {
                        Text("选择 JSON 题库文件")
                    }
                }
            }
            return@LazyColumn
        }

        item {
            Spacer(Modifier.height(6.dp))
            ModeCard(
                title = "随机练习",
                subtitle = "从全部题库中随机抽题，适合快速检验",
                onClick = { onStartSession(QuizMode.RANDOM, null, null) }
            )
        }

        item {
            ModeCard(
                title = "中内／经典专项",
                subtitle = "按科目顺序练习，从第一题开始",
                enabled = state.selectedSubject != null,
                disabledHint = "请先在下方选择科目",
                onClick = { onStartSession(QuizMode.SEQUENTIAL, state.selectedSubject, null) }
            )
        }

        item {
            Text(
                text = "按科目练习",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
            )
        }

        items(state.subjectCounts, key = { it.subject }) { item ->
            val selected = state.selectedSubject == item.subject
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                onClick = { onSelectSubject(if (selected) null else item.subject) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = subjectLabel(item.subject),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${item.count} 题",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.chapters.isNotEmpty()) {
            item {
                Text(
                    text = "按章节练习",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp)
                )
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    state.chapters.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { (chapter, count) ->
                                OutlinedButton(
                                    onClick = {
                                        onStartSession(QuizMode.CHAPTER, state.selectedSubject, chapter)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "$chapter ($count)",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    disabledHint: String? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = { if (enabled) onClick() }
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (enabled) subtitle else (disabledHint ?: subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (enabled) {
                Button(onClick = onClick, shape = RoundedCornerShape(12.dp)) { Text("开始") }
            } else {
                TagPill(
                    text = "未选科目",
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WrongBookTab(
    state: QuizHomeUiState,
    onStartWrongSession: () -> Unit,
    onRemove: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        if (state.wrongQuestions.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "错题本是空的",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "答错的题目会自动归集到这里。\n连续答对 2 次后自动移出，也可以手动移除。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            return@LazyColumn
        }

        item {
            Button(
                onClick = onStartWrongSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("开始错题重做（${state.wrongQuestions.size} 题）") }
        }

        items(state.wrongQuestions, key = { it.id }) { question ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TagPill(
                            text = question.type.label,
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (question.chapter != null) {
                            Spacer(Modifier.width(6.dp))
                            TagPill(
                                text = question.chapter,
                                container = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "错 ${question.wrongCount} 次",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = question.stem,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "正确答案：${question.correctText}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onRemove(question.id) }) {
                            Text("移除", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportResultDialog(summary: ImportSummary, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入完成") },
        text = {
            Column {
                Text("题库：${summary.packName}")
                Spacer(Modifier.height(6.dp))
                Text("成功导入 ${summary.imported} 题")
                if (summary.rejectedCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "驳回 ${summary.rejectedCount} 题：",
                        color = MaterialTheme.colorScheme.error
                    )
                    summary.rejected.take(5).forEach { r ->
                        Text(
                            text = "· 第 ${r.index + 1} 题：${r.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (summary.rejectedCount > 5) {
                        Text(
                            text = "… 其余 ${summary.rejectedCount - 5} 题同理",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("好") } }
    )
}
