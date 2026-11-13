package com.daybook.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.daybook.app.R

/**
 * Цвета утверждённого дизайна (docs/PLAN.md, раздел «Дизайн», версия 8).
 * В светлой теме яркий жёлто-оранжевый — только заливка (кнопка, ярлычки):
 * цветом букв на белом он становится «грязным».
 */
@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val plate: Color,
    val open: Color,
    val track: Color,
    val line: Color,
    val text: Color,
    val soft: Color,
    val muted: Color,
    val dim: Color,
    /** Цвет букв времени и тегов. */
    val accentText: Color,
    /** Фон ярлычков («сегодня», теги); прозрачный — значит, просто цветные буквы. */
    val chipBg: Color,
    val chipText: Color,
    val button: Color,
    val onButton: Color,
    val rec: Color,
    val wet: Color,
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF0C0C0E),
    plate = Color(0xFF1D1D21),
    open = Color(0xFF26262B),
    track = Color(0xFF313136),
    line = Color(0xFF3A3A40),
    text = Color(0xFFFFFCF6),
    soft = Color(0xFFFFFAF1),
    muted = Color(0xFFB4B4BA),
    dim = Color(0xFF66666C),
    accentText = Color(0xFFFFA01A),
    chipBg = Color.Transparent,
    chipText = Color(0xFFFFA01A),
    button = Color(0xFFFFA01A),
    onButton = Color(0xFF1F1200),
    rec = Color(0xFFFF3B30),
    wet = Color(0xFFB4B4BA),
)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFFFFFFF),
    plate = Color(0xFFF2F2F3),
    open = Color(0xFFF2F2F3),
    track = Color(0xFFFFFFFF),
    line = Color(0xFFE4E4E7),
    text = Color(0xFF111113),
    soft = Color(0xFF1D1D20),
    muted = Color(0xFF5F5F66),
    dim = Color(0xFFA9A9B0),
    accentText = Color(0xFF111113),
    chipBg = Color(0xFFFFA01A),
    chipText = Color(0xFF1F1200),
    button = Color(0xFFFFA01A),
    onButton = Color(0xFF1F1200),
    rec = Color(0xFFD93A2F),
    wet = Color(0xFF8A8A92),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

object Fonts {
    /** Заголовок, время, числа, кнопки языков. */
    val Unbounded = FontFamily(Font(R.font.unbounded_700, FontWeight.Bold))

    /** Мелкие подписи: дата, «4 записи», месяцы. */
    val Manrope = FontFamily(
        Font(R.font.manrope_600, FontWeight.SemiBold),
        Font(R.font.manrope_700, FontWeight.Bold),
    )

    /** Текст записей (Montserrat — выбор владельца 13.11.2026). */
    val Body = FontFamily(
        Font(R.font.montserrat_400, FontWeight.Normal),
        Font(R.font.montserrat_500, FontWeight.Medium),
    )
}

/** Размеры текста из макета. */
object Type {
    val title = TextStyle(fontFamily = Fonts.Unbounded, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.3).sp)
    val screenTitle = TextStyle(fontFamily = Fonts.Unbounded, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.2).sp)
    val month = TextStyle(fontFamily = Fonts.Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.3.sp)
    /** «Вт 11.11.2026» в плашке дня — чуть крупнее остальной строки. */
    val dayDate = TextStyle(fontFamily = Fonts.Manrope, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, lineHeight = 17.sp, letterSpacing = 0.1.sp)
    val meta = TextStyle(fontFamily = Fonts.Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)
    val label = TextStyle(fontFamily = Fonts.Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp)
    val labelBold = TextStyle(fontFamily = Fonts.Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp)
    val preview = TextStyle(fontFamily = Fonts.Body, fontSize = 17.sp, lineHeight = 21.sp, letterSpacing = (-0.015).em)
    val entry = TextStyle(fontFamily = Fonts.Body, fontSize = 17.5.sp, lineHeight = 23.sp)
    val time = TextStyle(fontFamily = Fonts.Unbounded, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
    val tag = TextStyle(fontFamily = Fonts.Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    val seg = TextStyle(fontFamily = Fonts.Unbounded, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
    val timer = TextStyle(fontFamily = Fonts.Unbounded, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    val pinDigit = TextStyle(fontFamily = Fonts.Unbounded, fontWeight = FontWeight.Bold, fontSize = 26.sp)
}

@Composable
fun DayBookTheme(palette: Palette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides palette, content = content)
}
