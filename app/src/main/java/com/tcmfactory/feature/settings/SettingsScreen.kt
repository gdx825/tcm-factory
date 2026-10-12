package com.tcmfactory.feature.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tcmfactory.core.datastore.SettingsRepository
import com.tcmfactory.core.datastore.UserSettings
import com.tcmfactory.data.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val settings: StateFlow<UserSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun setNewPerDay(v: Int) = viewModelScope.launch { repository.setNewPerDay(v) }
    fun setReviewPerDay(v: Int) = viewModelScope.launch { repository.setReviewPerDay(v) }
    fun setDayBoundaryHour(v: Int) = viewModelScope.launch { repository.setDayBoundaryHour(v) }
    fun setLapseStep(v: Int) = viewModelScope.launch { repository.setLapseStep(v) }
    fun setFourRatings(v: Boolean) = viewModelScope.launch { repository.setEnableFourRatings(v) }
    fun setAutoReveal(v: Boolean) = viewModelScope.launch { repository.setAutoReveal(v) }
    fun setThemeMode(v: String) = viewModelScope.launch { repository.setThemeMode(v) }

    /**
     * 开关提醒时**立即重新调度**，而不是等下次冷启动才生效。
     *
     * 「刚打开开关却要重开 App 才有效」是这类功能最常见的差评来源 ——
     * 用户会直接判定为功能坏了，而不会想到去重启。
     */
    fun setReminderEnabled(v: Boolean) = viewModelScope.launch {
        repository.setReminderEnabled(v)
        val hour = settings.value.reminderHour
        if (v && hour in 0..23) {
            WorkScheduler.scheduleReminder(context, hour)
        } else {
            WorkScheduler.cancelReminder(context)
        }
    }

    fun setReminderHour(v: Int) = viewModelScope.launch {
        repository.setReminderHour(v)
        if (settings.value.reminderEnabled && v in 0..23) {
            WorkScheduler.scheduleReminder(context, v)
        }
    }
}

@Composable
fun SettingsScreen(
    onOpenKnowledge: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 授权结果不改变开关状态：无论是否授权都保存用户的选择，
    // 未授权时通知会静默失败，用户仍可在系统设置里补授权限。
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { viewModel.setReminderEnabled(true) }

    // 组合作用域之外（onCheckedChange 回调里）不能调用 Composable 函数，故提前算好
    val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp)) {
                Text(
                    text = "我的",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "学习参数与外观设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---------------- 背诵计划 ----------------
        item { SettingsGroup(title = "背诵计划") {
            SliderRow(
                title = "每日新卡上限",
                value = settings.newPerDay,
                valueText = if (settings.newPerDay <= 0) "不限制" else "${settings.newPerDay} 张",
                range = 0f..100f,
                steps = 19,
                onValueChange = { viewModel.setNewPerDay(it.roundToInt()) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SliderRow(
                title = "每日复习上限",
                value = settings.reviewPerDay,
                valueText = "${settings.reviewPerDay} 张",
                range = 10f..500f,
                steps = 48,
                onValueChange = { viewModel.setReviewPerDay(it.roundToInt()) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SliderRow(
                title = "学习日边界",
                value = settings.dayBoundaryHour,
                valueText = String.format("%02d:00", settings.dayBoundaryHour),
                range = 0f..6f,
                steps = 5,
                subtitle = "熬夜到凌晨学的仍算作前一天",
                onValueChange = { viewModel.setDayBoundaryHour(it.roundToInt()) }
            )
        } }

        // ---------------- 遗忘曲线 ----------------
        item { SettingsGroup(title = "遗忘曲线") {
            SliderRow(
                title = "未记住时的回退档数",
                value = settings.lapseStep,
                valueText = "${settings.lapseStep} 档",
                range = 1f..8f,
                steps = 6,
                subtitle = "回退越多越保守。回退后立即 5 分钟重学",
                onValueChange = { viewModel.setLapseStep(it.roundToInt()) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow(
                title = "四档精细评分",
                subtitle = "关闭时只有「未记住 / 已记住」两档滑动",
                checked = settings.enableFourRatings,
                onCheckedChange = viewModel::setFourRatings
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow(
                title = "卡片自动显示答案",
                subtitle = "进入卡片即直接显示背面",
                checked = settings.autoReveal,
                onCheckedChange = viewModel::setAutoReveal
            )
        } }

        // ---------------- 外观 ----------------
        item { SettingsGroup(title = "外观") {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    text = "主题",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip("跟随系统", UserSettings.THEME_SYSTEM, settings.themeMode, viewModel::setThemeMode)
                    ThemeChip("浅色", UserSettings.THEME_LIGHT, settings.themeMode, viewModel::setThemeMode)
                    ThemeChip("深色", UserSettings.THEME_DARK, settings.themeMode, viewModel::setThemeMode)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip("护眼", UserSettings.THEME_EYE_CARE, settings.themeMode, viewModel::setThemeMode)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SwitchRow(
                title = "每日复习提醒",
                subtitle = if (settings.reminderEnabled) {
                    "每天 ${"%02d".format(settings.reminderHour)}:00 检查，仅在有到期卡片时通知"
                } else {
                    "已关闭"
                },
                checked = settings.reminderEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && needsNotificationPermission) {
                        // 先申请权限再落设置：否则用户会以为开了，实际什么都收不到
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setReminderEnabled(enabled)
                    }
                }
            )
            if (settings.reminderEnabled) {
                SliderRow(
                    title = "提醒时间",
                    value = settings.reminderHour,
                    valueText = "%02d:00".format(settings.reminderHour),
                    range = 5f..23f,
                    steps = 17,
                    onValueChange = { viewModel.setReminderHour(it.roundToInt()) },
                    subtitle = "固定在每天的复习时段，更容易养成习惯"
                )
            }
        } }

        // ---------------- 数据来源 ----------------
        item {
            SettingsGroup(title = "数据来源与免责声明") {
                Text(
                    text = """
                        内置知识库数据来自公开权威渠道：

                        · 中药性味功效 —— 《中国药典》2025 年版一部（2025-10-01 施行）
                        · 腧穴定位 —— GB/T 12346-2021《经穴名称与定位》（2021-11-26 实施）
                        · 方剂组成 —— 中国中医药出版社规划教材《方剂学》
                        · 经典条文 —— 公有领域古籍原文（《伤寒论》《黄帝内经》等）

                        每条卡片均标注出处（sourceRef）以便溯源校对。数据仅供学习参考，
                        不作为临床诊疗依据；如有疑义，请以最新版教材与国家标准为准。

                        用户自行导入的内容，其版权与准确性由导入者负责。
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }

        // ---------------- 学习 ----------------
        item {
            SettingsGroup(title = "学习") {
                NavRow(
                    title = "学习统计",
                    subtitle = "连续学习天数 · 学习热力图 · 近 30 天趋势",
                    onClick = onOpenStats
                )
            }
        }

        // ---------------- 知识库 ----------------
        item {
            SettingsGroup(title = "知识库") {
                NavRow(
                    title = "知识库更新",
                    subtitle = "查看已安装知识包 · 检查联网更新 · 配置数据源",
                    onClick = onOpenKnowledge
                )
            }
        }

        // ---------------- 关于 ----------------
        item {
            SettingsGroup(title = "关于") {
                Text(
                    // 从构建产物读取，避免每次发版遗漏这处硬编码
                    text = "中医工厂　v${com.tcmfactory.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "开发者：厂吴",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "面向中医学专业学生的考研与执医备考工具。\n" +
                        "核心功能完全离线可用；知识库更新为可选的联网增强。所有学习数据仅保存在本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun NavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.size(12.dp))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Int,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    subtitle: String? = null
) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat().coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun ThemeChip(
    label: String,
    value: String,
    current: String,
    onSelect: (String) -> Unit
) {
    FilterChip(
        selected = current == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}
