package com.daybook.app.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.speech.RecognitionService
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/** Четыре языка дневника. tag — код для распознавателя, code — для сравнения списков. */
enum class Lang(val button: String, val tag: String, val code: String, val title: String) {
    RU("RU", "ru-RU", "ru", "русский"),
    UK("UK", "uk-UA", "uk", "украинский"),
    EN("EN", "en-US", "en", "английский"),
    DE("DE", "de-DE", "de", "немецкий"),
}

/**
 * Способ распознавания, который проверяем.
 * onDevice — системный распознаватель «на телефоне» (Android сам выбирает службу).
 * component — конкретная служба (Google, Samsung…); preferOffline — просим её
 * работать только без интернета.
 */
data class Engine(
    val id: String,
    val title: String,
    val component: ComponentName?,
    val onDevice: Boolean,
    val preferOffline: Boolean,
)

data class ServiceEntry(val label: String, val component: ComponentName)

/** Что известно о языке у одного распознавателя. */
enum class LangSupport(val text: String) {
    INSTALLED("офлайн-пакет скачан"),
    PENDING("пакет скачивается"),
    DOWNLOADABLE("офлайн можно скачать"),
    ONLINE("только через интернет"),
    NONE("не поддерживается"),
}

object Engines {

    fun deviceSummary(): String =
        "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), " +
            "Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}"

    /** Все службы распознавания речи, установленные в телефоне. */
    fun services(context: Context): List<ServiceEntry> {
        val pm = context.packageManager
        return pm.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .mapNotNull { it.serviceInfo }
            .map { info ->
                ServiceEntry(
                    label = info.loadLabel(pm).toString().ifBlank { info.packageName },
                    component = ComponentName(info.packageName, info.name),
                )
            }
            .distinctBy { it.component }
    }

    /** Служба, выбранная в настройках телефона по умолчанию (если Android разрешает это прочитать). */
    fun defaultService(context: Context): String? = try {
        Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
    } catch (e: Exception) {
        null
    }

    fun onDeviceAvailable(context: Context): Boolean =
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    /** Список вариантов для проверки: системный «на телефоне» + каждая служба онлайн и офлайн. */
    fun engines(context: Context): List<Engine> {
        val list = mutableListOf<Engine>()
        if (onDeviceAvailable(context)) {
            list += Engine("ondevice", "На телефоне", null, onDevice = true, preferOffline = false)
        }
        for (s in services(context)) {
            val short = shortName(s)
            list += Engine(s.component.flattenToShortString(), short, s.component, false, false)
            list += Engine(
                s.component.flattenToShortString() + "#offline",
                "$short офлайн",
                s.component,
                onDevice = false,
                preferOffline = true,
            )
        }
        return list
    }

    /** Короткое понятное имя службы для кнопки. */
    fun shortName(s: ServiceEntry): String {
        val p = s.component.packageName.lowercase(Locale.ROOT)
        return when {
            // У Samsung две службы Google: «Android System Intelligence» и
            // «Распознавание и синтез речи» — подписываем их по-разному.
            p == "com.google.android.as" -> "Google ASI"
            p.contains("google") -> "Google"
            p.contains("samsung") || p.contains("sec.") -> "Samsung"
            else -> s.label
        }
    }

    fun createRecognizer(context: Context, engine: Engine): SpeechRecognizer = when {
        engine.onDevice -> SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        engine.component != null -> SpeechRecognizer.createSpeechRecognizer(context, engine.component)
        else -> SpeechRecognizer.createSpeechRecognizer(context)
    }

    /**
     * Спросить у распознавателя, какие языки он знает и какие скачаны.
     * Ответ приходит позже, в [onResult]: карта язык → состояние, либо текст ошибки.
     */
    fun checkSupport(
        context: Context,
        engine: Engine,
        onResult: (Map<Lang, LangSupport>?, String?) -> Unit,
    ) {
        val recognizer = try {
            createRecognizer(context, engine)
        } catch (e: Exception) {
            onResult(null, "не создаётся: ${e.javaClass.simpleName}")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Lang.RU.tag)
        }
        var done = false
        fun finish(map: Map<Lang, LangSupport>?, err: String?) {
            if (done) return
            done = true
            recognizer.destroy()
            onResult(map, err)
        }
        try {
            recognizer.checkRecognitionSupport(
                intent,
                context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        finish(toMap(support), null)
                    }

                    override fun onError(error: Int) {
                        finish(null, "не сообщает (${Errors.name(error)})")
                    }
                },
            )
        } catch (e: Exception) {
            finish(null, "ошибка проверки: ${e.javaClass.simpleName}")
        }
    }

    private fun toMap(s: RecognitionSupport): Map<Lang, LangSupport> {
        fun has(list: List<String>, lang: Lang) = list.any {
            Locale.forLanguageTag(it).language.equals(lang.code, ignoreCase = true)
        }
        return Lang.entries.associateWith { lang ->
            when {
                has(s.installedOnDeviceLanguages, lang) -> LangSupport.INSTALLED
                has(s.pendingOnDeviceLanguages, lang) -> LangSupport.PENDING
                has(s.supportedOnDeviceLanguages, lang) -> LangSupport.DOWNLOADABLE
                has(s.onlineLanguages, lang) -> LangSupport.ONLINE
                else -> LangSupport.NONE
            }
        }
    }

    /** Попросить телефон скачать офлайн-пакет языка для системного распознавателя. */
    fun requestDownload(context: Context, engine: Engine, lang: Lang): String? {
        return try {
            val recognizer = createRecognizer(context, engine)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.tag)
            }
            recognizer.triggerModelDownload(intent)
            // Даём службе время принять запрос, потом отпускаем её.
            android.os.Handler(context.mainLooper).postDelayed({ recognizer.destroy() }, 15_000)
            null
        } catch (e: Exception) {
            "не получилось: ${e.javaClass.simpleName}"
        }
    }
}

/** Коды ошибок распознавателя — понятными словами. */
object Errors {
    fun name(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "нет ответа сети"
        SpeechRecognizer.ERROR_NETWORK -> "нет сети"
        SpeechRecognizer.ERROR_AUDIO -> "ошибка микрофона"
        SpeechRecognizer.ERROR_SERVER -> "ошибка сервера"
        SpeechRecognizer.ERROR_CLIENT -> "ошибка клиента"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "тишина"
        SpeechRecognizer.ERROR_NO_MATCH -> "ничего не распознано"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "распознаватель занят"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "нет разрешения"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "слишком много запросов"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "служба отключилась"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "язык не поддерживается"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "языковой пакет не скачан"
        SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "не умеет сообщать о языках"
        15 -> "не следит за скачиванием"
        else -> "код $code"
    }
}
