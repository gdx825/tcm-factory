package com.tcmfactory.feature.recite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcmfactory.core.datastore.SettingsRepository
import com.tcmfactory.core.srs.SrsRating
import com.tcmfactory.domain.model.Card
import com.tcmfactory.domain.model.ReviewOutcome
import com.tcmfactory.domain.model.SessionStats
import com.tcmfactory.domain.usecase.BuildStudyQueueUseCase
import com.tcmfactory.domain.usecase.CountUpcomingDueUseCase
import com.tcmfactory.domain.usecase.ReviewCardUseCase
import com.tcmfactory.domain.usecase.UndoReviewUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val cards: List<Card> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    val loading: Boolean = true,
    val finished: Boolean = false,
    val lastFeedback: ReviewOutcome? = null,
    val remembered: Int = 0,
    val forgotten: Int = 0,
    val swipeThreshold: Float = 0.22f,
    val fourRatings: Boolean = false,
    val nextDueCount: Int = 0,
    val emptyReason: String? = null
) {
    val current: Card? get() = cards.getOrNull(index)

    val progress: Float
        get() = if (cards.isEmpty()) 0f else (index.toFloat() / cards.size).coerceIn(0f, 1f)

    val remaining: Int get() = (cards.size - index).coerceAtLeast(0)

    val canUndo: Boolean get() = index > 0
}

@HiltViewModel
class CardReviewViewModel @Inject constructor(
    private val buildQueue: BuildStudyQueueUseCase,
    private val reviewCard: ReviewCardUseCase,
    private val undoReview: UndoReviewUseCase,
    private val countUpcomingDue: CountUpcomingDueUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    /** 本地操作历史，用于撤销时精确回滚本次会话内的计数。 */
    private val history = mutableListOf<SrsRating>()

    private var sessionStartMs = System.currentTimeMillis()
    private var cardShownAtMs = sessionStartMs

    init {
        loadQueue()
    }

    private fun loadQueue() {
        viewModelScope.launch {
            val settings = settingsRepository.settingsSnapshot()
            val queue = buildQueue()
            val now = System.currentTimeMillis()
            sessionStartMs = now
            cardShownAtMs = now
            history.clear()

            _state.value = ReviewUiState(
                cards = queue.cards,
                index = 0,
                revealed = false,
                loading = false,
                finished = queue.cards.isEmpty(),
                swipeThreshold = settings.swipeThreshold,
                fourRatings = settings.enableFourRatings,
                nextDueCount = queue.total,
                emptyReason = if (queue.cards.isEmpty()) "今日任务已完成，明天再来" else null
            )
        }
    }

    fun reload() {
        _state.value = ReviewUiState(loading = true)
        loadQueue()
    }

    fun reveal() {
        if (_state.value.revealed) return
        _state.update { it.copy(revealed = true) }
    }

    fun rate(rating: SrsRating) {
        val snapshot = _state.value
        val card = snapshot.current ?: return
        val elapsed = (System.currentTimeMillis() - cardShownAtMs).coerceAtLeast(0)

        viewModelScope.launch {
            val outcome = reviewCard(card, rating, elapsed)
            history += rating

            val isForgotten = rating == SrsRating.AGAIN
            val nextIndex = snapshot.index + 1
            _state.update { s ->
                s.copy(
                    index = nextIndex,
                    revealed = false,
                    lastFeedback = outcome,
                    remembered = if (isForgotten) s.remembered else s.remembered + 1,
                    forgotten = if (isForgotten) s.forgotten + 1 else s.forgotten,
                    finished = nextIndex >= s.cards.size,
                    nextDueCount = (s.nextDueCount - 1).coerceAtLeast(0)
                )
            }
            cardShownAtMs = System.currentTimeMillis()
        }
    }

    fun undo() {
        val snapshot = _state.value
        if (snapshot.index == 0) return

        viewModelScope.launch {
            if (undoReview() == null) return@launch
            val lastRating = history.removeLastOrNull()

            _state.update { s ->
                val wasForgotten = lastRating == SrsRating.AGAIN
                s.copy(
                    index = (s.index - 1).coerceAtLeast(0),
                    revealed = false,
                    finished = false,
                    lastFeedback = null,
                    remembered = if (wasForgotten) s.remembered else (s.remembered - 1).coerceAtLeast(0),
                    forgotten = if (wasForgotten) (s.forgotten - 1).coerceAtLeast(0) else s.forgotten,
                    nextDueCount = s.nextDueCount + 1
                )
            }
            cardShownAtMs = System.currentTimeMillis()
        }
    }

    fun sessionStats(): SessionStats {
        val s = _state.value
        return SessionStats(
            reviewed = s.remembered + s.forgotten,
            remembered = s.remembered,
            forgotten = s.forgotten,
            elapsedMs = (System.currentTimeMillis() - sessionStartMs).coerceAtLeast(0),
            nextDueCount = s.nextDueCount
        )
    }

    fun loadUpcomingCount() {
        viewModelScope.launch {
            val count = countUpcomingDue(withinDays = 1)
            _state.update { it.copy(nextDueCount = count) }
        }
    }
}
