package com.daybook.app.speech

/**
 * Склейка распознанных кусков в одну запись.
 *
 * Распознаватель отдаёт текст кусками (между паузами). Каждый кусок он
 * начинает с заглавной, а точку ставит не всегда. Здесь:
 *  - голосовые знаки препинания («запятая», «крапка», «comma»…) последним
 *    словом куска превращаются в знак;
 *  - после длинной паузы, если знака конца фразы нет, ставится точка;
 *  - заглавная — только в начале записи и после . ! ? …
 *    (в немецком заглавные не трогаем: там все существительные с большой).
 */
object TextJoin {

    /** Пауза между кусками, после которой считаем, что фраза закончилась. */
    const val LONG_PAUSE_MS = 2000L

    private val TERMINAL = setOf('.', '!', '?', '…')
    private val NO_SPACE_BEFORE = setOf('.', ',', '!', '?', ';', ':', '…')

    /** Голосовые знаки. Проверяются только в конце куска, длинные фразы — первыми. */
    private val VOICE_MARKS: Map<Lang, List<Pair<String, String>>> = mapOf(
        Lang.RU to listOf(
            "вопросительный знак" to "?", "восклицательный знак" to "!", "знак вопроса" to "?",
            "запятая" to ",", "точка" to ".",
        ),
        Lang.UK to listOf(
            "знак питання" to "?", "знак оклику" to "!", "кома" to ",", "крапка" to ".",
        ),
        Lang.EN to listOf(
            "question mark" to "?", "exclamation mark" to "!", "full stop" to ".",
            "comma" to ",", "period" to ".",
        ),
        Lang.DE to listOf(
            "fragezeichen" to "?", "ausrufezeichen" to "!", "komma" to ",", "punkt" to ".",
        ),
    )

    /** «…завтра запятая» → «…завтра,». Только если слово-знак стоит последним. */
    fun applyVoiceMarks(segment: String, lang: Lang): String {
        val core = segment.trim().trimEnd('.', ',', '!', '?', '…', ' ')
        val lower = core.lowercase()
        for ((phrase, mark) in VOICE_MARKS[lang].orEmpty()) {
            val matches = lower == phrase || lower.endsWith(" $phrase")
            if (matches) {
                val before = core.dropLast(phrase.length).trimEnd()
                return before + mark
            }
        }
        return segment.trim()
    }

    /**
     * Дописать кусок [segment] к тексту [existing].
     * [raw] — тот же кусок без форматирования (если служба его дала): по нему
     * видно, была ли заглавная «настоящей» (имя), а не просто началом куска.
     * [pauseBeforeMs] — пауза перед этим куском.
     */
    fun append(existing: String, segment: String, raw: String?, pauseBeforeMs: Long, lang: Lang): String {
        val seg = segment.trim()
        if (seg.isEmpty()) return existing
        val base = existing.trimEnd()
        if (base.isEmpty()) return capitalizeFirst(seg)

        // Кусок из одного знака («крапка» → «.») пристёгиваем без пробела.
        if (seg.first() in NO_SPACE_BEFORE) {
            val glued = base.trimEnd(',', ';', ':') + seg
            return glued
        }

        var b = base
        var sentenceEnded = b.last() in TERMINAL
        if (!sentenceEnded && pauseBeforeMs >= LONG_PAUSE_MS && b.last() !in setOf(',', ';', ':', '—', '-')) {
            b += "."
            sentenceEnded = true
        }
        val s = when {
            sentenceEnded -> capitalizeFirst(seg)
            lang == Lang.DE -> seg
            rawStartsUpper(raw) -> seg
            else -> lowerFirstWord(seg, lang)
        }
        return "$b $s"
    }

    fun capitalizeFirst(s: String): String {
        val i = s.indexOfFirst { it.isLetter() }
        if (i < 0) return s
        return s.substring(0, i) + s[i].uppercaseChar() + s.substring(i + 1)
    }

    private fun rawStartsUpper(raw: String?): Boolean {
        val c = raw?.trim()?.firstOrNull { it.isLetter() } ?: return false
        return c.isUpperCase()
    }

    /** Строчная первая буква, но не для сокращений («ВК», «OK») и английского «I». */
    private fun lowerFirstWord(s: String, lang: Lang): String {
        val word = s.takeWhile { !it.isWhitespace() }
        val letters = word.filter { it.isLetter() }
        if (letters.length >= 2 && letters.all { it.isUpperCase() }) return s
        if (lang == Lang.EN && (word == "I" || word.startsWith("I'") || word.startsWith("I’"))) return s
        val i = s.indexOfFirst { it.isLetter() }
        if (i < 0) return s
        return s.substring(0, i) + s[i].lowercaseChar() + s.substring(i + 1)
    }
}
