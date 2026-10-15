package com.tcmfactory.feature.knowledge

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcmfactory.core.knowledge.OfficialSources
import com.tcmfactory.core.knowledge.PackKind
import com.tcmfactory.core.knowledge.PackSource
import com.tcmfactory.core.knowledge.PlanItem
import com.tcmfactory.data.repository.InstallReport
import com.tcmfactory.data.repository.InstalledPack
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(
    onBack: () -> Unit,
    viewModel: KnowledgeViewModel = hiltViewModel()
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::installFromUri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("知识库") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SourceStatusCard(state, viewModel) }

            if (state.applying) {
                item { ProgressCard(state) }
            }

            if (state.plan != null && state.hasPending) {
                item { PlanCard(state, viewModel) }
            }

            if (state.reports.isNotEmpty()) {
                item { ReportCard(state.reports) }
            }

            item {
                SectionTitle(
                    "已安装知识包",
                    subtitle = "共 ${state.installed.size} 个 · " +
                        "${state.installed.sumOf { it.itemCount }} 条内容"
                )
            }
            items(state.installed, key = { it.packId }) { pack ->
                InstalledPackRow(pack = pack, onUninstall = { viewModel.uninstall(pack.packId) })
            }

            item { LocalFileCard(onPick = { filePicker.launch(arrayOf("application/json", "text/plain", "*/*")) }) }

            item { SourceSettingsCard(state, viewModel) }

            item { HelpCard() }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ==================================================================
//  数据源状态
// ==================================================================

@Composable
private fun SourceStatusCard(state: KnowledgeUiState, viewModel: KnowledgeViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (state.sources.isConfigured) "已配置数据源" else "尚未配置数据源",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (state.sources.isConfigured) {
                    state.sources.urls.first() +
                        if (state.sources.urls.size > 1) " 等 ${state.sources.urls.size} 个源" else ""
                } else {
                    "知识库支持联网更新。配置数据源后即可拉取增量内容；不配置也不影响已有功能，全部内容离线可用。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.sources.lastCheckAt > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "上次检查：${formatTime(state.sources.lastCheckAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.error?.let { err ->
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = err,
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            state.message?.let { msg ->
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = viewModel::checkForUpdates,
                    enabled = !state.checking && !state.applying && state.sources.isConfigured
                ) {
                    if (state.checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.checking) "检查中…" else "检查更新")
                }
                if (state.message != null || state.error != null) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = viewModel::clearMessage) { Text("知道了") }
                }
            }
        }
    }
}

// ==================================================================
//  进度
// ==================================================================

@Composable
private fun ProgressCard(state: KnowledgeUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = state.currentStage ?: "正在处理…",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            if (state.progress >= 0f) {
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ==================================================================
//  更新计划
// ==================================================================

@Composable
private fun PlanCard(state: KnowledgeUiState, viewModel: KnowledgeViewModel) {
    val plan = state.plan ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "可更新内容",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(8.dp))

            plan.pending.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.entry.packName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = when (item) {
                            is PlanItem.Install -> "${item.entry.itemCount} 条 · 新增"
                            is PlanItem.Update -> "${item.fromVersion} → ${item.entry.version}"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            val sourceRisk = plan.pending.any { it is PlanItem.Update && it.fromSource == PackSource.FILE }
            if (sourceRisk) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "提示：其中包含你手动导入过的包，更新会覆盖其内容。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            plan.rejected.takeIf { it.isNotEmpty() }?.let { rejected ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "另有 ${rejected.size} 个索引项因格式问题被跳过。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = viewModel::applyPlan,
                enabled = !state.applying
            ) {
                Text("立即更新（${plan.pendingItemCount} 条）")
            }
        }
    }
}

// ==================================================================
//  结果报告
// ==================================================================

@Composable
private fun ReportCard(reports: List<InstallReport>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "本次结果",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            reports.forEach { report ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = report.packName.ifBlank { report.packId },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (report.success) "成功" else "失败",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (report.success) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    if (report.success) {
                        Text(
                            text = "新增 ${report.added} 条 · 更新 ${report.updated} 条" +
                                if (report.skipped > 0) " · 跳过 ${report.skipped} 条" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = report.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    report.warnings.forEach { w ->
                        Text(
                            text = "· $w",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    report.itemErrors.take(5).forEach { e ->
                        Text(
                            text = "· 第 ${e.index + 1} 条被跳过：${e.message}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (report.itemErrors.size > 5) {
                        Text(
                            text = "· 其余 ${report.itemErrors.size - 5} 条错误已省略",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ==================================================================
//  已安装包
// ==================================================================

@Composable
private fun InstalledPackRow(pack: InstalledPack, onUninstall: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pack.packName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(6.dp))
                    if (pack.source == PackSource.BUILTIN) {
                        Badge("内置")
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append("v${pack.version}")
                        append(" · ${pack.itemCount} 条")
                        append(" · ${kindLabel(pack.kind)}")
                        if (pack.source == PackSource.NETWORK) append(" · 联网更新")
                        if (pack.source == PackSource.FILE) append(" · 本地导入")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                pack.updatedAt?.takeIf { it > 0 }?.let {
                    Text(
                        text = "更新于 ${formatTime(it)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (pack.source != PackSource.BUILTIN) {
                IconButton(onClick = onUninstall) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "卸载",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

// ==================================================================
//  本地文件安装
// ==================================================================

@Composable
private fun LocalFileCard(onPick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "从本地文件安装",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "不需要服务器也能更新。选择一份知识包 JSON 即可导入，" +
                    "格式与联网更新使用的完全一致。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onPick) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("选择知识包文件")
            }
        }
    }
}

// ==================================================================
//  数据源设置
// ==================================================================

@Composable
private fun SourceSettingsCard(state: KnowledgeUiState, viewModel: KnowledgeViewModel) {
    var input by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "数据源设置",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            state.sources.urls.forEach { url ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // 官方源给出中文短名，否则用户看到的就是一串 jsDelivr 网址，
                        // 既不知道是谁的、也不敢删。
                        OfficialSources.displayName(url)?.let { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    IconButton(onClick = { viewModel.removeSource(url) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "移除", modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (state.sources.urls.isEmpty()) {
                Text(
                    text = "尚未添加数据源。点下方「填入官方源」即可接收题库与支持者名单更新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("知识库地址") },
                placeholder = { Text("https://你的域名/tcm-kb") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                )
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        viewModel.addSource(input)
                        input = ""
                    },
                    enabled = input.isNotBlank()
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        // 只填主源。其余镜像由「恢复官方源」一并补上，
                        // 避免用户手动连点三次还不知道自己漏了哪个。
                        input = OfficialSources.URLS.first()
                    }
                ) {
                    Text("填入官方源")
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            SwitchRow(
                title = "允许明文传输（HTTP）",
                subtitle = "默认只允许 HTTPS。仅当你自建局域网数据源时才需要开启，开启后内容可能被中间人篡改。",
                checked = state.sources.allowCleartext,
                onCheckedChange = viewModel::setAllowCleartext
            )
            SwitchRow(
                title = "启动时自动检查更新",
                subtitle = "后台每天检查一次是否有新版本，只提示不自动下载。",
                checked = state.sources.autoCheck,
                onCheckedChange = viewModel::setAutoCheck
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ==================================================================
//  帮助
// ==================================================================

@Composable
private fun HelpCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "如何自建知识库源",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "1. 用工程内 tools/build_knowledge_packs.py 把知识包 JSON 打成发布目录；\n" +
                    "2. 把生成的目录上传到任意静态托管（GitHub Pages / Gitee Pages / 对象存储 / 自建服务器）；\n" +
                    "3. 把该目录地址填到上方输入框。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "更新机制说明",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "· 每个包独立版本，只下载有变化的包；\n" +
                    "· 下载后会校验 SHA-256，校验不通过一律丢弃、不写入任何内容；\n" +
                    "· 更新只替换内容，你的背诵进度（等级、到期时间）完整保留；\n" +
                    "· 只有版本号高于本地才会更新，不会把内容回退到旧版。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================================================================
//  小组件
// ==================================================================

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    PackKind.CARDS -> "背诵卡"
    PackKind.QUESTIONS -> "题库"
    PackKind.BOOK -> "古籍"
    else -> kind
}

private fun formatTime(millis: Long): String =
    runCatching { TIME_FORMAT.format(Instant.ofEpochMilli(millis)) }.getOrDefault("-")
