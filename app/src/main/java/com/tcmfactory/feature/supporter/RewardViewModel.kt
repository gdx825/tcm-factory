package com.tcmfactory.feature.supporter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcmfactory.core.datastore.SupporterRepository
import com.tcmfactory.core.datastore.SupporterStatus
import com.tcmfactory.core.license.LicenseCheckResult
import com.tcmfactory.core.license.LicenseFormat
import com.tcmfactory.core.license.LicenseVerifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RewardUiState(
    val input: String = "",
    val message: String? = null,
    val isError: Boolean = false,
    val submitting: Boolean = false
) {
    /** 输入框里的内容是否已经像是一个完整的支持者码（长度与前缀都对）。 */
    val canSubmit: Boolean
        get() {
            val normalized = LicenseVerifier.normalize(input)
            return normalized.startsWith(LicenseFormat.PREFIX) &&
                normalized.length == LicenseFormat.PREFIX.length + LicenseFormat.ENCODED_CHARS
        }
}

@HiltViewModel
class RewardViewModel @Inject constructor(
    private val repository: SupporterRepository
) : ViewModel() {

    val status: StateFlow<SupporterStatus> = repository.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SupporterStatus.NONE)

    private val _ui = MutableStateFlow(RewardUiState())
    val ui: StateFlow<RewardUiState> = _ui.asStateFlow()

    fun onInputChange(value: String) {
        // 允许用户粘贴带空格/换行的内容，这里只做长度截断，真正的清理在提交时进行
        _ui.update { it.copy(input = value.take(300), message = null) }
    }

    /** 从剪贴板粘贴后直接提交（界面上是「粘贴并确认」按钮）。 */
    fun pasteAndSubmit(clipText: String?) {
        if (clipText.isNullOrBlank()) {
            _ui.update { it.copy(message = "剪贴板里没有内容", isError = true) }
            return
        }
        _ui.update { it.copy(input = clipText.take(300)) }
        submit()
    }

    fun submit() {
        val raw = _ui.value.input
        if (raw.isBlank()) return

        _ui.update { it.copy(submitting = true, message = null) }
        viewModelScope.launch {
            val result = repository.redeem(raw)
            _ui.update {
                it.copy(
                    submitting = false,
                    isError = result !is LicenseCheckResult.Valid,
                    message = when (result) {
                        is LicenseCheckResult.Valid ->
                            "感谢你的支持，徽标已点亮。"
                        else -> result.message()
                    },
                    // 成功时清空输入框，避免用户以为还需要再点一次
                    input = if (result is LicenseCheckResult.Valid) "" else it.input
                )
            }
        }
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }
}
