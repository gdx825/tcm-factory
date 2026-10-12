package com.tcmfactory.feature.recite

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcmfactory.core.common.TimeFormat
import com.tcmfactory.core.designsystem.StatBlock
import com.tcmfactory.core.designsystem.TagPill
import com.tcmfactory.core.designsystem.ThinProgressBar
import com.tcmfactory.core.srs.SrsRating
import com.tcmfactory.domain.model.Card
import com.tcmfactory.domain.model.ReviewOutcome
import com.tcmfactory.domain.model.SessionStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun CardReviewScreen(
    onExit: () -> Unit,
    onOpenSource: (Long, Int) -> Unit = { _, _ -> },
    viewModel: CardReviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished && state.remembered + state.forgotten > 0) {
            viewModel.loadUpcomingCount()
        }
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "正在准备今日队列…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    if (state.finished) {
        SessionSummaryScreen(
            stats = viewModel.sessionStats(),
            emptyReason = state.emptyReason,
            onExit = onExit,
            onRestart = viewModel::reload
        )
    } else {
        ReviewSession(
            state = state,
            viewModel = viewModel,
            onExit = onExit,
            onOpenSource = onOpenSource
        )
    }
}

@Composable
private fun ReviewSession(
    state: ReviewUiState,
    viewModel: CardReviewViewModel,
    onExit: () -> Unit,
    onOpenSource: (Long, Int) -> Unit
) {
    val card = state.current ?: return

    var feedback by remember { mutableStateOf<ReviewOutcome?>(null) }
    LaunchedEffect(state.lastFeedback) {
        val outcome = state.lastFeedback ?: return@LaunchedEffect
        feedback = outcome
        delay(1500)
        feedback = null
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ---------- 顶部：退出 + 进度 + 撤销 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onExit) {
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
                        text = "剩余 ${state.remaining}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "记住 ${state.remembered} · 未记住 ${state.forgotten}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                Icon(
                    Icons.Outlined.Undo,
                    contentDescription = "撤销",
                    tint = if (state.canUndo) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
        }

        // ---------- 中部：卡片 ----------
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            SwipeCard(
                card = card,
                revealed = state.revealed,
                threshold = state.swipeThreshold,
                onTap = viewModel::reveal,
                onSwipe = viewModel::rate,
                onOpenSource = onOpenSource
            )
        }

        // ---------- 底部：反馈 + 操作 ----------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(min = 84.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedContent(
                targetState = feedback,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "feedback"
            ) { outcome ->
                if (outcome != null) {
                    FeedbackBubble {
                        Text(
                            text = if (outcome.rating == SrsRating.AGAIN) {
                                "未记住 · ${outcome.nextDueText}重新出现"
                            } else {
                                "已记住 · 下次复习：${outcome.nextDueText}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    BottomActions(
                        revealed = state.revealed,
                        fourRatings = state.fourRatings,
                        onReveal = viewModel::reveal,
                        onRate = viewModel::rate
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FeedbackBubble(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun BottomActions(
    revealed: Boolean,
    fourRatings: Boolean,
    onReveal: () -> Unit,
    onRate: (SrsRating) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!revealed) {
            Button(
                onClick = onReveal,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("显示答案", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "点击卡片也可翻面",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (fourRatings) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RatingButton("未记住", SrsRating.AGAIN, MaterialTheme.colorScheme.error, onRate, Modifier.weight(1f))
                RatingButton("模糊", SrsRating.HARD, MaterialTheme.colorScheme.tertiary, onRate, Modifier.weight(1f))
                RatingButton("记住", SrsRating.GOOD, MaterialTheme.colorScheme.primary, onRate, Modifier.weight(1f))
                RatingButton("熟练", SrsRating.EASY, MaterialTheme.colorScheme.secondary, onRate, Modifier.weight(1f))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "← 未记住",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "已记住 →",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "左右滑动卡片，或点下方按钮",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onRate(SrsRating.AGAIN) },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("未记住") }
                Button(
                    onClick = { onRate(SrsRating.GOOD) },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("已记住") }
            }
        }
    }
}

@Composable
private fun RatingButton(
    label: String,
    rating: SrsRating,
    color: Color,
    onRate: (SrsRating) -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { onRate(rating) },
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.14f),
            contentColor = color
        )
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

// ==================================================================
//  可滑动卡片
// ==================================================================

@Composable
private fun SwipeCard(
    card: Card,
    revealed: Boolean,
    threshold: Float,
    onTap: () -> Unit,
    onSwipe: (SrsRating) -> Unit,
    onOpenSource: (Long, Int) -> Unit
) {
    // 拖拽阶段用普通 State 即时更新（非 suspend 操作）。
    // 关键约束：AwaitPointerEventScope 是受限协程作用域，不能在其中调用
    // Animatable 的 suspend 方法，因此松手后的收尾动画统一交给外部 scope 驱动。
    var offset by remember(card.id) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val limitPx = constraints.maxWidth * threshold
        val widthPx = constraints.maxWidth.toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(card.id) {
                    detectTapGestures(onTap = { onTap() })
                }
                .pointerInput(card.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val current = offset
                            val limit = size.width.toFloat() * threshold
                            val travel = size.width.toFloat() * 1.6f
                            scope.launch {
                                when {
                                    current > limit -> {
                                        glide(current, travel) { offset = it }
                                        onSwipe(SrsRating.GOOD)
                                    }
                                    current < -limit -> {
                                        glide(current, -travel) { offset = it }
                                        onSwipe(SrsRating.AGAIN)
                                    }
                                    else -> glide(current, 0f) { offset = it }
                                }
                            }
                        },
                        onDragCancel = {
                            val current = offset
                            scope.launch { glide(current, 0f) { offset = it } }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offset += dragAmount
                        }
                    )
                }
        ) {
            // 左右滑动意图提示（不拦截触摸，仅作视觉反馈）
            Text(
                text = "未记住",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .graphicsLayer {
                        alpha = (-offset / limitPx).coerceIn(0f, 1f) * 0.9f
                    }
            )
            Text(
                text = "已记住",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .graphicsLayer {
                        alpha = (offset / limitPx).coerceIn(0f, 1f) * 0.9f
                    }
            )

            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = offset
                        rotationZ = (offset / widthPx) * 7f
                    },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                CardBody(card = card, revealed = revealed, onOpenSource = onOpenSource)
            }
        }
    }
}

/** 卡片收尾动画：从 from 平滑滑到 to，把中间值回吐给调用方。 */
private suspend fun glide(from: Float, to: Float, onUpdate: (Float) -> Unit) {
    animate(
        initialValue = from,
        targetValue = to,
        animationSpec = tween(durationMillis = 200)
    ) { value, _ -> onUpdate(value) }
}

@Composable
private fun CardBody(
    card: Card,
    revealed: Boolean,
    onOpenSource: (Long, Int) -> Unit
) {
    AnimatedContent(
        targetState = revealed,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        label = "cardFace"
    ) { showBack ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TagPill(
                    text = card.deckName.ifBlank { "卡片" },
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (showBack) "答案" else "问题",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            if (card.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    card.tags.take(3).forEach { tag ->
                        TagPill(text = tag)
                    }
                }
                Spacer(Modifier.height(16.dp))
            } else {
                Spacer(Modifier.height(12.dp))
            }

            if (!showBack) {
                // ---------- 正面 ----------
                Text(
                    text = card.front,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = MaterialTheme.typography.headlineMedium.lineHeight
                )
            } else {
                // ---------- 背面 ----------
                Text(
                    text = card.front,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = card.back,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!card.extra.isNullOrBlank()) {
                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = "鉴别 / 延伸",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = card.extra,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (!card.sourceRef.isNullOrBlank()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "出处：${card.sourceRef}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 古籍溯源闭环：从原文制出的卡片可一键跳回古籍对应位置
            val sourceBookId = card.sourceBookId
            if (sourceBookId != null) {
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = { onOpenSource(sourceBookId, card.sourceCharStart ?: -1) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(
                        text = "查看原文 →",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ==================================================================
//  会话完成页
// ==================================================================

@Composable
private fun SessionSummaryScreen(
    stats: SessionStats,
    emptyReason: String?,
    onExit: () -> Unit,
    onRestart: () -> Unit
) {
    val nothingDone = stats.reviewed == 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))

        Box(
            modifier = Modifier
                .size(84.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(26.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (nothingDone) "☕" else "✓",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = if (nothingDone) "当前没有待学内容" else "今日任务完成！",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        if (nothingDone) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = emptyReason ?: "明天会有新的复习任务等着你。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            Spacer(Modifier.height(28.dp))

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
                        StatBlock("${stats.reviewed}", "已复习")
                        StatBlock("${stats.remembered}", "已记住", valueColor = MaterialTheme.colorScheme.primary)
                        StatBlock("${stats.forgotten}", "未记住", valueColor = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatBlock(TimeFormat.duration(stats.elapsedMs), "总用时")
                        StatBlock("${stats.averageMsPerCard / 1000}s", "平均单卡")
                        StatBlock("${stats.accuracy}%", "记住率")
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "明日待复习 ${stats.nextDueCount} 张",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.weight(1f))

        if (stats.reviewed > 0) {
            Button(
                onClick = onRestart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("再来一组") }
            Spacer(Modifier.height(10.dp))
        }

        TextButton(onClick = onExit) {
            Text("返回首页")
        }
        Spacer(Modifier.height(12.dp))
    }
}
