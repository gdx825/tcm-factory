package com.tcmfactory.feature.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcmfactory.core.designsystem.SectionHeader
import com.tcmfactory.core.designsystem.TagPill
import com.tcmfactory.core.designsystem.ThinProgressBar
import com.tcmfactory.core.reader.CleanOptions
import com.tcmfactory.domain.model.Book

@Composable
fun LibraryScreen(
    onOpenBook: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onFilePicked(uri)
    }

    val currentImport = importState
    if (currentImport != null) {
        ImportPreviewScreen(
            state = currentImport,
            onOptionsChange = viewModel::updateOptions,
            onEncodingChange = viewModel::setEncoding,
            onCancel = viewModel::cancelImport,
            onConfirm = { title, author, dynasty ->
                viewModel.confirmImport(title, author, dynasty) { bookId -> onOpenBook(bookId) }
            }
        )
        return
    }

    LibraryContent(
        books = books,
        onOpenBook = onOpenBook,
        onImportClick = { picker.launch(arrayOf("text/plain", "text/*", "*/*")) },
        onDelete = viewModel::deleteBook
    )
}

@Composable
private fun LibraryContent(
    books: List<Book>,
    onOpenBook: (Long) -> Unit,
    onImportClick: () -> Unit,
    onDelete: (Long) -> Unit
) {
    val continuing = remember(books) {
        books.filter { it.hasProgress }.maxByOrNull { it.lastReadAt ?: 0L }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 28.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "古籍",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (books.isEmpty()) "还没有古籍" else "共 ${books.size} 部典籍",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onImportClick) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "导入古籍",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (continuing != null) {
            item {
                SectionHeader(title = "继续阅读")
                ContinueReadingCard(book = continuing, onClick = { onOpenBook(continuing.id) })
            }
        }

        if (books.isNotEmpty()) {
            item { SectionHeader(title = "全部典籍") }
            val rows = books.chunked(3)
            items(rows.size) { rowIndex ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rows[rowIndex].forEach { book ->
                        BookTile(
                            book = book,
                            onClick = { onOpenBook(book.id) },
                            onDelete = { onDelete(book.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - rows[rowIndex].size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        } else {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "导入你自己的古籍 TXT",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "支持 GBK / GB18030 / Big5 / UTF-8 自动识别，\n自动切分卷、篇、章、条并生成目录。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onImportClick, shape = RoundedCornerShape(14.dp)) {
                        Text("选择 TXT 文件")
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueReadingCard(book: Book, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (book.isBuiltin) TagPill(text = "内置")
            }
            if (book.subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = book.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(14.dp))
            ThinProgressBar(progress = book.progress)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "已读 ${book.progressText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "第 ${book.lastReadChapterIndex + 1} 篇 · 共 ${book.chapterCount} 篇",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BookTile(
    book: Book,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .aspectRatio(0.72f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 4.dp, top = 12.dp, bottom = 10.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.weight(1f))
                if (book.isBuiltin) {
                    TagPill(text = "内置")
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = book.charCountText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (book.hasProgress) {
                    Spacer(Modifier.height(5.dp))
                    ThinProgressBar(progress = book.progress, height = 3.dp)
                }
            }

            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "更多",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("删除") },
                        enabled = !book.isBuiltin,
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

// ==================================================================
//  导入预览页
// ==================================================================

@Composable
private fun ImportPreviewScreen(
    state: ImportUiState,
    onOptionsChange: (CleanOptions) -> Unit,
    onEncodingChange: (String?) -> Unit,
    onCancel: () -> Unit,
    onConfirm: (String, String?, String?) -> Unit
) {
    val preview = state.preview

    var title by remember(preview) { mutableStateOf(preview?.suggestedTitle.orEmpty()) }
    var author by remember(preview) { mutableStateOf("") }
    var dynasty by remember(preview) { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "导入预览",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = state.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (state.analyzing || state.committing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (state.error != null) {
            Text(
                text = state.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            if (preview != null) {
                // ---------- 识别结果 ----------
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ResultRow("编码", "${preview.encoding}　(${(preview.encodingConfidence * 100).toInt()}% 置信)")
                        ResultRow("字数", "${preview.charCount} 字")
                        ResultRow(
                            "章节",
                            if (preview.hasStructure) {
                                "识别到 ${preview.chapterCount} 个${preview.structureName}级章节"
                            } else {
                                "未识别到章节标记，已按字数切分"
                            }
                        )
                        ResultRow("段落重排", "合并 ${preview.mergedBreaks} 处硬换行")
                        ResultRow("噪音剔除", "移除 ${preview.removedLines} 行")
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ---------- 编码手动切换 ----------
                if (preview.candidateEncodings.size > 1) {
                    Text(
                        text = "编码",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        preview.candidateEncodings.take(4).forEach { candidate ->
                            FilterChip(
                                selected = preview.encoding == candidate.name,
                                onClick = {
                                    onEncodingChange(
                                        if (preview.encoding == candidate.name) null else candidate.name
                                    )
                                },
                                label = { Text(candidate.name, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "若正文出现乱码，请切换编码重新预览。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // ---------- 处理选项 ----------
                Text(
                    text = "处理选项",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                ToggleRow(
                    title = "智能段落重排",
                    subtitle = "把每行固定字数断开的碎行合并回自然段",
                    checked = state.options.mergeHardWraps,
                    onCheckedChange = { onOptionsChange(state.options.copy(mergeHardWraps = it)) }
                )
                ToggleRow(
                    title = "剔除页码与水印",
                    subtitle = "移除「- 12 -」「【第13页】」等噪音行",
                    checked = state.options.removeNoiseLines,
                    onCheckedChange = { onOptionsChange(state.options.copy(removeNoiseLines = it)) }
                )
                ToggleRow(
                    title = "全角空格转半角",
                    subtitle = "古籍常用全角空格做缩进，默认保留",
                    checked = state.options.fullWidthToHalfWidth,
                    onCheckedChange = { onOptionsChange(state.options.copy(fullWidthToHalfWidth = it)) }
                )

                Spacer(Modifier.height(16.dp))

                // ---------- 正文预览 ----------
                Text(
                    text = "正文预览",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(14.dp)
                ) {
                    Text(
                        text = preview.headText.ifBlank { "（无内容）" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ---------- 章节结构 ----------
                if (preview.chapters.isNotEmpty()) {
                    Text(
                        text = "章节结构（前 ${preview.chapters.size} 个）",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            preview.chapters.forEach { chapter ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = chapter.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "${chapter.charCount}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // ---------- 书籍信息 ----------
                Text(
                    text = "书籍信息",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("书名") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("作者") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dynasty,
                        onValueChange = { dynasty = it },
                        label = { Text("朝代") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(110.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            ) { Text("取消") }
            Button(
                onClick = { onConfirm(title, author, dynasty) },
                enabled = preview != null && !state.committing && title.isNotBlank(),
                modifier = Modifier
                    .weight(1.6f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("确认导入") }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(78.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ToggleRow(
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
