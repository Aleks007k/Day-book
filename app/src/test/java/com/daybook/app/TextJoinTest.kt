package com.daybook.app

import com.daybook.app.speech.Lang
import com.daybook.app.speech.TextJoin
import org.junit.Assert.assertEquals
import org.junit.Test

class TextJoinTest {

    private fun join(existing: String, seg: String, pause: Long = 0, raw: String? = null, lang: Lang = Lang.RU) =
        TextJoin.append(existing, seg, raw, pause, lang)

    @Test
    fun firstSegmentGetsCapital() {
        assertEquals("Привет, как дела", join("", "привет, как дела"))
    }

    @Test
    fun shortPauseContinuesSentenceInLowercase() {
        // Так было в отчёте режима C: «…может сказать Вот так А как…»
        assertEquals("Может сказать вот так", join("Может сказать", "Вот так", pause = 500))
    }

    @Test
    fun longPauseEndsSentence() {
        assertEquals("Пришёл домой. Сразу лёг спать", join("Пришёл домой", "сразу лёг спать", pause = 3000))
    }

    @Test
    fun afterRecognizerPeriodCapitalStays() {
        assertEquals("Что случилось. Если я говорю", join("Что случилось.", "Если я говорю"))
    }

    @Test
    fun namesStayCapitalWhenRawSaysSo() {
        assertEquals("Говорил с Мариной", join("Говорил с", "Мариной", raw = "Мариной"))
    }

    @Test
    fun abbreviationsStayUppercase() {
        assertEquals("Написал в ВК", join("Написал в", "ВК"))
    }

    @Test
    fun englishIStaysUppercase() {
        assertEquals("Yesterday I went home", join("Yesterday", "I went home", lang = Lang.EN))
    }

    @Test
    fun germanNounsAreNotLowercased() {
        assertEquals("Ich habe Hunger", join("Ich habe", "Hunger", lang = Lang.DE))
    }

    @Test
    fun noDoublePeriodAfterComma() {
        assertEquals("Ну, в общем, в целом", join("Ну, в общем,", "в целом", pause = 5000))
    }

    @Test
    fun voiceCommaUkrainian() {
        assertEquals("Пішов додому,", TextJoin.applyVoiceMarks("Пішов додому кома", Lang.UK))
    }

    @Test
    fun voiceQuestionRussianIgnoresRecognizerPeriod() {
        assertEquals("Почему ты молчишь?", TextJoin.applyVoiceMarks("Почему ты молчишь вопросительный знак.", Lang.RU))
    }

    @Test
    fun voiceMarkOnlyAtTheEnd() {
        assertEquals("Точка зрения у него другая", TextJoin.applyVoiceMarks("Точка зрения у него другая", Lang.RU))
    }

    @Test
    fun loneMarkSegmentGluesWithoutSpace() {
        val seg = TextJoin.applyVoiceMarks("крапка", Lang.UK)
        assertEquals(".", seg)
        assertEquals("Добре.", join("Добре", seg, lang = Lang.UK))
    }

    @Test
    fun afterVoicePeriodNextIsCapital() {
        assertEquals("Добре. Завтра", join("Добре.", "завтра", lang = Lang.UK))
    }
}
