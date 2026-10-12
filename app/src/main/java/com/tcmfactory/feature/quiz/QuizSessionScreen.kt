package com.tcmfactory.feature.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tcmfactory.core.common.TimeFormat
import com.tcmfactory.core.designsystem.StatBlock
import com.tcmfactory.core.designsystem.TagPill
import com.tcmfactory.core.designsystem.ThinProgressBar
import com.tcmfactory.data.repository.AnswerRecord
import com.tcmfactory.data.repository.QuizRepository
import com.tcmfactory.domain.model.Question
import com.tcmfactory.domain.model.QuizMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuizSessionUiState(
    val mode: QuizMode = QuizMode.RANDOM,
    val questions: List<Question> = emptyList(),
    val index: Int = 0,
    val selected: Set<Int> = emptySet(),
    val submitted: Boolean = false,
    val lastCorrect: Boolean = false,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val elapsedMs: Long = 0L,
    val loading: Boolean = true,
    val finished: Boolean = false,
    val records: List<AnswerRecord> = emptyList(),
    val emptyHint: String? = null
) {
    val current: Question? get() = questions.getOrNull(index)
    val total: Int get() = questions.size
    val progress: Float get() = if (total == 0) 0f else index.toFloat() / total
    val answered: Int get() = correctCount + wrongCount
}

@HiltViewModel
class QuizSessionViewModel @Inject constructor(
    private val quizRepository: QuizRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val mode: QuizMode =
        runCatching { QuizMode.valueOf(savedStateHandle.get<String>("mode") ?: "RANDOM") }
            .getOrDefault(QuizMode.RANDOM)

    private val subject: String? =
        savedStateHandle.get<String>("subject")?.takeIf { it.isNotBlank() }

    private val chapter: String? =
        savedStateHandle.get<String>("chapter")?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(QuizSessionUiState(mode = mode))
    val uiState: StateFlow<QuizSessionUiState> = _uiState.asStateFlow()

    private var sessionId: Long = 0L
    private var questionShownAt: Long = System.currentTimeMillis()
    private var sessionStartedAt: Long = System.currentTimeMillis()

    init {
        start()
    }

    private fun start() {
        viewModelScope.launch {
            val questions = runCatching {
                quizRepository.buildSession(mode, subject, chapter)
            }.getOrDefault(emptyList())

            if (questions.isEmpty()) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        finished = false,
                        emptyHint = when (mode) {
                            QuizMode.WRONG -> "错题本是空的，先去练习吧"
                            else -> "题库里还没有符合条件的题目"
                        }
                    )
                }
                return@launch
            }

            sessionId = quizRepository.startSession(mode, subject, chapter, questions.size)
            val now = System.currentTimeMillis()
            sessionStartedAt = now
            questionShownAt = now

            _uiState.update {
                it.copy(
                    questions = questions,
                    index = 0,
                    loading = false,
                    finished = false
                )
            }
        }
    }

    fun toggleOption(optionIndex: Int) {
        val state = _uiState.value
        if (state.submitted) return
        val question = state.current ?: return

        if (question.isMulti) {
            val next = state.selected.toMutableSet()
            if (!next.add(optionIndex)) next.remove(optionIndex)
            _uiState.update { it.copy(selected = next) }
        } else {
            // 单选：点击即判定
            _uiState.update { it.copy(selected = setOf(optionIndex)) }
            submit()
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.submitted) return
        val question = state.current ?: return
        if (state.selected.isEmpty()) return

        val elapsed = (System.currentTimeMillis() - questionShownAt).coerceAtLeast(0)

        viewModelScope.launch {
            val isCorrect = quizRepository.submitAnswer(
                sessionId = sessionId,
                question = question,
                userAnswers = state.selected,
                elapsedMs = elapsed
            )

            _uiState.update { s ->
                s.copy(
                    submitted = true,
                    lastCorrect = isCorrect,
                    correctCount = if (isCorrect) s.correctCount + 1 else s.correctCount,
                    wrongCount = if (isCorrect) s.wrongCount else s.wrongCount + 1,
                    elapsedMs = System.currentTimeMillis() - sessionStartedAt,
                    records = s.records + AnswerRecord(
                        question = question,
                        userAnswers = s.selected.sorted(),
                        isCorrect = isCorrect,
                        elapsedMs = elapsed
                    )
                )
            }
        }
    }

    fun next() {
        val state = _uiState.value
        val nextIndex = state.index + 1
        if (nextIndex >= state.total) {
            finish()
            return
        }
        _uiState.update {
            it.copy(index = nextIndex, selected = emptySet(), submitted = false)
        }
        questionShownAt = System.currentTimeMillis()
    }

    private fun finish() {
        val state = _uiState.value
        viewModelScope.launch {
            runCatching {
                quizRepository.finishSession(
                    sessionId,
                    com.tcmfactory.data.repository.SessionResult(sessionId, state.records)
                )
            }
            _uiState.update {
                it.copy(finished = true, submitted = false, elapsedMs = System.currentTimeMillis() - sessionStartedAt)
            }
        }
    }

    fun restart() {
        _uiState.value = QuizSessionUiState(mode = mode)
        start()
    }

    fun exitEarly() {
        finish()
    }
}

@Composable
fun QuizSessionScreen(
    onExit: () -> Unit,
    viewModel: QuizSessionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 答题期间保持屏幕常亮
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (state.emptyHint != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = state.emptyHint.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onExit, shape = RoundedCornerShape(14.dp)) { Text("返回") }
        }
        return
    }

    if (state.finished) {
        QuizResultView(state = state, onExit = onExit, onRestart = viewModel::restart)
        return
    }

    val question = state.current ?: return

    Column(modifier = Modifier.fillMaxSize()) {

        // ---------- 顶部 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.exitEarly() }) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "退出",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                ThinProgressBar(progress = state.progress, height = 5.dp)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${state.mode.label} · ${state.index + 1}/${state.total}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "对 ${state.correctCount} · 错 ${state.wrongCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        // ---------- 内容 ----------
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TagPill(text = question.type.label)
                if (question.chapter != null) {
                    Spacer(Modifier.width(6.dp))
                    TagPill(
                        text = question.chapter,
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (question.wrongCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    TagPill(
                        text = "错 ${question.wrongCount} 次",
                        container = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = question.stem,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(18.dp))

            question.options.forEachIndexed { index, option ->
                OptionRow(
                    label = ('A' + index).toString(),
                    text = option,
                    selected = index in state.selected,
                    submitted = state.submitted,
                    isCorrectOption = index in question.answers,
                    onClick = { viewModel.toggleOption(index) }
                )
                Spacer(Modifier.height(8.dp))
            }

            if (state.submitted) {
                Spacer(Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.lastCorrect) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (state.lastCorrect) "回答正确" else
                                "回答错误 · 正确答案 ${question.correctText}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (state.lastCorrect) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        if (!question.explanation.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "解析",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = question.explanation.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (!question.sourceRef.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "出处：${question.sourceRef}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // ---------- 底部操作 ----------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp)
        ) {
            when {
                state.submitted -> Button(
                    onClick = { viewModel.next() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(if (state.index + 1 >= state.total) "查看结果" else "下一题")
                }

                question.isMulti -> Button(
                    onClick = { viewModel.submit() },
                    enabled = state.selected.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("确认作答") }

                else -> Text(
                    text = "点击选项即可作答",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    text: String,
    selected: Boolean,
    submitted: Boolean,
    isCorrectOption: Boolean,
    onClick: () -> Unit
) {
    val container = when {
        submitted && isCorrectOption -> MaterialTheme.colorScheme.primaryContainer
        submitted && selected && !isCorrectOption -> MaterialTheme.colorScheme.errorContainer
        selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        submitted && isCorrectOption -> MaterialTheme.colorScheme.primary
        submitted && selected -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(14.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(enabled = !submitted, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(
                    color = if (selected || (submitted && isCorrectOption)) {
                        borderColor
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                .border(1.5.dp, borderColor, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected || (submitted && isCorrectOption)) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun QuizResultView(
    state: QuizSessionUiState,
    onExit: () -> Unit,
    onRestart: () -> Unit
) {
    val total = state.records.size
    val correct = state.records.count { it.isCorrect }
    val accuracy = if (total == 0) 0 else correct * 100 / total

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        Box(
            modifier = Modifier
                .size(84.dp)
                .background(
                    color = if (accuracy >= 60) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    shape = RoundedCornerShape(26.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$accuracy%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (accuracy >= 60) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "本轮练习完成",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatBlock("$total", "总题数")
                    StatBlock("$correct", "答对", valueColor = MaterialTheme.colorScheme.primary)
                    StatBlock("${total - correct}", "答错", valueColor = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatBlock(TimeFormat.duration(state.elapsedMs), "总用时")
                    StatBlock(
                        if (total > 0) "${state.elapsedMs / total / 1000}s" else "0s",
                        "平均单题"
                    )
                }
            }
        }

        if (total - correct > 0) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "答错的 ${total - correct} 题已加入错题本，可随时重做",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onRestart,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) { Text("再来一组") }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onExit) { Text("返回") }
        Spacer(Modifier.height(12.dp))
    }
}
