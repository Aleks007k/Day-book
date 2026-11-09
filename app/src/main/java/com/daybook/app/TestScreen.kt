package com.daybook.app

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daybook.app.speech.Lang
import com.daybook.app.speech.LangSupport
import com.daybook.app.speech.LongMode
import kotlinx.coroutines.delay

// Временный простой вид: это служебный экран проверки, дизайн — на этапе 3.
private val Paper = Color(0xFFF6F8FB)
private val Ink = Color(0xFF1B2F7A)
private val Soft = Color(0xFF5A6B9E)
private val Pale = Color(0xFFA9B4D3)
private val Line = Color(0xFFD5DBEA)

private val body = TextStyle(color = Ink, fontSize = 16.sp, lineHeight = 23.sp)
private val small = TextStyle(color = Soft, fontSize = 13.sp, lineHeight = 18.sp)
private val heading = TextStyle(color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TestScreen(c: TestController, onStart: () -> Unit) {
    val session = c.session
    val running = session?.active == true

    // Пока идёт диктовка, экран не гаснет.
    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(running) {
        while (running) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
    }

    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .safeDrawingPadding()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BasicText("Проверка распознавания", style = body.copy(fontSize = 22.sp))
        BasicText(c.device, style = small)

        // Какие службы есть и что они знают о языках.
        Section("Распознаватели в телефоне") {
            if (c.services.isEmpty()) BasicText("Служб распознавания не найдено.", style = small)
            c.services.forEach {
                BasicText("${it.label} — ${it.component.packageName}", style = small)
            }
            BasicText("По умолчанию: ${c.defaultService ?: "не удалось узнать"}", style = small)
            BasicText(
                "Системный «на телефоне»: ${if (c.onDeviceAvailable) "есть" else "нет"}",
                style = small,
            )
        }

        Section("Языки") {
            c.engines.filter { !it.preferOffline }.forEach { e ->
                BasicText(e.title, style = body)
                val map = c.support[e.id]
                val err = c.supportErrors[e.id]
                when {
                    map != null -> Lang.entries.forEach { l ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText("${l.button}  ${map[l]?.text}", style = small)
                            if (map[l] == LangSupport.DOWNLOADABLE && e.onDevice) {
                                Spacer(Modifier.width(10.dp))
                                Chip("скачать", false) { c.download(e, l) }
                            }
                        }
                    }
                    err != null -> BasicText(err, style = small)
                    else -> BasicText("жду ответа…", style = small)
                }
            }
            Chip("Обновить", false) {
                c.support.clear()
                c.supportErrors.clear()
                c.checkAllSupport()
            }
        }

        Section("Чем распознавать") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                c.engines.forEach { e ->
                    Chip(e.title, c.engine?.id == e.id, enabled = !running) { c.engine = e; c.saveChoice() }
                }
            }
        }

        Section("Язык") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Lang.entries.forEach { l ->
                    Chip(l.button, c.lang == l, enabled = !running) { c.lang = l; c.saveChoice() }
                }
            }
        }

        Section("Настройки") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText("Длинный сеанс", style = small)
                LongMode.entries.forEach { m ->
                    Chip(m.label, c.longMode == m, enabled = !running) { c.longMode = m; c.saveChoice() }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("пунктуация", c.punctuation, enabled = !running) {
                    c.punctuation = !c.punctuation; c.saveChoice()
                }
                Chip("уверенность слов", c.wordConfidence, enabled = !running) {
                    c.wordConfidence = !c.wordConfidence; c.saveChoice()
                }
                Chip("смена языка", c.languageSwitch, enabled = !running) {
                    c.languageSwitch = !c.languageSwitch; c.saveChoice()
                }
            }
        }

        // Кнопка микрофона и уровень звука.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(72.dp)
                    .background(if (running) Ink else Paper, CircleShape)
                    .border(2.dp, Ink, CircleShape)
                    .clickable(enabled = c.engine != null) {
                        if (running) c.stop() else onStart()
                    },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    if (running) "Стоп" else "Начать",
                    style = body.copy(color = if (running) Paper else Ink, fontSize = 14.sp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (session != null) {
                    val sec = if (running) (now - session.startedAt) / 1000 else null
                    BasicText(session.status, style = small)
                    if (sec != null) BasicText("${sec / 60}:${"%02d".format(sec % 60)}", style = small)
                    BasicText(
                        "кусков ${session.segments}, перезапусков ${session.restarts}" +
                            if (session.maxGapMs > 0) ", макс. разрыв ${session.maxGapMs} мс" else "",
                        style = small,
                    )
                    val lvl = ((session.level + 2f) / 12f).coerceIn(0f, 1f)
                    Box(Modifier.width(120.dp).height(4.dp).background(Line)) {
                        Box(Modifier.width((120 * lvl).dp).height(4.dp).background(Ink))
                    }
                } else if (c.engine == null) {
                    BasicText("Распознавателей нет — проверять нечего.", style = small)
                }
            }
        }

        // Текст: подтверждённый — чернилами, промежуточный — бледно.
        if (session != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .border(1.dp, Line, RoundedCornerShape(4.dp))
                    .padding(12.dp),
            ) {
                BasicText(
                    buildAnnotatedString {
                        append(session.finalText)
                        if (session.partialText.isNotEmpty()) {
                            if (session.finalText.isNotEmpty()) append(" ")
                            withStyle(SpanStyle(color = Soft)) { append(session.partialText) }
                        }
                        if (session.unstableText.isNotEmpty()) {
                            append(" ")
                            withStyle(SpanStyle(color = Pale)) { append(session.unstableText) }
                        }
                    },
                    style = body,
                )
            }
        }

        if (c.notice.isNotEmpty()) BasicText(c.notice, style = small.copy(color = Ink))

        Section("Журнал проверок") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Скопировать отчёт", false) { c.copyReport() }
                Chip("Очистить", false, enabled = !running) { c.clearLog() }
            }
            if (c.log.isEmpty()) BasicText("Пока пусто.", style = small)
            c.log.asReversed().forEach { BasicText(it.trimEnd(), style = small) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(title, style = heading)
        content()
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .background(if (selected) Ink else Paper, shape)
            .border(1.dp, if (enabled) Ink else Line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        BasicText(
            text,
            style = small.copy(
                color = when {
                    selected -> Paper
                    enabled -> Ink
                    else -> Pale
                },
            ),
        )
    }
}
