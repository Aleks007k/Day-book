package com.daybook.app.speech

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Запись голоса в текст для одной записи дневника.
 *
 * Способ, выбранный на этапе 1 (режим C): приложение само пишет звук с
 * микрофона и непрерывно передаёт его распознавателю. Распознаватель не
 * выключается на паузах — нет перезапусков, разрывов и писка.
 *  - RU, EN, DE — распознаватель «на телефоне», без интернета;
 *  - UK — служба распознавания по умолчанию (Google), через интернет.
 *
 * Все методы — только из главного потока.
 */
class Recorder(
    private val context: Context,
    private val lang: Lang,
    private val languageSwitch: Boolean,
    private val listener: Listener,
) {
    interface Listener {
        /** Текст, который ещё уточняется (показываем бледным). */
        fun onPartial(text: String)

        /** Окончательный кусок. raw — он же без форматирования (если есть). */
        fun onSegment(text: String, raw: String?, pauseBeforeMs: Long)

        fun onLevel(level: Float)

        /** Запись остановилась сама и дальше не пойдёт. needsDownload — нет офлайн-пакета языка. */
        fun onFailed(message: String, needsDownload: Boolean)

        /** Запись полностью закончена (после [stop] или [onFailed]). */
        fun onFinished()
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var mic: MicPipe? = null
    private var active = false
    private var finished = false
    private var listening = false
    private var consecutiveErrors = 0
    private var partial = ""

    // Пауза между кусками: от последнего окончательного куска до начала следующего.
    private var lastSegmentAt = 0L
    private var segmentStartedAt = 0L

    val onDevice: Boolean get() = lang.offline && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start() {
        active = true
        if (!createRecognizer()) return
        listen()
    }

    /** Остановить: закрываем микрофон, распознаватель дописывает последний кусок. */
    fun stop() {
        if (!active) return
        active = false
        handler.removeCallbacksAndMessages(null)
        mic?.close()
        // Даём распознавателю время отдать последний кусок, потом закрываем.
        handler.postDelayed({ finish() }, 1500)
    }

    private fun createRecognizer(): Boolean {
        recognizer?.destroy()
        recognizer = null
        return try {
            recognizer = (
                if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                else SpeechRecognizer.createSpeechRecognizer(context)
                ).also { it.setRecognitionListener(rl) }
            true
        } catch (e: Exception) {
            fail("Распознавание речи недоступно на этом телефоне.", false)
            false
        }
    }

    private fun listen() {
        if (!active) return
        mic?.close()
        val m = MicPipe { lvl -> handler.post { listener.onLevel(lvl) } }
        val err = m.open()
        if (err != null) {
            fail("Не удалось включить микрофон: $err", false)
            return
        }
        mic = m
        try {
            recognizer?.startListening(intent(m))
            listening = true
        } catch (e: Exception) {
            fail("Распознаватель не запускается.", false)
        }
    }

    private fun intent(m: MicPipe) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.tag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        // Точки и запятые от распознавателя; второй вариант — тот же текст без форматирования.
        putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 2)
        // Режим C: свой микрофон, непрерывный поток.
        putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, m.readSide)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, MicPipe.SAMPLE_RATE)
        // Смена языка на лету — только «на телефоне» и в осторожном режиме
        // (быстрый режим на этапе 1 путал русский с английским).
        if (languageSwitch && onDevice && Build.VERSION.SDK_INT >= 34) {
            val langs = ArrayList(listOf(lang.tag) + Lang.entries.filter { it.offline && it != lang }.map { it.tag })
            putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
            putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, langs)
            putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_HIGH_PRECISION)
            putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, langs)
        }
    }

    private val rl = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit // уровень считаем сами в MicPipe
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
        override fun onError(error: Int) = handleError(error)

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (text.isNotBlank() && segmentStartedAt == 0L) segmentStartedAt = SystemClock.elapsedRealtime()
            partial = text
            consecutiveErrors = 0
            listener.onPartial(text)
        }

        override fun onSegmentResults(segmentResults: Bundle) = commit(segmentResults)

        override fun onResults(results: Bundle?) {
            commit(results)
            endOfRound()
        }

        override fun onEndOfSegmentedSession() = endOfRound()
    }

    private fun commit(bundle: Bundle?) {
        val list = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = list?.firstOrNull()?.trim().orEmpty()
        val raw = list?.getOrNull(1)?.trim()
        partial = ""
        listener.onPartial("")
        if (text.isEmpty()) return
        consecutiveErrors = 0
        val now = SystemClock.elapsedRealtime()
        val start = if (segmentStartedAt > 0) segmentStartedAt else now
        val pause = if (lastSegmentAt > 0) (start - lastSegmentAt).coerceAtLeast(0) else 0
        lastSegmentAt = now
        segmentStartedAt = 0L
        listener.onSegment(text, raw, pause)
    }

    /** Сеанс закончился сам (редко в режиме C) — если не остановлено, запускаем снова. */
    private fun endOfRound() {
        if (!listening) return
        listening = false
        if (active) handler.postDelayed({ listen() }, 50)
    }

    private fun handleError(code: Int) {
        listening = false
        if (!active) return
        when (code) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                fail("Нет доступа к микрофону.", false); return
            }
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                fail("Для языка «${lang.title}» на телефоне нет офлайн-пакета.", true); return
            }
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> {
                fail("Язык «${lang.title}» этим распознавателем не поддерживается.", lang.offline); return
            }
        }
        consecutiveErrors++
        if (consecutiveErrors >= 8) {
            val net = code == SpeechRecognizer.ERROR_NETWORK || code == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                code == SpeechRecognizer.ERROR_SERVER
            fail(
                if (net && !onDevice) "Нет интернета: украинский распознаётся только через интернет."
                else "Распознаватель несколько раз подряд не ответил. Попробуйте ещё раз.",
                false,
            )
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
        handler.postDelayed({
            if (!active) return@postDelayed
            if (recreate && !createRecognizer()) return@postDelayed
            listen()
        }, delay)
    }

    private fun fail(message: String, needsDownload: Boolean) {
        active = false
        handler.removeCallbacksAndMessages(null)
        if (!finished) listener.onFailed(message, needsDownload)
        finish()
    }

    private fun finish() {
        if (finished) return
        finished = true
        mic?.close()
        mic = null
        recognizer?.destroy()
        recognizer = null
        // Недоговорённый хвост тоже сохраняем.
        if (partial.isNotBlank()) {
            val tail = partial
            partial = ""
            val pause = if (lastSegmentAt > 0 && segmentStartedAt > 0) segmentStartedAt - lastSegmentAt else 0
            listener.onSegment(tail, null, pause)
        }
        listener.onLevel(0f)
        listener.onFinished()
    }

    companion object {
        /** Попросить телефон скачать офлайн-пакет языка (бесплатно, через Wi-Fi/интернет). */
        fun requestDownload(context: Context, lang: Lang): Boolean = try {
            val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            r.triggerModelDownload(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.tag)
                },
            )
            Handler(Looper.getMainLooper()).postDelayed({ r.destroy() }, 15_000)
            true
        } catch (e: Exception) {
            false
        }
    }
}
