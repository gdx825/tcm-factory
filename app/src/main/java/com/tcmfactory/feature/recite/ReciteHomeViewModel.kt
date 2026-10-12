package com.tcmfactory.feature.recite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcmfactory.core.common.DayBoundary
import com.tcmfactory.data.repository.CardRepository
import com.tcmfactory.domain.model.DeckSummary
import com.tcmfactory.domain.model.TodayProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ReciteHomeUiState(
    val greeting: String = "你好",
    val progress: TodayProgress = TodayProgress.EMPTY,
    val decks: List<DeckSummary> = emptyList(),
    val totalCards: Int = 0,
    val loading: Boolean = true
) {
    val totalDue: Int get() = progress.dueNow
    val canStart: Boolean get() = totalDue > 0
}

@HiltViewModel
class ReciteHomeViewModel @Inject constructor(
    repository: CardRepository
) : ViewModel() {

    private val now = System.currentTimeMillis()

    val uiState: StateFlow<ReciteHomeUiState> = combine(
        repository.observeDeckSummaries(now),
        repository.observeTodayProgress(),
        repository.observeTotalCardCount()
    ) { decks, progress, totalCards ->
        ReciteHomeUiState(
            greeting = DayBoundary.greeting(System.currentTimeMillis()),
            progress = progress,
            decks = decks,
            totalCards = totalCards,
            loading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReciteHomeUiState()
    )
}
