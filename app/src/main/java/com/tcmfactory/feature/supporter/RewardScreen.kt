package com.tcmfactory.feature.supporter

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcmfactory.R
import com.tcmfactory.core.datastore.SupporterStatus
import com.tcmfactory.core.designsystem.MarkdownText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「支持开发」页 —— 打赏入口与支持者徽标。
 *
 * ## 设计上的克制
 *
 * 这类页面最容易让人反感，所以做了三处克制：
 *
 * 1. **入口只在设置页与名单页底部**，不做启动弹窗、不做"使用 N 次后提醒"。
 * 2. **文案先讲清免费，再提打赏**。开篇第一句就是"完全免费、没有广告、不收集数据"，
 *    而不是"开发不易，请我喝杯咖啡"。
 * 3. **支持者码说明里明确写"不影响任何功能"**，避免用户误以为不打赏会被限制 ——
 *    一旦给人这种印象，开源项目的口碑就毁了。
 *
 * ## 为什么要"叠甲"叠到这个程度
 *
 * 有两件事必须说在收款码**前面**，而不是藏在条款里：
 *
 * - **打赏的用途**：只说"支持开发"不够，还要说清它**不购买任何东西**。
 *   否则用户付款后若对功能或内容不满，主观上会认为"我付了钱"。
 * - **内容的边界**：这个软件可以**导入第三方题库**，也能从用户自配的数据源取内容。
 *   用户在「支持开发」页看到二维码，很容易理解成"在为这些内容付费"。
 *   可那些内容既不属于开发者，也未必有再分发许可 —— 这句话必须写出来。
 *
 * 依据是[免责条款](com.tcmfactory.core.legal.LegalTexts)第四条与第五条，
 * 那两条是正式表述，这里是把结论前置到用户真正看得见的位置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardScreen(
    onBack: () -> Unit,
    onOpenSupporterList: () -> Unit,
    viewModel: RewardViewModel = hiltViewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "支持开发",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ---------------- 已支持：显示感谢 ----------------
            if (status.isSupporter) {
                item { SupporterBadgeCard(status) }
            }

            // ---------------- 主说明 ----------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "支持一下开发者",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "这个软件完全免费，没有广告，也不收集你的任何数据。" +
                                "它由一个人利用业余时间维护。\n\n" +
                                "如果它帮到了你，可以请我喝杯茶 —— 你的支持只会用在" +
                                "这个软件的开发与维护上，这会让我更有动力把它继续做下去。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // ---------------- 打赏的性质与用途（放在收款码之前，而不是之后）----------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        MarkdownText(
                            text = "**打赏完全出于自愿，且仅用于支持这个软件的开发与维护。**\n\n" +
                                "· 不打赏也能使用全部功能，没有任何区别\n" +
                                "· 不解锁任何额外功能，也不购买任何内容或服务\n" +
                                "· 打赏金额不对应任何内容的数量、范围或期限",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---------------- 内容边界：题库等第三方内容与打赏无关 ----------------
            // 这一条是为"这个软件能导入第三方题库"专门写的。用户看到「支持开发」
            // 很容易理解成"在支持/购买这些内容"，而内容根本不归开发者所有 ——
            // 说清这件事，既是对用户负责，也是对内容权利人负责。
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        MarkdownText(
                            text = "**题库、古籍等内容不是打赏的对象，也与本软件的开发支持无关。**\n\n" +
                                "软件内的学习内容，有的是随软件内置的公开资料与自编条目，" +
                                "有的可以由你**自行导入**（例如第三方题库文件），" +
                                "也可以从**你自己配置的数据源**获取 —— 这些内容由各自的提供方负责。\n\n" +
                                "**打赏不包含任何内容授权。** 无论是否打赏，你能获取的内容完全相同。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---------------- 收款码 ----------------
            item { PaymentCodeCard(R.drawable.reward_alipay, "支付宝") }
            item { PaymentCodeCard(R.drawable.reward_wechat, "微信") }

            // ---------------- 支持者码 ----------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "感谢徽标",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        MarkdownText(
                            text = "打赏之后你会收到一串**支持者码**。输入它可以点亮一枚徽标，" +
                                "仅作纪念 —— **它不会解锁任何功能，也没有功能需要解锁。**",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))

                        if (status.isSupporter) {
                            Text(
                                text = "已点亮 · 支持者编号 ${status.serial ?: "-"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            OutlinedTextField(
                                value = ui.input,
                                onValueChange = viewModel::onInputChange,
                                label = { Text("支持者码") },
                                placeholder = { Text("TCMF1…") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4,
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.pasteAndSubmit(clipboard.getText()?.text) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("从剪贴板粘贴")
                                }
                                OutlinedButton(
                                    onClick = viewModel::submit,
                                    enabled = ui.canSubmit && !ui.submitting,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if (ui.submitting) "确认中…" else "确认")
                                }
                            }
                        }

                        ui.message?.let { msg ->
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ui.isError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }
            }

            // ---------------- 名单入口 ----------------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "支持者名单",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "感谢愿意公开留名的每一位",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onOpenSupporterList) { Text("查看") }
                    }
                }
            }

            // ---------------- 页脚声明 ----------------
            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "打赏属于自愿赠与：不构成购买行为，不购买任何内容或服务，" +
                        "也不包含任何内容授权；原则上不支持退款。" +
                        "如果这让你犹豫，请不要打赏 —— 你正常使用它，就是对我最大的支持。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.labelSmall.lineHeight * 1.4f
                )
            }
        }
    }
}

@Composable
private fun SupporterBadgeCard(status: SupporterStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "谢谢你的支持",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append("支持者编号 ${status.serial ?: "-"}")
                        if (status.since > 0) {
                            append(" · ")
                            append(
                                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                    .format(Date(status.since))
                            )
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun PaymentCodeCard(drawableRes: Int, label: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            // 容器固定用白色：二维码必须落在白底上才扫得出来，
            // 用主题色会在深色模式下变成深底浅码，直接扫不动。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(drawableRes),
                    contentDescription = "$label 收款码",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "保存图片后，用$label 扫码\n仅用于支持软件开发与维护",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.labelSmall.lineHeight * 1.4f
            )
        }
    }
}
