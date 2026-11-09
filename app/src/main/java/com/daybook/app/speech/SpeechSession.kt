package com.daybook.app.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionPart
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Длинный сеанс (Android 13+): распознаватель не останавливается на паузах,
 * а отдаёт текст кусками. Как задать длину сеанса, документация описывает
 * неоднозначно, поэтому проверяем два способа:
 *  A — «минимальная длина сеанса» числом int;
 *  B — то же самое числом long (старые службы Google читают long).
 */
enum class LongMode(val label: String) { OFF("нет"), A("A"), B("B") }

data class SessionOptions(
    val engine: Engine,
    val lang: Lang,
    val longMode: LongMode,
    val punctuation: Boolean,
    val wordConfidence: Boolean,
)

/** Итог одной проверки — строка в отчёте. */
data class SessionReport(
    val options: SessionOptions,
    val durationSec: Long,
    val segments: Int,
    val restarts: Int,
    val maxGapMs: Long,
    val avgGapMs: Long,
    val errors: Map<String, Int>,
    val stopReason: String,
    val punctuationSeen: Boolean,
    val capitalsSeen: Boolean,
    val formattedDiffersFromRaw: Boolean,
    val unstableSeen: Boolean,
    val confidence: Map<Int, Int>,
    val unsureWords: List<String>,
    val text: String,
) {
    fun toText(index: Int): String = buildString {
        val o = options
        appendLine("Проверка $index: ${o.engine.title}, ${o.lang.button}")
        appendLine(
            "  настройки: длинный сеанс ${o.longMode.label}, " +
                "пунктуация ${yes(o.punctuation)}, уверенность слов ${yes(o.wordConfidence)}",
        )
        appendLine("  длительность ${durationSec} с, кусков текста $segments, перезапусков $restarts")
        if (restarts > 0) appendLine("  разрыв при перезапуске: макс $maxGapMs мс, в среднем $avgGapMs мс")
        if (errors.isNotEmpty()) {
            appendLine("  ошибки: " + errors.entries.joinToString { "${it.key} ×${it.value}" })
        }
        appendLine("  остановка: $stopReason")
        appendLine(
            "  знаки препинания ${yes(punctuationSeen)}, заглавные ${yes(capitalsSeen)}, " +
                "форматирование меняло текст ${yes(formattedDiffersFromRaw)}",
        )
        appendLine("  неустойчивая часть в промежуточном тексте ${yes(unstableSeen)}")
        if (o.wordConfidence) {
            if (confidence.isEmpty()) {
                appendLine("  уверенность по словам: не пришла")
            } else {
                appendLine(
                    "  уверенность по словам: " + confidence.toSortedMap()
                        .entries.joinToString { "${levelName(it.key)} ${it.value}" },
                )
                if (unsureWords.isNotEmpty()) {
                    appendLine("  неуверенные слова: " + unsureWords.take(20).joinToString(", "))
                }
            }
        }
        appendLine("  текст: ${text.take(400)}${if (text.length > 400) "…" else ""}")
    }

    private fun yes(b: Boolean) = if (b) "да" else "нет"

    private fun levelName(level: Int) = when (level) {
        RecognitionPart.CONFIDENCE_LEVEL_HIGH -> "высокая"
        RecognitionPart.CONFIDENCE_LEVEL_MEDIUM_HIGH -> "выше средней"
        RecognitionPart.CONFIDENCE_LEVEL_MEDIUM -> "средняя"
        RecognitionPart.CONFIDENCE_LEVEL_MEDIUM_LOW -> "ниже средней"
        RecognitionPart.CONFIDENCE_LEVEL_LOW -> "низкая"
        else -> "неизвестна"
    }
}

/**
 * Одна проверка: держит распознаватель включённым, пока не нажат «Стоп»,
 * перезапускает его, если он остановился сам, и считает всё, что нужно
 * для отчёта. Все методы — только из главного потока (так требует Android).
 */
class SpeechSession(
    private val context: Context,
    val options: SessionOptions,
    private val onFinished: (SessionReport) -> Unit,
) {
    // То, что показывает экран.
    var finalText by mutableStateOf("")
        private set
    var partialText by mutableStateOf("")
        private set
    var unstableText by mutableStateOf("")
        private set
    var status by mutableStateOf("")
        private set
    var active by mutableStateOf(false)
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var segments by mutableIntStateOf(0)
        private set
    var restarts by mutableIntStateOf(0)
        private set
    var maxGapMs by mutableLongStateOf(0L)
        private set
    var startedAt = 0L
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var stoppedAt = 0L
    private var consecutiveErrors = 0
    private var finished = false
    private var stopReason = "кнопка «Стоп»"

    private val gaps = mutableListOf<Long>()
    private val errors = linkedMapOf<String, Int>()
    private var punctuationSeen = false
    private var capitalsSeen = false
    private var formattedDiffers = false
    private var unstableSeen = false
    private val confidence = mutableMapOf<Int, Int>()
    private val unsureWords = mutableListOf<String>()

    fun start() {
        startedAt = SystemClock.elapsedRealtime()
        active = true
        if (!createRecognizer()) return
        listen(first = true)
    }

    /** Остановить по кнопке (или когда приложение ушло с экрана). */
    fun stop(reason: String = "кнопка «Стоп»") {
        if (!active) return
        active = false
        stopReason = reason
        status = "завершаю…"
        handler.removeCallbacksAndMessages(null)
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {
        }
        // Даём распознавателю секунду дописать последний кусок, потом закрываем.
        handler.postDelayed({ finish() }, 1500)
    }

    private fun createRecognizer(): Boolean {
        recognizer?.destroy()
        recognizer = null
        return try {
            recognizer = Engines.createRecognizer(context, options.engine).also {
                it.setRecognitionListener(listener)
            }
            true
        } catch (e: Exception) {
            fail("распознаватель не создаётся: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun listen(first: Boolean) {
        if (!active) return
        if (!first) restarts++
        try {
            recognizer?.startListening(buildIntent())
            listening = true
            status = "включаю микрофон…"
        } catch (e: Exception) {
            fail("не запускается: ${e.javaClass.simpleName}")
        }
    }

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, options.lang.tag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        if (options.engine.preferOffline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        val hour = 60 * 60 * 1000
        when (options.longMode) {
            LongMode.OFF -> Unit
            LongMode.A -> {
                putExtra(
                    RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                )
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, hour)
            }
            LongMode.B -> {
                putExtra(
                    RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                    RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                )
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, hour.toLong())
            }
        }
        if (options.punctuation) {
            putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
            // С форматированием служба отдаёт два варианта: с пунктуацией и без.
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 2)
        } else {
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        if (options.wordConfidence && Build.VERSION.SDK_INT >= 34) {
            putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_CONFIDENCE, true)
            putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_TIMING, true)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (stoppedAt > 0) {
                val gap = SystemClock.elapsedRealtime() - stoppedAt
                gaps += gap
                if (gap > maxGapMs) maxGapMs = gap
                stoppedAt = 0
            }
            status = "слушаю"
        }

        override fun onBeginningOfSpeech() {
            status = "слышу речь"
        }

        override fun onRmsChanged(rmsdB: Float) {
            level = rmsdB
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            status = "пауза"
        }

        override fun onError(error: Int) = handleError(error)

        override fun onResults(results: Bundle?) {
            commit(results)
            endOfRound()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            partialText = list?.firstOrNull().orEmpty()
            val unstable = partialResults
                ?.getStringArrayList("android.speech.extra.UNSTABLE_TEXT")
                ?.firstOrNull()
                .orEmpty()
            if (unstable.isNotBlank()) unstableSeen = true
            unstableText = unstable
            consecutiveErrors = 0
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onSegmentResults(segmentResults: Bundle) {
            commit(segmentResults)
        }

        override fun onEndOfSegmentedSession() {
            endOfRound()
        }
    }

    /** Пришёл окончательный кусок текста — дописываем его в конец. */
    private fun commit(bundle: Bundle?) {
        partialText = ""
        unstableText = ""
        if (bundle == null) return
        val list = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = list?.firstOrNull()?.trim().orEmpty()
        if (options.punctuation && list != null && list.size >= 2 && list[0].trim() != list[1].trim()) {
            formattedDiffers = true
        }
        if (Build.VERSION.SDK_INT >= 34) readParts(bundle)
        if (text.isEmpty()) return
        consecutiveErrors = 0
        segments++
        if (text.any { it in ".,!?;:" }) punctuationSeen = true
        if (text.any { it.isUpperCase() }) capitalsSeen = true
        finalText = if (finalText.isEmpty()) text else "$finalText $text"
    }

    private fun readParts(bundle: Bundle) {
        val parts = try {
            bundle.getParcelableArrayList(SpeechRecognizer.RECOGNITION_PARTS, RecognitionPart::class.java)
        } catch (_: Exception) {
            null
        } ?: return
        for (p in parts) {
            val lvl = p.confidenceLevel
            confidence[lvl] = (confidence[lvl] ?: 0) + 1
            if (lvl == RecognitionPart.CONFIDENCE_LEVEL_LOW || lvl == RecognitionPart.CONFIDENCE_LEVEL_MEDIUM_LOW) {
                val word = p.formattedText ?: p.rawText
                if (unsureWords.size < 50) unsureWords += word
            }
        }
    }

    /** Распознаватель закончил очередной круг. Если «Стоп» не нажат — запускаем снова. */
    private fun endOfRound() {
        if (!listening) return
        listening = false
        stoppedAt = SystemClock.elapsedRealtime()
        if (active) {
            status = "перезапуск"
            handler.postDelayed({ listen(first = false) }, 50)
        }
    }

    private fun handleError(code: Int) {
        val wasListening = listening
        listening = false
        if (!active) return
        errors[Errors.name(code)] = (errors[Errors.name(code)] ?: 0) + 1
        if (wasListening) stoppedAt = SystemClock.elapsedRealtime()
        when (code) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            -> {
                fail(Errors.name(code))
                return
            }
        }
        consecutiveErrors++
        if (consecutiveErrors >= 8) {
            fail("8 ошибок подряд (${Errors.name(code)})")
            return
        }
        val recreate = code == SpeechRecognizer.ERROR_CLIENT ||
            code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
            code == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
        val delay = when (code) {
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
            -> 1000L
            else -> if (recreate) 300L else 50L
        }
        status = "перезапуск после ошибки: ${Errors.name(code)}"
        handler.postDelayed({
            if (!active) return@postDelayed
            if (recreate && !createRecognizer()) return@postDelayed
            listen(first = false)
        }, delay)
    }

    private fun fail(reason: String) {
        stopReason = reason
        active = false
        status = "остановлено: $reason"
        handler.removeCallbacksAndMessages(null)
        finish()
    }

    private fun finish() {
        if (finished) return
        finished = true
        recognizer?.destroy()
        recognizer = null
        // Недописанный хвост тоже сохраняем, чтобы он попал в отчёт.
        if (partialText.isNotBlank()) {
            finalText = if (finalText.isEmpty()) partialText else "$finalText $partialText"
            partialText = ""
            unstableText = ""
        }
        if (status.startsWith("завершаю")) status = "готово"
        level = 0f
        onFinished(
            SessionReport(
                options = options,
                durationSec = (SystemClock.elapsedRealtime() - startedAt) / 1000,
                segments = segments,
                restarts = restarts,
                maxGapMs = maxGapMs,
                avgGapMs = if (gaps.isEmpty()) 0 else gaps.sum() / gaps.size,
                errors = errors.toMap(),
                stopReason = stopReason,
                punctuationSeen = punctuationSeen,
                capitalsSeen = capitalsSeen,
                formattedDiffersFromRaw = formattedDiffers,
                unstableSeen = unstableSeen,
                confidence = confidence.toMap(),
                unsureWords = unsureWords.toList(),
                text = finalText,
            ),
        )
    }
}
