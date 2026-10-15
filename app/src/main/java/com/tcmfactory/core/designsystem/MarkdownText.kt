package com.tcmfactory.core.designsystem

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * 极简的 Markdown 强调解析：只支持 `**粗体**`。
 *
 * ## 为什么需要它
 *
 * 免责条款的文本在 [com.tcmfactory.core.legal.LegalTexts] 里只写一份，
 * 同时供应用内展示与仓库 README 使用 —— README 是 Markdown，需要 `**` 标记。
 * 如果应用内不解析这些标记，用户就会看到字面的星号，观感很差；
 * 而为应用单独维护一份去掉标记的副本，又会让两处内容逐渐走偏。
 *
 * 所以这里只实现唯一用到的那一种语法，而不是引入一个完整的 Markdown 渲染库 ——
 * 条款文本不需要标题、列表、链接之外的任何能力，为它引入依赖并不划算。
 */
fun String.toBoldMarkdown(): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < this@toBoldMarkdown.length) {
        val start = this@toBoldMarkdown.indexOf("**", index)
        if (start < 0) {
            append(this@toBoldMarkdown.substring(index))
            break
        }
        val end = this@toBoldMarkdown.indexOf("**", start + 2)
        if (end < 0) {
            // 落单的 ** 当作普通文本，避免把后半段整段吞掉
            append(this@toBoldMarkdown.substring(index))
            break
        }
        append(this@toBoldMarkdown.substring(index, start))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
            append(this@toBoldMarkdown.substring(start + 2, end))
        }
        index = end + 2
    }
}

/** 渲染带 `**粗体**` 标记的段落。 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = text.toBoldMarkdown(),
        modifier = modifier,
        style = style,
        color = color
    )
}
