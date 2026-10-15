package com.tcmfactory.feature.supporter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcmfactory.core.database.dao.SupporterDao
import com.tcmfactory.core.database.entity.SupporterEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SupporterListViewModel @Inject constructor(
    supporterDao: SupporterDao
) : ViewModel() {

    val supporters: StateFlow<List<SupporterEntity>> = supporterDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
