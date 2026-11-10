package com.daybook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daybook.app.Notice

/** Текст нужного стиля и цвета. */
@Composable
fun Txt(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    BasicText(
        text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = align ?: TextAlign.Unspecified),
        maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

/** Квадратная кнопка со значком (поиск, настройки, назад). */
@Composable
fun SquareButton(icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true, tint: Color? = null) {
    val p = LocalPalette.current
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(p.plate)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, if (enabled) tint ?: p.text else p.dim) }
}

/** Ярлычок: «сегодня», тег. В тёмной теме — оранжевые буквы, в светлой — оранжевая плашка. */
@Composable
fun Chip(text: String) {
    val p = LocalPalette.current
    val filled = p.chipBg != Color.Transparent
    Txt(
        text, Type.meta.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), p.chipText,
        modifier = if (filled) {
            Modifier.clip(RoundedCornerShape(6.dp)).background(p.chipBg).padding(horizontal = 6.dp, vertical = 1.dp)
        } else Modifier,
    )
}

/** Подпись раздела: «НОЯБРЬ 2026», «ТЕМА». */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier) {
    Txt(text.uppercase(), Type.month, LocalPalette.current.muted, modifier)
}

/** Большая оранжевая кнопка. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(p.button).clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, Type.labelBold, p.onButton) }
}

/** Спокойная кнопка на плашке. */
@Composable
fun PlateButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
    center: Boolean = false,
) {
    val p = LocalPalette.current
    Box(
        modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(p.plate).clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = if (center) Alignment.Center else Alignment.CenterStart,
    ) { Txt(text, Type.labelBold, color ?: p.text, maxLines = 1) }
}

/** Переключатель «вкл/выкл». */
@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.width(50.dp).height(30.dp).clip(CircleShape).background(if (on) p.button else p.track)
            .border(1.dp, if (on) p.button else p.line, CircleShape)
            .clickable { onChange(!on) }
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(24.dp).clip(CircleShape).background(if (on) p.onButton else p.muted)) }
}

/** Строка настроек: название и пояснение слева, переключатель справа. */
@Composable
fun ToggleRow(title: String, hint: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.plate)
            .clickable { onChange(!on) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(title, Type.labelBold, p.text)
            if (hint != null) {
                Spacer(Modifier.height(3.dp))
                Txt(hint, Type.meta, p.muted)
            }
        }
        Spacer(Modifier.width(12.dp))
        Toggle(on, onChange)
    }
}

/** Переключатель из нескольких вариантов (языки, тема). */
@Composable
fun <T> Segments(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    height: Dp = 52.dp,
    style: TextStyle = Type.seg,
) {
    val p = LocalPalette.current
    Row(
        modifier.height(height).clip(RoundedCornerShape(18.dp)).background(p.plate).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (item in items) {
            val on = item == selected
            Box(
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(14.dp))
                    .then(if (on) Modifier.background(p.track).border(1.5.dp, p.button, RoundedCornerShape(14.dp)) else Modifier)
                    .clickable(enabled = !locked) { onSelect(item) },
                contentAlignment = Alignment.Center,
            ) {
                Txt(label(item), style, if (on) p.text else if (locked) p.dim else p.muted, maxLines = 1)
            }
        }
    }
}

/** Окно с сообщением или вопросом поверх экрана. */
@Composable
fun NoticeOverlay(notice: Notice, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.open)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
        ) {
            Txt(notice.text, Type.entry, p.text)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (notice.action != null) {
                    PlateButton("Отмена", onDismiss, Modifier.weight(1f), center = true)
                    PrimaryButton(notice.action, {
                        onDismiss()
                        notice.onAction?.invoke()
                    }, Modifier.weight(1f))
                } else {
                    PrimaryButton("Понятно", onDismiss, Modifier.weight(1f))
                }
            }
        }
    }
}
