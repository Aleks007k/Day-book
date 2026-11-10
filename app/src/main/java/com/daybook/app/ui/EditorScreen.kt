package com.daybook.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.daybook.app.DiaryState
import com.daybook.app.Notice
import com.daybook.app.Screen
import com.daybook.app.data.Days
import com.daybook.app.speech.Lang
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Правка одной записи: текст, дата и время, удаление. Всё сохраняется сразу. */
@Composable
fun EditorScreen(state: DiaryState, entryId: Long) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val e = state.entry(entryId)
    if (e == null) {
        LaunchedEffect(Unit) { state.screen = Screen.Feed }
        return
    }
    val isLive = e.id == state.recordingId
    val zone = ZoneId.systemDefault()
    val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.createdAt), zone)
    val dialogTheme = if (p.dark) android.R.style.Theme_DeviceDefault_Dialog_Alert
    else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert

    fun pickDateTime() {
        DatePickerDialog(context, dialogTheme, { _, y, m, d ->
            TimePickerDialog(context, dialogTheme, { _, h, min ->
                val millis = LocalDateTime.of(y, m + 1, d, h, min).atZone(zone).toInstant().toEpochMilli()
                state.setTime(e.id, millis)
            }, dt.hour, dt.minute, true).show()
        }, dt.year, dt.monthValue - 1, dt.dayOfMonth).show()
    }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SquareButton(Icons.back, { state.screen = Screen.Feed })
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { pickDateTime() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Txt("%d:%02d".format(dt.hour, dt.minute), Type.screenTitle, p.text)
                Txt(
                    Days.dateText(dt.toLocalDate()) + " · " + Lang.of(e.lang).title + " · изменить время",
                    Type.meta, p.muted, maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            SquareButton(Icons.trash, {
                state.notice = Notice("Удалить эту запись? Вернуть её будет нельзя.", "Удалить") {
                    state.screen = Screen.Feed
                    state.delete(e.id)
                }
            }, enabled = !isLive)
        }
        Column(
            Modifier.weight(1f).padding(horizontal = 8.dp).fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)).background(p.open)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = e.text,
                onValueChange = { state.setText(e.id, it) },
                textStyle = Type.entry.copy(color = p.text),
                cursorBrush = SolidColor(p.button),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (e.text.isEmpty()) Txt("Текст записи", Type.entry, p.dim)
                        inner()
                    }
                },
            )
            if (isLive) {
                Spacer(Modifier.height(8.dp))
                if (state.partial.isNotBlank()) Txt(state.partial, Type.entry, p.wet)
                Spacer(Modifier.height(6.dp))
                Txt("Идёт запись — новые слова добавляются в конец.", Type.meta, p.muted)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
