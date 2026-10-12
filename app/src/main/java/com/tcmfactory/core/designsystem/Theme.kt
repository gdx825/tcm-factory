package com.tcmfactory.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tcmfactory.core.datastore.UserSettings

/**
 * 三套色板。共同原则：
 *  · 绝不使用纯白 #FFFFFF 大面积铺底，也绝不用纯黑 #000000 做正文色。
 *    长时间阅读场景下，高对比度是疲劳的主因。
 *  · 语义色（记住 / 忘记）在中国学生认知里用「青绿＝掌握、赭红＝未掌握」，
 *    与股市红涨绿跌的约定无关，此处以「警示＝红」的通用语义为准。
 */

// ---------------- 米白纸感（日间默认） ----------------
val LightScheme = lightColorScheme(
    primary = Color(0xFF2F6F62),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E7E1),
    onPrimaryContainer = Color(0xFF11302B),
    secondary = Color(0xFF4A7C59),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E8DC),
    onSecondaryContainer = Color(0xFF1B3323),
    tertiary = Color(0xFFB4803A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3E3C9),
    onTertiaryContainer = Color(0xFF4A3413),
    error = Color(0xFFC1584A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF7DAD5),
    onErrorContainer = Color(0xFF5C2018),
    background = Color(0xFFFAF8F3),
    onBackground = Color(0xFF2B2A28),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2B2A28),
    surfaceVariant = Color(0xFFF0EDE6),
    onSurfaceVariant = Color(0xFF6B6862),
    outline = Color(0xFFD8D4CB),
    outlineVariant = Color(0xFFE8E4DB)
)

// ---------------- 墨灰（夜间默认） ----------------
val DarkScheme = darkColorScheme(
    primary = Color(0xFF6FA97E),
    onPrimary = Color(0xFF10241A),
    primaryContainer = Color(0xFF2A4A3C),
    onPrimaryContainer = Color(0xFFCDE8D6),
    secondary = Color(0xFF8FB89A),
    onSecondary = Color(0xFF12231A),
    secondaryContainer = Color(0xFF2E4438),
    onSecondaryContainer = Color(0xFFD3E6D9),
    tertiary = Color(0xFFD4A96C),
    onTertiary = Color(0xFF3A2A10),
    tertiaryContainer = Color(0xFF523E1F),
    onTertiaryContainer = Color(0xFFF3E2C6),
    error = Color(0xFFD97B6B),
    onError = Color(0xFF3D150F),
    errorContainer = Color(0xFF5C241B),
    onErrorContainer = Color(0xFFF7DAD5),
    background = Color(0xFF1C1B19),
    onBackground = Color(0xFFE8E4DA),
    surface = Color(0xFF262522),
    onSurface = Color(0xFFE8E4DA),
    surfaceVariant = Color(0xFF33312D),
    onSurfaceVariant = Color(0xFFB5B0A5),
    outline = Color(0xFF4A4740),
    outlineVariant = Color(0xFF38352F)
)

// ---------------- 豆沙绿（护眼） ----------------
val EyeCareScheme = lightColorScheme(
    primary = Color(0xFF3E6B4A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBCCDAE),
    onPrimaryContainer = Color(0xFF1E2E1F),
    secondary = Color(0xFF556B45),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC6D2B3),
    onSecondaryContainer = Color(0xFF232C1C),
    tertiary = Color(0xFF8A6A33),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE2D3B0),
    onTertiaryContainer = Color(0xFF3C2E12),
    error = Color(0xFFA84B3E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFE7C7C1),
    onErrorContainer = Color(0xFF4C1A13),
    background = Color(0xFFC7D2B5),
    onBackground = Color(0xFF2E3328),
    surface = Color(0xFFD6DEC6),
    onSurface = Color(0xFF2E3328),
    surfaceVariant = Color(0xFFBFCBAE),
    onSurfaceVariant = Color(0xFF4E5643),
    outline = Color(0xFF9AA88A),
    outlineVariant = Color(0xFFAFBC9E)
)

/** 语义色：记住 / 忘记。集中定义，避免各页面各写一套。 */
object TcmSemanticColors {
    val rememberLight = Color(0xFF4A7C59)
    val forgetLight = Color(0xFFC1584A)
    val rememberDark = Color(0xFF6FA97E)
    val forgetDark = Color(0xFFD97B6B)
}

/**
 * 排版。中文正文的行高需要比 Material 默认值更宽松，
 * 否则密排的汉字会显得拥挤。
 */
val TcmTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,
        fontSize = 40.sp, lineHeight = 48.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp, lineHeight = 34.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 26.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 22.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 10.sp, lineHeight = 14.sp
    )
)

@Composable
fun TcmFactoryTheme(
    themeMode: String = UserSettings.THEME_SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        UserSettings.THEME_DARK -> true
        UserSettings.THEME_LIGHT -> false
        UserSettings.THEME_EYE_CARE -> false
        else -> isSystemInDarkTheme()
    }
    val scheme = when {
        themeMode == UserSettings.THEME_EYE_CARE -> EyeCareScheme
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = TcmTypography,
        content = content
    )
}

/** 当前是否处于深色语境，供个别需要手写颜色的地方判断。 */
@Composable
fun isDarkTheme(themeMode: String): Boolean = when (themeMode) {
    UserSettings.THEME_DARK -> true
    UserSettings.THEME_LIGHT, UserSettings.THEME_EYE_CARE -> false
    else -> isSystemInDarkTheme()
}
