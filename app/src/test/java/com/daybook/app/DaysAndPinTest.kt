package com.daybook.app

import com.daybook.app.data.Days
import com.daybook.app.data.Entry
import com.daybook.app.data.FeedRow
import com.daybook.app.data.Pin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DaysAndPinTest {

    private val zone = ZoneOffset.UTC
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) =
        LocalDate.of(y, m, d).atTime(h, 0).toInstant(zone).toEpochMilli()

    private fun e(id: Long, millis: Long) = Entry(id, millis, "RU", "t$id", millis)

    @Test
    fun feedNewestFirstWithMonthsAndToday() {
        val entries = listOf(e(1, at(2026, 10, 28)), e(2, at(2026, 11, 8, 9)), e(3, at(2026, 11, 8, 7)))
        val rows = Days.feed(entries, today = LocalDate.of(2026, 11, 10), zone = zone)
        val keys = rows.map { it.key }
        assertEquals(listOf("m-2026-11", "d-2026-11-10", "d-2026-11-08", "m-2026-10", "d-2026-10-28"), keys)
        val nov8 = (rows[2] as FeedRow.DayRow).day
        assertEquals(listOf(3L, 2L), nov8.entries.map { it.id }) // по времени: 7:00, потом 9:00
        assertTrue((rows[1] as FeedRow.DayRow).day.entries.isEmpty()) // сегодня пустой, но есть
    }

    @Test
    fun chosenPastDayShownEvenIfEmpty() {
        val entries = listOf(e(1, at(2026, 11, 11)))
        val rows = Days.feed(entries, today = LocalDate.of(2026, 11, 11), zone = zone, extra = LocalDate.of(2026, 11, 9))
        assertEquals(listOf("m-2026-11", "d-2026-11-11", "d-2026-11-09"), rows.map { it.key })
    }

    @Test
    fun entriesWord() {
        assertEquals("1 запись", Days.entriesWord(1))
        assertEquals("3 записи", Days.entriesWord(3))
        assertEquals("5 записей", Days.entriesWord(5))
        assertEquals("11 записей", Days.entriesWord(11))
        assertEquals("21 запись", Days.entriesWord(21))
        assertEquals("22 записи", Days.entriesWord(22))
    }

    @Test
    fun dateText() {
        assertEquals("09.11.2026", Days.dateText(LocalDate.of(2026, 11, 9)))
        assertEquals("Пн", Days.weekdayShort(LocalDate.of(2026, 11, 9)))
        assertEquals("Вт", Days.weekdayShort(LocalDate.of(2026, 11, 10)))
        assertEquals("Вс", Days.weekdayShort(LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun pinHashAndCheck() {
        val salt = Pin.newSalt()
        val h = Pin.hash("1234", salt)
        assertTrue(Pin.matches("1234", salt, h))
        assertFalse(Pin.matches("1235", salt, h))
        assertFalse(Pin.matches("123", salt, h))
        assertFalse(Pin.isValid("12a4"))
    }
}
