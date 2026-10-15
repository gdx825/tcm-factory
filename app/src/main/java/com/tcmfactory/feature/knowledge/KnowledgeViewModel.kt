package com.tcmfactory.feature.knowledge

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcmfactory.core.datastore.KnowledgeSettingsRepository
import com.tcmfactory.core.datastore.KnowledgeSources
import com.tcmfactory.core.knowledge.PackKind
import com.tcmfactory.core.knowledge.PackSource
import com.tcmfactory.core.knowledge.PlanItem
import com.tcmfactory.core.knowledge.UpdatePlan
import com.tcmfactory.data.repository.InstallReport
import com.tcmfactory.data.repository.InstalledPack
import com.tcmfactory.data.repository.KnowledgeRepository
import com.tcmfactory.data.work.WorkScheduler
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

data class KnowledgeUiState(
    val installed: List<InstalledPack> = emptyList(),
    val sources: KnowledgeSources = KnowledgeSources(),
    val checking: Boolean = false,
    val plan: UpdatePlan? = null,
    val applying: Boolean = false,
    val currentStage: String? = null,
    /** 0f..1f，-1f 表示不确定进度。 */
    val progress: Float = -1f,
    val reports: List<InstallReport> = emptyList(),
    val message: String? = null,
    val error: String? = null
) {
    val pendingCount: Int get() = plan?.pending?.size ?: 0
    val hasPending: Boolean get() = pendingCount > 0
}

@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    private val repository: KnowledgeRepository,
    private val settings: KnowledgeSettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _ui = MutableStateFlow(KnowledgeUiState())
    val ui: StateFlow<KnowledgeUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeInstalledPacks().collect { list ->
                _ui.update { it.copy(installed = list) }
            }
        }
        viewModelScope.launch {
            settings.sources.collect { s ->
                _ui.update { it.copy(sources = s) }
            }
        }
    }

    // ---------------- 检查更新 ----------------

    fun checkForUpdates() {
        if (_ui.value.checking || _ui.value.applying) return
        viewModelScope.launch {
            _ui.update { it.copy(checking = true, error = null, message = null, plan = null, reports = emptyList()) }
            // fresh = true：手动检查一律直取源站。开发者刚发布的新名单，
            // 用户点一下就该看到，不能因为 CDN 缓存让他等几小时。
            val result = repository.checkForUpdates(fresh = true)
            result.fold(
                onSuccess = { plan ->
                    _ui.update {
                        it.copy(
                            checking = false,
                            plan = plan,
                            message = when {
                                plan.blockedByAppVersion -> plan.blockReason
                                plan.pending.isEmpty() -> "已是最新，全部知识包均为最新版本。"
                                else -> "发现 ${plan.pending.size} 个可更新知识包。"
                            }
                        )
                    }
                },
                onFailure = { e ->
                    _ui.update {
                        it.copy(checking = false, error = e.message ?: "检查更新失败")
                    }
                }
            )
        }
    }

    // ---------------- 执行更新 ----------------

    fun applyPlan() {
        val plan = _ui.value.plan ?: return
        val pending = plan.pending
        if (pending.isEmpty() || _ui.value.applying) return

        viewModelScope.launch {
            _ui.update { it.copy(applying = true, error = null, reports = emptyList(), progress = 0f) }

            // 用检查阶段实际解析成功的那个源，而不是「列表里的第一个」。
            // 否则第一个源不可达时会出现「检查成功、下载必然失败」。
            val baseUrl = plan.resolvedBaseUrl
            if (baseUrl.isBlank()) {
                _ui.update { it.copy(applying = false, error = "数据源地址无效，请重新检查更新。") }
                return@launch
            }
            val reports = ArrayList<InstallReport>(pending.size)

            pending.forEachIndexed { index, item ->
                val entry = item.entry
                val report = repository.installFromSource(
                    baseUrl = baseUrl,
                    entry = entry,
                    onStage = { stage -> _ui.update { it.copy(currentStage = stage) } },
                    onProgress = { done, total ->
                        val frac = if (total > 0) done.toFloat() / total.toFloat() else -1f
                        val overall = (index + frac.coerceIn(0f, 1f)) / pending.size
                        _ui.update { it.copy(progress = if (frac < 0f) -1f else overall) }
                    }
                )
                reports += report
            }

            _ui.update {
                it.copy(
                    applying = false,
                    currentStage = null,
                    progress = -1f,
                    reports = reports,
                    plan = null,
                    message = summarize(reports)
                )
            }
        }
    }

    private fun summarize(reports: List<InstallReport>): String {
        val ok = reports.count { it.success }
        val fail = reports.size - ok
        val added = reports.sumOf { it.added }
        val updated = reports.sumOf { it.updated }
        return buildString {
            append("共处理 ${reports.size} 个知识包：成功 $ok")
            if (fail > 0) append("，失败 $fail")
            append("。新增 $added 条，更新 $updated 条。")
        }
    }

    // ---------------- 本地文件安装 ----------------

    fun installFromUri(uri: Uri) {
        if (_ui.value.applying) return
        viewModelScope.launch {
            _ui.update { it.copy(applying = true, error = null, currentStage = "正在读取文件…", reports = emptyList()) }
            val report = runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("无法打开所选文件")
                }
                val name = displayNameOf(uri) ?: "本地知识包"
                repository.installFromBytes(bytes, name) { stage ->
                    _ui.update { it.copy(currentStage = stage) }
                }
            }.getOrElse { e ->
                InstallReport(
                    packId = "",
                    packName = "本地文件",
                    version = "",
                    kind = PackKind.CARDS,
                    source = PackSource.FILE,
                    success = false,
                    errorMessage = e.message ?: "导入失败"
                )
            }
            _ui.update {
                it.copy(
                    applying = false,
                    currentStage = null,
                    reports = it.reports + report,
                    message = if (report.success) {
                        "已从本地文件安装：新增 ${report.added} 条，更新 ${report.updated} 条。"
                    } else null,
                    error = report.errorMessage
                )
            }
        }
    }

    private fun displayNameOf(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()

    // ---------------- 卸载 ----------------

    fun uninstall(packId: String) {
        viewModelScope.launch {
            val ok = repository.uninstall(packId)
            _ui.update {
                it.copy(
                    message = if (ok) "已卸载知识包。" else null,
                    error = if (ok) null else "内置知识包不可卸载。"
                )
            }
        }
    }

    // ---------------- 数据源配置 ----------------

    fun setAutoCheck(value: Boolean) {
        viewModelScope.launch {
            settings.setAutoCheck(value)
            // 立即应用调度，避免要等下次冷启动才生效
            if (value && _ui.value.sources.urls.isNotEmpty()) {
                WorkScheduler.scheduleKnowledgeCheck(context)
            } else {
                WorkScheduler.cancelKnowledgeCheck(context)
            }
        }
    }

    fun addSource(raw: String) {
        viewModelScope.launch {
            val ok = settings.addSource(raw)
            // 新增数据源后若已开启自动检查，需要让它立即生效
            if (ok && _ui.value.sources.autoCheck) WorkScheduler.scheduleKnowledgeCheck(context)
            _ui.update {
                it.copy(
                    message = if (ok) "已添加数据源。" else null,
                    error = if (ok) null else "地址无效或已存在。请输入以 https:// 开头的完整地址。"
                )
            }
        }
    }

    fun removeSource(url: String) {
        viewModelScope.launch {
            settings.removeSource(url)
            // 数据源被删空后「自动检查」已无意义，及时取消调度，避免空跑
            if (settings.current().urls.isEmpty()) WorkScheduler.cancelKnowledgeCheck(context)
        }
    }

    fun setAllowCleartext(value: Boolean) {
        viewModelScope.launch { settings.setAllowCleartext(value) }
    }

    fun clearMessage() = _ui.update { it.copy(message = null, error = null) }
}

/** 供界面展示更新计划条目。 */
fun PlanItem.describe(): String = when (this) {
    is PlanItem.Install -> "待安装"
    is PlanItem.Update -> "可更新 ${fromVersion} → ${entry.version}"
    is PlanItem.UpToDate -> "已是最新 ${installedVersion}"
    is PlanItem.Rejected -> "已跳过：$reason"
}
