package com.daybook.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.daybook.app.speech.Engine
import com.daybook.app.speech.Engines
import com.daybook.app.speech.Lang
import com.daybook.app.speech.LangSupport
import com.daybook.app.speech.LongMode
import com.daybook.app.speech.SessionOptions
import com.daybook.app.speech.SpeechSession

/**
 * Всё состояние экрана проверки: какие распознаватели есть, что выбрано,
 * идущая проверка и журнал результатов. Выбор и журнал запоминаются,
 * поэтому закрытие приложения их не стирает.
 */
class TestController(private val context: Context) {

    private val prefs = context.getSharedPreferences("speech_test", Context.MODE_PRIVATE)

    val device = Engines.deviceSummary()
    val services = Engines.services(context)
    val defaultService = Engines.defaultService(context)
    val onDeviceAvailable = Engines.onDeviceAvailable(context)
    val engines: List<Engine> = Engines.engines(context)

    /** Языки по каждому распознавателю (ключ — id варианта). */
    val support = mutableStateMapOf<String, Map<Lang, LangSupport>>()
    val supportErrors = mutableStateMapOf<String, String>()

    var engine by mutableStateOf(
        engines.firstOrNull { it.id == prefs.getString("engine", null) } ?: engines.firstOrNull(),
    )
    var lang by mutableStateOf(
        Lang.entries.firstOrNull { it.name == prefs.getString("lang", null) } ?: Lang.RU,
    )
    var longMode by mutableStateOf(
        LongMode.entries.firstOrNull { it.name == prefs.getString("long", null) } ?: LongMode.A,
    )
    var punctuation by mutableStateOf(prefs.getBoolean("punct", true))
    var wordConfidence by mutableStateOf(prefs.getBoolean("conf", true))

    var session by mutableStateOf<SpeechSession?>(null)
        private set

    val log = mutableStateListOf<String>().apply {
        prefs.getString("log", null)?.split(SEP)?.filter { it.isNotBlank() }?.let { addAll(it) }
    }

    var notice by mutableStateOf("")

    fun saveChoice() {
        prefs.edit()
            .putString("engine", engine?.id)
            .putString("lang", lang.name)
            .putString("long", longMode.name)
            .putBoolean("punct", punctuation)
            .putBoolean("conf", wordConfidence)
            .apply()
    }

    /** Опросить распознаватели по очереди (а не все сразу — так надёжнее). */
    fun checkAllSupport() {
        val targets = engines.filter { !it.preferOffline }
        fun next(i: Int) {
            if (i >= targets.size) return
            val e = targets[i]
            Engines.checkSupport(context, e) { map, err ->
                if (map != null) support[e.id] = map
                if (err != null) supportErrors[e.id] = err
                next(i + 1)
            }
        }
        next(0)
    }

    fun download(e: Engine, l: Lang) {
        val err = Engines.requestDownload(context, e, l)
        notice = err ?: "Запрос на скачивание (${l.title}) отправлен. Через минуту нажмите «Обновить»."
    }

    fun start() {
        val e = engine ?: return
        if (session?.active == true) return
        saveChoice()
        notice = ""
        val s = SpeechSession(
            context,
            SessionOptions(e, lang, longMode, punctuation, wordConfidence),
        ) { report ->
            val entry = report.toText(log.size + 1)
            log.add(entry)
            persistLog()
        }
        session = s
        s.start()
    }

    fun stop(reason: String = "кнопка «Стоп»") {
        session?.stop(reason)
    }

    fun clearLog() {
        log.clear()
        persistLog()
    }

    fun reportText(): String = buildString {
        appendLine("Отчёт: проверка распознавания")
        appendLine("Телефон: $device")
        appendLine("Службы распознавания: " + services.joinToString { "${it.label} (${it.component.packageName})" })
        appendLine("Служба по умолчанию: ${defaultService ?: "не удалось узнать"}")
        appendLine("Системный «на телефоне»: ${if (onDeviceAvailable) "есть" else "нет"}")
        for (e in engines.filter { !it.preferOffline }) {
            val map = support[e.id]
            val err = supportErrors[e.id]
            appendLine(
                "Языки, ${e.title}: " + when {
                    map != null -> Lang.entries.joinToString { "${it.button} — ${map[it]?.text}" }
                    err != null -> err
                    else -> "нет ответа"
                },
            )
        }
        appendLine()
        log.forEach { appendLine(it) }
    }

    fun copyReport() {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Отчёт", reportText()))
        notice = "Отчёт скопирован. Вставьте его в чат."
    }

    private fun persistLog() {
        prefs.edit().putString("log", log.joinToString(SEP)).apply()
    }

    private companion object {
        const val SEP = "\u001E"
    }
}
