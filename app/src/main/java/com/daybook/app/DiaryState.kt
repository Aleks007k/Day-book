package com.daybook.app

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.daybook.app.data.Backup
import com.daybook.app.data.Days
import com.daybook.app.data.Db
import com.daybook.app.data.Entry
import com.daybook.app.data.Prefs
import com.daybook.app.data.ThemeMode
import com.daybook.app.speech.Lang
import com.daybook.app.speech.Recorder
import com.daybook.app.speech.TextJoin
import java.time.LocalDate

/** Какой экран открыт. */
sealed interface Screen {
    data object Feed : Screen
    data class Edit(val entryId: Long) : Screen
    data object Settings : Screen
}

/** Сообщение-вопрос поверх экрана (например, «скачать офлайн-пакет?»). */
data class Notice(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

/**
 * Всё состояние дневника и действия с ним. Живёт, пока открыто приложение;
 * записи при этом всегда уже лежат в базе.
 */
class DiaryState(private val context: Context) {
    val db = Db(context)
    val prefs = Prefs(context)

    val entries = mutableStateListOf<Entry>().apply { addAll(db.all()) }

    var today by mutableStateOf(LocalDate.now())
        private set
    val feed by derivedStateOf { Days.feed(entries.toList(), today) }

    var expanded by mutableStateOf<LocalDate?>(null)
    var screen by mutableStateOf<Screen>(Screen.Feed)

    var lang by mutableStateOf(prefs.lang)
        private set
    var theme by mutableStateOf(prefs.theme)
        private set
    var languageSwitch by mutableStateOf(prefs.languageSwitch)
        private set

    var notice by mutableStateOf<Notice?>(null)

    // --- запись ---
    private var recorder: Recorder? = null
    var recordingId by mutableStateOf<Long?>(null)
        private set
    var partial by mutableStateOf("")
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var recordingSince by mutableLongStateOf(0L)
        private set
    val recording get() = recordingId != null

    fun refreshToday() {
        val now = LocalDate.now()
        if (now != today) today = now
    }

    fun chooseLang(l: Lang) {
        if (recording) return
        lang = l
        prefs.lang = l
    }

    fun chooseTheme(t: ThemeMode) {
        theme = t
        prefs.theme = t
    }

    fun chooseLanguageSwitch(on: Boolean) {
        languageSwitch = on
        prefs.languageSwitch = on
    }

    fun toggleDay(date: LocalDate) {
        expanded = if (expanded == date) null else date
    }

    fun entry(id: Long): Entry? = entries.firstOrNull { it.id == id }

    // --- запись голосом ---

    /** Нажата кнопка микрофона (разрешение уже есть). */
    fun startRecording() {
        if (recording) return
        refreshToday()
        if (lang == Lang.UK && !prefs.ukNoticeShown) {
            prefs.ukNoticeShown = true
            notice = Notice(
                "Украинский распознаётся через интернет: звук уходит в Google. " +
                    "Русский, английский и немецкий распознаются прямо на телефоне, без интернета.",
                action = "Понятно, записывать",
                onAction = { startRecording() },
            )
            return
        }
        val e = db.insert(System.currentTimeMillis(), lang.name, "")
        entries += e
        recordingId = e.id
        recordingSince = SystemClock.elapsedRealtime()
        expanded = today
        partial = ""
        val recLang = lang
        val r = Recorder(context, recLang, languageSwitch, object : Recorder.Listener {
            override fun onPartial(text: String) {
                partial = text
            }

            override fun onSegment(text: String, raw: String?, pauseBeforeMs: Long) {
                val id = e.id
                val cur = entry(id) ?: return
                val l = Lang.of(cur.lang)
                val seg = TextJoin.applyVoiceMarks(text, l)
                val rawSeg = raw?.let { TextJoin.applyVoiceMarks(it, l) }
                save(cur.copy(text = TextJoin.append(cur.text, seg, rawSeg, pauseBeforeMs, l)))
            }

            override fun onLevel(level: Float) {
                this@DiaryState.level = level
            }

            override fun onFailed(message: String, needsDownload: Boolean) {
                val l = recLang
                notice = if (needsDownload) {
                    Notice(
                        "$message Скачать его? Это бесплатно, около 50–100 МБ, лучше через Wi-Fi. " +
                            "После скачивания нажмите микрофон ещё раз.",
                        action = "Скачать",
                        onAction = {
                            notice = if (Recorder.requestDownload(context, l)) {
                                Notice("Телефон скачивает пакет. Обычно это занимает пару минут.")
                            } else {
                                Notice("Не получилось запустить скачивание. Попробуйте позже.")
                            }
                        },
                    )
                } else {
                    Notice(message)
                }
            }

            override fun onFinished() {
                val id = recordingId
                recorder = null
                recordingId = null
                partial = ""
                level = 0f
                // Ничего не сказали — пустую запись не оставляем.
                if (id != null) entry(id)?.let { if (it.text.isBlank()) delete(it.id) }
            }
        })
        recorder = r
        r.start()
    }

    fun stopRecording() {
        recorder?.stop()
    }

    // --- правка ---

    fun save(e: Entry) {
        val i = entries.indexOfFirst { it.id == e.id }
        if (i < 0) return
        val updated = e.copy(updatedAt = System.currentTimeMillis())
        entries[i] = updated
        db.update(updated)
    }

    fun setText(id: Long, text: String) {
        entry(id)?.let { if (it.text != text) save(it.copy(text = text)) }
    }

    fun setTime(id: Long, millis: Long) {
        entry(id)?.let {
            save(it.copy(createdAt = millis))
            expanded = Days.dateOf(millis)
        }
    }

    fun delete(id: Long) {
        if (id == recordingId) return
        entries.removeAll { it.id == id }
        db.delete(id)
    }

    // --- резервная копия ---

    fun backupJson(): String = Backup.toJson(entries.toList())

    /** Добавить записи из копии. Возвращает текст для пользователя. */
    fun restore(json: String): String = try {
        val fromFile = Backup.parse(json)
        val fresh = Backup.newOnly(fromFile, entries.toList())
        entries += db.insertAll(fresh)
        val skipped = fromFile.size - fresh.size
        "Восстановлено записей: ${fresh.size}." + if (skipped > 0) " Уже были в дневнике: $skipped." else ""
    } catch (e: IllegalArgumentException) {
        e.message ?: "Не удалось прочитать копию."
    } catch (e: Exception) {
        "Не удалось прочитать копию."
    }
}
