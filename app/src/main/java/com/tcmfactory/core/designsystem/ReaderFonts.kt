package com.tcmfactory.core.designsystem

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.tcmfactory.R

/**
 * 内置古籍字体：**霞鹜文楷**（LXGW WenKai TC）。
 *
 * 授权：SIL Open Font License 1.1 —— 允许免费商用、允许嵌入分发，无版权风险。
 *
 * 体积控制：完整字库 22,401 字符 / 12.5MB，直接打进 APK 不可接受。
 * 这里使用的是**子集化**版本：按 GB2312 全集（6763 汉字）+ ASCII + 中文排印标点
 * + 中医古籍专用生僻字裁剪到 7,593 字符，体积 3.9MB（压缩 68%）。
 * 裁剪脚本见 `app/tools/subset_font.py`，可复现。
 *
 * 缺字兜底：子集外的生僻字会由 Android 系统字体 fallback 链接管，
 * 显示为系统字形而非豆腐块，可读性不受影响。
 *
 * 为什么选 TC（繁体）版本：Google Fonts 只收录了 TC 版；
 * 且中医古籍本身多为繁体，TC 字形反而更贴合阅读场景。
 */
val LxgwWenKai: FontFamily = FontFamily(
    Font(R.font.lxgw_wenkai, FontWeight.Normal)
)
