package com.daybook.app.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.daybook.app.DiaryState
import com.daybook.app.data.Day
import com.daybook.app.data.Days
import com.daybook.app.data.Entry
import com.daybook.app.data.FeedRow
import com.daybook.app.speech.Lang
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

private val DOCK_SPACE = 100.dp

/** Главный экран: лента дней, внизу языки и микрофон. */
@Composable
fun FeedScreen(state: DiaryState, onMic: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val rows = state.feed
    var choosing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val expandedKey = state.expanded?.let { "d-$it" }

    // Строка раскрытого дня «прилипает» к верху, если день прокручен вверх.
    val pinned by remember(expandedKey) {
        derivedStateOf {
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == expandedKey }
            info != null && info.offset < 0 && info.offset + info.size > 120
        }
    }

    // Во время записи держим конец сегодняшнего дня над кнопками.
    val live = state.recordingId?.let { state.entry(it) }
    LaunchedEffect(live?.text?.length, state.partial.length, state.recordingId) {
        if (state.recordingId == null) return@LaunchedEffect
        val key = "d-${state.recordingDate ?: state.today}"
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: run {
            val index = rows.indexOfFirst { it.key == key }
            if (index >= 0) listState.scrollToItem(index)
            return@LaunchedEffect
        }
        val limit = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.afterContentPadding
        val bottom = info.offset + info.size
        if (bottom > limit) listState.animateScrollBy((bottom - limit).toFloat())
    }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt("Day-book", Type.title, p.text, Modifier.weight(1f))
                SquareButton(Icons.plus, { choosing = true }, enabled = !state.recording)
                Spacer(Modifier.width(8.dp))
                SquareButton(Icons.settings, { state.screen = com.daybook.app.Screen.Settings }, enabled = !state.recording)
            }
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = DOCK_SPACE + 40.dp),
                ) {
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is FeedRow.Month -> Caption(
                                Days.monthTitle(row.year, row.month),
                                Modifier.padding(start = 18.dp, top = 16.dp, bottom = 8.dp),
                            )
                            is FeedRow.DayRow -> DayPlate(row.day, state)
                        }
                    }
                }
                if (pinned) {
                    state.expanded?.let { date ->
                        rows.firstOrNull { it.key == expandedKey }?.let { row ->
                            Box(
                                Modifier.padding(horizontal = 8.dp).fillMaxWidth()
                                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)).background(p.open),
                            ) { DayHeader((row as FeedRow.DayRow).day, state, open = true) { state.toggleDay(date) } }
                        }
                    }
                }
            }
        }
        // Мягкое затухание ленты над кнопками.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(28.dp).background(Brush.verticalGradient(listOf(p.bg.copy(alpha = 0f), p.bg))))
            Column(Modifier.fillMaxWidth().background(p.bg)) {
                state.target?.let { TargetBanner(it, state) }
                Dock(state, onMic, Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp))
            }
        }
        if (choosing) {
            DayChooser(
                state,
                onPick = { d ->
                    choosing = false
                    state.chooseTarget(d)
                    val i = state.feed.indexOfFirst { it.key == "d-$d" }
                    if (i >= 0) scope.launch { listState.animateScrollToItem(i) }
                },
                onOther = {
                    choosing = false
                    pickPastDate(context, p.dark, state.today) { d ->
                        state.chooseTarget(d)
                        val i = state.feed.indexOfFirst { it.key == "d-$d" }
                        if (i >= 0) scope.launch { listState.animateScrollToItem(i) }
                    }
                },
                onDismiss = { choosing = false },
            )
        }
    }
}

/** «Запись за 10.11.2026 · вчера  ✕» — микрофон пишет в этот прошлый день. */
@Composable
private fun TargetBanner(date: LocalDate, state: DiaryState) {
    val p = LocalPalette.current
    Row(
        Modifier.padding(horizontal = 16.dp).padding(top = 2.dp, bottom = 4.dp).fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(p.plate).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt("Запись за ", Type.label, p.muted)
        Txt(Days.dateText(date) + relativeDay(date, state.today), Type.labelBold, p.accentText, Modifier.weight(1f), maxLines = 1)
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !state.recording) { state.chooseTarget(null) },
            contentAlignment = Alignment.Center,
        ) { if (!state.recording) Icon(Icons.close, p.muted, 20.dp) }
    }
}

private fun relativeDay(date: LocalDate, today: LocalDate) = when (date) {
    today.minusDays(1) -> " · вчера"
    today.minusDays(2) -> " · позавчера"
    else -> ""
}

/** Выбор прошлого дня: вчера, позавчера или любой другой. */
@Composable
private fun DayChooser(state: DiaryState, onPick: (LocalDate) -> Unit, onOther: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.open)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Txt("За какой день запись?", Type.labelBold, p.text, Modifier.padding(start = 4.dp, bottom = 4.dp))
            val y = state.today.minusDays(1)
            val y2 = state.today.minusDays(2)
            PlateButton("Вчера, ${Days.dateText(y)}", { onPick(y) }, Modifier.fillMaxWidth())
            PlateButton("Позавчера, ${Days.dateText(y2)}", { onPick(y2) }, Modifier.fillMaxWidth())
            PlateButton("Другой день…", onOther, Modifier.fillMaxWidth())
            PlateButton("Отмена", onDismiss, Modifier.fillMaxWidth(), color = p.muted, center = true)
        }
    }
}

/** Системный календарь; будущие дни выбрать нельзя. */
private fun pickPastDate(context: android.content.Context, dark: Boolean, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val theme = if (dark) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
    val start = today.minusDays(3)
    val dialog = android.app.DatePickerDialog(context, theme, { _, y, m, d ->
        onPick(LocalDate.of(y, m + 1, d))
    }, start.year, start.monthValue - 1, start.dayOfMonth)
    dialog.datePicker.maxDate = System.currentTimeMillis()
    dialog.show()
}

@Composable
private fun DayPlate(day: Day, state: DiaryState) {
    val p = LocalPalette.current
    val open = state.expanded == day.date
    if (!open) {
        Column(
            Modifier.padding(horizontal = 8.dp).padding(bottom = 4.dp).fillMaxWidth()
                .clip(RoundedCornerShape(14.dp)).background(p.plate)
                .clickable { state.toggleDay(day.date) }
                .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 7.dp),
        ) {
            MetaLine(day, state)
            val first = day.entries.firstOrNull { it.text.isNotBlank() }
            Txt(
                first?.text?.replace('\n', ' ') ?: if (day.date == state.today) "Пока пусто — нажмите на микрофон внизу" else "Пока пусто",
                Type.preview,
                if (first != null) p.soft else p.muted,
                maxLines = 1,
            )
        }
    } else {
        Column(
            Modifier.padding(horizontal = 8.dp).padding(bottom = 4.dp).fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)).background(p.open),
        ) {
            DayHeader(day, state, open = true) { state.toggleDay(day.date) }
            Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 10.dp)) {
                if (day.entries.isEmpty()) {
                    Txt("Пока пусто. Нажмите на микрофон внизу и говорите.", Type.entry, p.muted)
                }
                for (e in day.entries) {
                    EntryText(e, state)
                    Spacer(Modifier.height(10.dp))
                }
                if (day.date.isBefore(state.today) && state.target != day.date && !state.recording) {
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).clickable { state.chooseTarget(day.date) }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.plus, p.accentText, 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Txt("Дописать за этот день", Type.labelBold, p.accentText)
                    }
                } else if (state.target == day.date && !state.recording) {
                    Txt("Нажмите на микрофон — запись пойдёт в этот день.", Type.meta, p.muted)
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: Day, state: DiaryState, open: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { MetaLine(day, state) }
        if (open) Icon(Icons.up, p.muted, 20.dp)
    }
}

/** «Вт 11.11.2026 · 2 записи» — и всё (просьба владельца); число записей оранжевым. */
@Composable
private fun MetaLine(day: Day, state: DiaryState) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Txt(Days.weekdayShort(day.date) + " " + Days.dateText(day.date), Type.dayDate, p.soft)
        val n = day.entries.count { it.text.isNotBlank() || it.id == state.recordingId }
        if (n > 0) {
            Txt("·", Type.meta, p.muted)
            Chip(Days.entriesWord(n))
        }
    }
}

/** Одна запись в раскрытом дне: время оранжевым и текст. Нажатие — правка. */
@Composable
private fun EntryText(e: Entry, state: DiaryState) {
    val p = LocalPalette.current
    val isLive = e.id == state.recordingId
    val time = java.time.Instant.ofEpochMilli(e.createdAt).atZone(ZoneId.systemDefault()).toLocalTime()
    val text: AnnotatedString = buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = Type.time.fontFamily, fontWeight = Type.time.fontWeight, fontSize = Type.time.fontSize, color = p.accentText)) {
            append("%d:%02d".format(time.hour, time.minute))
        }
        if (Lang.of(e.lang) != Lang.RU) {
            withStyle(SpanStyle(fontFamily = Type.tag.fontFamily, fontWeight = Type.tag.fontWeight, fontSize = Type.tag.fontSize, color = p.muted)) {
                append(" ${Lang.of(e.lang).button}")
            }
        }
        append("  ")
        withStyle(SpanStyle(color = p.text)) { append(e.text) }
        for (tag in e.tags) {
            append(" ")
            val chip = SpanStyle(
                fontFamily = Type.tag.fontFamily, fontWeight = Type.tag.fontWeight, fontSize = Type.tag.fontSize,
                color = p.chipText, background = p.chipBg,
            )
            withStyle(chip) { append(if (p.chipBg.alpha > 0f) "\u2009#$tag\u2009" else "#$tag") }
        }
        if (isLive) {
            val tail = state.partial
            if (tail.isNotBlank()) {
                if (e.text.isNotEmpty()) append(" ")
                withStyle(SpanStyle(color = p.wet)) { append(tail) }
            } else if (e.text.isEmpty()) {
                withStyle(SpanStyle(color = p.wet)) { append("говорите…") }
            }
        }
    }
    BasicText(
        text,
        modifier = Modifier.fillMaxWidth().clickable { state.screen = com.daybook.app.Screen.Edit(e.id) },
        style = Type.entry.copy(color = p.text),
        overflow = TextOverflow.Clip,
    )
}

/** Низ экрана: языки и микрофон; во время записи — таймер и «стоп». */
@Composable
private fun Dock(state: DiaryState, onMic: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    Row(modifier.fillMaxWidth().height(62.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.recording) {
            var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
            LaunchedEffect(Unit) {
                while (true) {
                    now = SystemClock.elapsedRealtime()
                    delay(250)
                }
            }
            val sec = ((now - state.recordingSince) / 1000).coerceAtLeast(0)
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(p.plate).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val dot = (11 + (state.level.coerceIn(0f, 10f) * 0.6f)).dp
                Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(dot).clip(CircleShape).background(p.rec))
                }
                Spacer(Modifier.width(8.dp))
                Txt("%d:%02d".format(sec / 60, sec % 60), Type.timer, p.text, Modifier.weight(1f))
                Txt(state.lang.button, Type.seg, p.muted)
            }
        } else {
            Segments(
                Lang.entries, state.lang, { it.button }, { state.chooseLang(it) },
                Modifier.weight(1f), height = 62.dp,
            )
        }
        Box(
            Modifier.width(104.dp).fillMaxHeight().clip(RoundedCornerShape(20.dp)).background(p.button)
                .clickable { if (state.recording) state.stopRecording() else onMic() },
            contentAlignment = Alignment.Center,
        ) {
            if (state.recording) Icon(Icons.stop, p.onButton, 22.dp) else Icon(Icons.mic, p.onButton, 28.dp)
        }
    }
}
