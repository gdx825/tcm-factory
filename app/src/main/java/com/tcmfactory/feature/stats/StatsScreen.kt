package com.tcmfactory.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("学习统计") },
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
            item { SummaryRow(state) }
            item { HeatmapCard(state.days) }
            item { TrendCard(state) }
            item { FooterNote(state) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ==================================================================
//  汇总
// ==================================================================

@Composable
private fun SummaryRow(state: StatsUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(
            modifier = Modifier.weight(1f),
            value = "${state.streakDays}",
            unit = "天",
            label = "连续学习",
            highlight = state.streakDays > 0
        )
        StatTile(
            modifier = Modifier.weight(1f),
            value = "${state.activeDays}",
            unit = "天",
            label = "有学习记录"
        )
        StatTile(
            modifier = Modifier.weight(1f),
            value = "${state.totalCards}",
            unit = "张",
            label = "累计卡片"
        )
    }
}

@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    value: String,
    unit: String,
    label: String,
    highlight: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (highlight) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================================================================
//  热力图
// ==================================================================

@Composable
private fun HeatmapCard(days: List<DayStat>) {
    val primary = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val gap = 3.dp

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "学习热力图",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "近 ${days.size} 天，每格一天",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            if (days.isEmpty()) {
                Text(
                    text = "暂无数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            // 前置空格数：让第一列从周一开始对齐
            val pad = days.first().date.dayOfWeek.value - 1
            val weeks = ceil((pad + days.size) / 7.0).toInt()

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cellW = ((maxWidth - gap * (weeks - 1)) / weeks).coerceAtLeast(2.dp)
                val cellH = cellW

                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(cellH * 7 + gap * 6)
                ) {
                    val cw = cellW.toPx()
                    val ch = cellH.toPx()
                    val g = gap.toPx()
                    val radius = CornerRadius(2.dp.toPx(), 2.dp.toPx())

                    for (week in 0 until weeks) {
                        for (dow in 0 until 7) {
                            val index = week * 7 + dow - pad
                            val day = days.getOrNull(index) ?: continue
                            val color = if (!day.hasActivity) {
                                emptyColor
                            } else {
                                primary.copy(alpha = alphaFor(day.totalCards))
                            }
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(week * (cw + g), dow * (ch + g)),
                                size = Size(cw, ch),
                                cornerRadius = radius
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "少",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                listOf(0.12f, 0.30f, 0.50f, 0.72f, 1f).forEach { alpha ->
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(
                                color = primary.copy(alpha = alpha),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "多",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun alphaFor(total: Int): Float = when {
    total <= 0 -> 0.12f
    total < 10 -> 0.30f
    total < 30 -> 0.50f
    total < 60 -> 0.72f
    else -> 1f
}

// ==================================================================
//  近 30 天趋势
// ==================================================================

@Composable
private fun TrendCard(state: StatsUiState) {
    val primary = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val max = state.recentMax

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "近 30 天学习量",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "每日新学 + 复习卡片数，峰值 $max 张",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                state.recent.forEach { day ->
                    val ratio = day.totalCards.toFloat() / max
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(ratio.coerceIn(0.03f, 1f))
                            .background(
                                color = if (day.totalCards > 0) primary else emptyColor,
                                shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                            )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "30 天前",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "今天",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==================================================================
//  其他
// ==================================================================

@Composable
private fun FooterNote(state: StatsUiState) {
    Column {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "累计概览",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                DetailRow("新学卡片", "${state.totalNew} 张")
                DetailRow("复习卡片", "${state.totalReview} 张")
                DetailRow("累计用时", formatMinutes(state.totalMinutes))
                DetailRow(
                    "答题正确率",
                    state.quizAccuracy?.let { "%.1f%%".format(it * 100) } ?: "暂无记录"
                )
                DetailRow("累计答题", "${state.totalQuiz} 题")
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "统计按学习日计算（默认凌晨 04:00 为日界，可在设置中调整）。" +
                "换设备后本机统计不会同步。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatMinutes(minutes: Int): String = when {
    minutes <= 0 -> "暂无记录"
    minutes < 60 -> "$minutes 分钟"
    else -> "%d 小时 %d 分钟".format(minutes / 60, minutes % 60)
}
