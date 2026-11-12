package com.daybook.app

import com.daybook.app.data.AutoTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTagsTest {

    private fun tags(text: String) = AutoTags.suggest(text)

    @Test
    fun workInRussian() {
        assertTrue("работа" in tags("Созвон с командой. Обсудили проект, Марина предложила перенести релиз."))
    }

    @Test
    fun familyInUkrainian() {
        assertTrue("семья" in tags("Зайшов до мами, посиділи з батьком на ґанку."))
    }

    @Test
    fun sportInEnglishAndGerman() {
        assertTrue("спорт" in tags("Quick workout at the gym after work."))
        assertTrue("спорт" in tags("Heute war ich joggen und danach im Fitness."))
    }

    @Test
    fun phraseBirthday() {
        assertTrue("праздник" in tags("Вчера был день рождения у Оли, дарили подарки."))
    }

    @Test
    fun noFalsePositivesOnCommonWords() {
        // «брать», «лестница», «погоди», «of course», «kind of» — не теги.
        assertEquals(emptyList<String>(), tags("Надо брать лестницу, погоди. Of course, kind of nice."))
    }

    @Test
    fun zdorovoIsJoyNotHealth() {
        assertEquals(listOf("радость"), tags("Было здорово!"))
    }

    @Test
    fun atMostThreeMostFrequentFirst() {
        val t = tags("Работа, работа, офис и коллеги. Потом спортзал? Нет, тренировка. Купил хлеб в магазине. Поел пиццу.")
        assertTrue(t.size <= AutoTags.MAX_TAGS)
        assertEquals("работа", t.first())
    }

    @Test
    fun emptyText() {
        assertTrue(tags("").isEmpty())
    }

    @Test
    fun storeAndParse() {
        assertEquals(listOf("работа", "семья"), AutoTags.parse(" работа, семья ,работа,"))
        assertEquals("работа,семья", AutoTags.store(listOf("работа", " семья", "работа")))
        assertEquals("мой тег", AutoTags.clean("#Мой, тег "))
        assertFalse(AutoTags.NAMES.isEmpty())
    }
}
