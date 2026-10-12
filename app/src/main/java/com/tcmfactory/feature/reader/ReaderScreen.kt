package com.tcmfactory.feature.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcmfactory.core.datastore.ReaderSettings
import com.tcmfactory.core.designsystem.LxgwWenKai
import com.tcmfactory.core.designsystem.TagPill
import com.tcmfactory.domain.model.BookChapter
import com.tcmfactory.domain.model.BookMark
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 阅读主题色板。阅读器不跟随 App 主题，独立控制，避免夜间读书被全局主题干扰。 */
data class ReaderPalette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val secondary: Color,
    val divider: Color,
    val accent: Color
)

fun readerPalette(theme: String): ReaderPalette = when (theme) {
    ReaderSettings.THEME_DARK -> ReaderPalette(
        background = Color(0xFF1C1B19),
        surface = Color(0xFF262522),
        text = Color(0xFFC9C4B8),
        secondary = Color(0xFF8A857A),
        divider = Color(0xFF38352F),
        accent = Color(0xFF6FA97E)
    )
    ReaderSettings.THEME_GREEN -> ReaderPalette(
        background = Color(0xFFC7D2B5),
        surface = Color(0xFFD6DEC6),
        text = Color(0xFF2E3328),
        secondary = Color(0xFF5A6350),
        divider = Color(0xFFAFBC9E),
        accent = Color(0xFF3E6B4A)
    )
    ReaderSettings.THEME_PARCHMENT -> ReaderPalette(
        background = Color(0xFFF5EBD8),
        surface = Color(0xFFEFE2C8),
        text = Color(0xFF4A3F2E),
        secondary = Color(0xFF8A7B60),
        divider = Color(0xFFDCCEB0),
        accent = Color(0xFF8A6A33)
    )
    else -> ReaderPalette(
        background = Color(0xFFFAF8F3),
        surface = Color(0xFFFFFFFF),
        text = Color(0xFF2B2A28),
        secondary = Color(0xFF6B6862),
        divider = Color(0xFFE8E4DB),
        accent = Color(0xFF2F6F62)
    )
}

/** 阅读时保持屏幕常亮；离开页面即恢复。 */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    onOpenCardSource: (Long) -> Unit = {},
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val draft by viewModel.cardDraft.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()

    val palette = remember(settings.theme) { readerPalette(settings.theme) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var showBars by remember { mutableStateOf(true) }
    var showToc by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    KeepScreenOn(settings.keepScreenOn)

    if (state.loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = palette.accent)
        }
        return
    }

    val book = state.book
    if (book == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error ?: "无法打开书籍", color = palette.text)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onBack) { Text("返回", color = palette.accent) }
            }
        }
        return
    }

    // 反向跳转定位：从卡片「查看原文」进来时直接滚到对应章节
    var initialScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(state.chapters.size, state.initialScrollIndex) {
        if (!initialScrollDone && state.chapters.isNotEmpty()) {
            if (state.initialScrollIndex > 0) {
                listState.scrollToItem(state.initialScrollIndex)
            }
            initialScrollDone = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        // ---------------- 正文 ----------------
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = settings.horizontalPaddingDp.dp,
                end = settings.horizontalPaddingDp.dp,
                top = 72.dp,
                bottom = 96.dp
            )
        ) {
            items(state.chapters.size, key = { state.chapters[it].id }) { index ->
                ChapterBlock(
                    chapter = state.chapters[index],
                    chapterIndex = index,
                    settings = settings,
                    palette = palette,
                    loadText = viewModel::loadSlice,
                    onFirstVisible = { viewModel.onChapterVisible(index, state.chapters[index].startCharOffset) },
                    onTapParagraph = { showBars = !showBars },
                    onLongPressParagraph = { text, start, end ->
                        viewModel.requestMakeCard(index, text, start, end)
                    }
                )
            }
        }

        // ---------------- 工具栏 ----------------
        if (showBars) {
            ReaderTopBar(
                title = book.title,
                subtitle = state.currentChapter?.title.orEmpty(),
                progress = state.progressText,
                palette = palette,
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            ReaderBottomBar(
                palette = palette,
                onToc = { showToc = true },
                onSettings = { showSettings = true },
                onBookmark = {
                    val chapter = state.currentChapter
                    if (chapter != null) {
                        viewModel.addBookmark(
                            chapterIndex = chapter.index,
                            charOffset = chapter.startCharOffset,
                            selectedText = chapter.title
                        )
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // ---------------- 轻提示 ----------------
        if (toast != null) {
            LaunchedEffect(toast) {
                kotlinx.coroutines.delay(1600)
                viewModel.consumeToast()
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.surface)
                ) {
                    Text(
                        text = toast.orEmpty(),
                        color = palette.text,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    // ---------------- 目录 ----------------
    if (showToc) {
        ModalBottomSheet(
            onDismissRequest = { showToc = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            TocSheet(
                chapters = state.chapters,
                currentIndex = state.currentChapterIndex,
                marks = marks,
                onJump = { index ->
                    showToc = false
                    scope.launch { listState.animateScrollToItem(index) }
                }
            )
        }
    }

    // ---------------- 排版设置 ----------------
    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            TypeSettingsSheet(
                settings = settings,
                onFontSize = viewModel::setFontSize,
                onLineHeight = viewModel::setLineHeight,
                onParagraphSpacing = viewModel::setParagraphSpacing,
                onMargin = viewModel::setMargin,
                onFontFamily = viewModel::setFontFamily,
                onTheme = viewModel::setTheme,
                onIndent = viewModel::setIndent,
                onKeepScreenOn = viewModel::setKeepScreenOn
            )
        }
    }

    // ---------------- 制卡 ----------------
    draft?.let { d ->
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissCardDraft,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            MakeCardSheet(
                draft = d,
                onDismiss = viewModel::dismissCardDraft,
                onSave = { front, back -> viewModel.saveCard(front, back, null) }
            )
        }
    }
}

// ==================================================================
//  章节块
// ==================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterBlock(
    chapter: BookChapter,
    chapterIndex: Int,
    settings: ReaderSettings,
    palette: ReaderPalette,
    loadText: suspend (Int, Int) -> String,
    onFirstVisible: () -> Unit,
    onTapParagraph: () -> Unit,
    onLongPressParagraph: (String, Int, Int) -> Unit
) {
    // 只有章节被组合（即进入可视区域附近）时才会真正读取正文，天然懒加载
    val paragraphs by produceState(initialValue = emptyList<Pair<String, Int>>(), chapter.id) {
        val text = loadText(chapter.startCharOffset, chapter.endCharOffset)
        value = splitParagraphs(text, chapter.startCharOffset, chapter.title)
    }

    LaunchedEffect(chapter.id) { onFirstVisible() }

    val fontFamily = when (settings.fontFamily) {
        ReaderSettings.FONT_SERIF -> FontFamily.Serif
        ReaderSettings.FONT_SANS -> FontFamily.SansSerif
        else -> LxgwWenKai
    }
    val fontSize = settings.fontSize.sp
    val lineHeight = (settings.fontSize * settings.lineHeight).sp

    Column(modifier = Modifier.fillMaxWidth()) {

        // 章节标题
        Spacer(Modifier.height(28.dp))
        Text(
            text = chapter.title,
            color = palette.text,
            fontSize = (settings.fontSize + 4).sp,
            lineHeight = ((settings.fontSize + 4) * 1.5).sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = fontFamily,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            textAlign = TextAlign.Center
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(2.dp)
                .background(palette.divider)
        )
        Spacer(Modifier.height(20.dp))

        // 段落
        paragraphs.forEach { (text, offset) ->
            Text(
                text = if (settings.indent) "\u3000\u3000$text" else text,
                color = palette.text,
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontFamily = fontFamily,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onTapParagraph,
                        onLongClick = { onLongPressParagraph(text, offset, offset + text.length) }
                    )
                    .padding(bottom = (settings.fontSize * settings.paragraphSpacing).dp)
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 按段落切分章节文本，并计算每段在**全书**中的绝对字符偏移。
 * 绝对偏移是「长按制卡 → 卡片记录位置 → 反向跳回原文」的基础。
 */
private fun splitParagraphs(
    text: String,
    baseOffset: Int,
    chapterTitle: String
): List<Pair<String, Int>> {
    val result = ArrayList<Pair<String, Int>>()
    var cursor = 0
    text.split('\n').forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty()) return@forEach
        if (line == chapterTitle.trim()) {
            cursor += rawLine.length + 1
            return@forEach
        }
        val found = text.indexOf(line, cursor)
        val absolute = if (found >= 0) baseOffset + found else baseOffset + cursor
        result += line to absolute
        cursor = if (found >= 0) found + line.length else cursor + rawLine.length + 1
    }
    return result
}

// ==================================================================
//  工具栏
// ==================================================================

@Composable
private fun ReaderTopBar(
    title: String,
    subtitle: String,
    progress: String,
    palette: ReaderPalette,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.background.copy(alpha = 0.96f))
            .padding(start = 4.dp, end = 16.dp, top = 28.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = palette.text
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = palette.text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = palette.secondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = progress,
            color = palette.secondary,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun ReaderBottomBar(
    palette: ReaderPalette,
    onToc: () -> Unit,
    onSettings: () -> Unit,
    onBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.background.copy(alpha = 0.96f))
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomAction(Icons.Outlined.List, "目录", palette, onToc)
        BottomAction(Icons.Outlined.FormatSize, "排版", palette, onSettings)
        BottomAction(Icons.Outlined.BookmarkBorder, "书签", palette, onBookmark)
    }
}

@Composable
private fun BottomAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    palette: ReaderPalette,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = palette.text, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = palette.secondary, style = MaterialTheme.typography.labelSmall)
    }
}

// ==================================================================
//  目录抽屉
// ==================================================================

@Composable
private fun TocSheet(
    chapters: List<BookChapter>,
    currentIndex: Int,
    marks: List<BookMark>,
    onJump: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "目录",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "共 ${chapters.size} 篇",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (marks.isNotEmpty()) {
            item {
                Text(
                    text = "书签（${marks.size}）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
                )
            }
            items(marks.size) { i ->
                val mark = marks[i]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mark.selectedText ?: "书签",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (mark.chapterIndex in chapters.indices) {
                        TextButton(onClick = { onJump(mark.chapterIndex) }) { Text("跳转") }
                    }
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }

        items(chapters.size) { index ->
            val chapter = chapters[index]
            val selected = index == currentIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onJump(index) }
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else Color.Transparent
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${chapter.charCount} 字",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TypeSettingsSheet(
    settings: ReaderSettings,
    onFontSize: (Int) -> Unit,
    onLineHeight: (Float) -> Unit,
    onParagraphSpacing: (Float) -> Unit,
    onMargin: (Int) -> Unit,
    onFontFamily: (String) -> Unit,
    onTheme: (String) -> Unit,
    onIndent: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        Text("排版", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(14.dp))

        SheetSlider(
            label = "字号",
            valueText = "${settings.fontSize} sp",
            value = settings.fontSize.toFloat(),
            range = 12f..32f,
            steps = 19,
            onValueChange = { onFontSize(it.roundToInt()) }
        )
        SheetSlider(
            label = "行距",
            valueText = "%.2f 倍".format(settings.lineHeight),
            value = settings.lineHeight,
            range = 1.2f..2.2f,
            steps = 9,
            onValueChange = onLineHeight
        )
        SheetSlider(
            label = "段间距",
            valueText = "%.1f 倍".format(settings.paragraphSpacing),
            value = settings.paragraphSpacing,
            range = 0f..1.5f,
            steps = 14,
            onValueChange = onParagraphSpacing
        )

        Spacer(Modifier.height(10.dp))
        Text("页边距", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        ChipRow(
            options = listOf("窄" to 0, "标准" to 1, "宽" to 2),
            isSelected = { settings.margin == it },
            labelOf = { it.toString() },
            onSelect = onMargin
        )

        Spacer(Modifier.height(12.dp))
        Text("字体", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        ChipRow(
            options = listOf(
                "霞鹜文楷" to ReaderSettings.FONT_LXGW,
                "宋体" to ReaderSettings.FONT_SERIF,
                "黑体" to ReaderSettings.FONT_SANS
            ),
            isSelected = { settings.fontFamily == it },
            labelOf = { it },
            onSelect = onFontFamily
        )

        Spacer(Modifier.height(12.dp))
        Text("主题", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        ChipRow(
            options = listOf(
                "米白" to ReaderSettings.THEME_PAPER,
                "墨灰" to ReaderSettings.THEME_DARK,
                "豆沙绿" to ReaderSettings.THEME_GREEN,
                "羊皮纸" to ReaderSettings.THEME_PARCHMENT
            ),
            isSelected = { settings.theme == it },
            labelOf = { it },
            onSelect = onTheme
        )

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("首行缩进", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "古籍排版习惯缩进两个字",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = settings.indent, onCheckedChange = onIndent)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("阅读时常亮", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "仅在阅读页面生效",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = settings.keepScreenOn, onCheckedChange = onKeepScreenOn)
        }
    }
}

@Composable
private fun SheetSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}

@Composable
private fun <T> ChipRow(
    options: List<Pair<String, T>>,
    isSelected: (T) -> Boolean,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            FilterChip(
                selected = isSelected(value),
                onClick = { onSelect(value) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

// ==================================================================
//  制卡面板
// ==================================================================

@Composable
private fun MakeCardSheet(
    draft: CardDraft,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var front by remember(draft) { mutableStateOf(draft.clozeText ?: draft.front) }
    var back by remember(draft) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "原文制卡",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            TagPill(text = "来自古籍")
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${draft.sourceBookTitle} · ${draft.sourceChapterTitle}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        if (draft.clozeText != null && draft.clozeText != draft.front) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "已自动挖空方剂名，可点下方按钮恢复原文",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = draft.clozeText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = front,
            onValueChange = { front = it },
            label = { Text("正面（问题）") },
            minLines = 3,
            maxLines = 6,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = back,
            onValueChange = { back = it },
            label = { Text("背面（答案）") },
            minLines = 2,
            maxLines = 5,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(
                onClick = { onSave(front, back) },
                enabled = front.isNotBlank(),
                modifier = Modifier
                    .weight(1.6f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("加入背诵队列") }
        }
    }
}
