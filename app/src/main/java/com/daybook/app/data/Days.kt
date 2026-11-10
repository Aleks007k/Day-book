package com.daybook.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Одна запись дневника. createdAt — дата и время записи (можно править), в миллисекундах. */
data class Entry(
    val id: Long,
    val createdAt: Long,
    val lang: String,
    val text: String,
    val updatedAt: Long,
)

/** День в ленте: дата и его записи по времени (раньше — выше). */
data class Day(val date: LocalDate, val entries: List<Entry>)

/** Строка ленты: разделитель месяца или день. */
sealed interface FeedRow {
    val key: String

    data class Month(val year: Int, val month: Int) : FeedRow {
        override val key get() = "m-$year-$month"
    }

    data class DayRow(val day: Day) : FeedRow {
        override val key get() = "d-${day.date}"
    }
}

object Days {

    fun dateOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /**
     * Лента: сверху новые дни, только дни с записями, плюс сегодняшний
     * (даже пустой — в него пишет микрофон). Перед каждым новым месяцем —
     * разделитель.
     */
    fun feed(entries: List<Entry>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<FeedRow> {
        val byDate = entries.groupBy { dateOf(it.createdAt, zone) }.toMutableMap()
        if (today !in byDate) byDate[today] = emptyList()
        val rows = mutableListOf<FeedRow>()
        var lastMonth: Pair<Int, Int>? = null
        for (date in byDate.keys.sortedDescending()) {
            val ym = date.year to date.monthValue
            if (ym != lastMonth) {
                rows += FeedRow.Month(date.year, date.monthValue)
                lastMonth = ym
            }
            rows += FeedRow.DayRow(Day(date, byDate.getValue(date).sortedBy { it.createdAt }))
        }
        return rows
    }

    /** «1 запись», «3 записи», «5 записей». */
    fun entriesWord(n: Int): String {
        val m100 = n % 100
        val m10 = n % 10
        val w = when {
            m100 in 11..14 -> "записей"
            m10 == 1 -> "запись"
            m10 in 2..4 -> "записи"
            else -> "записей"
        }
        return "$n $w"
    }

    private val MONTHS = listOf(
        "январь", "февраль", "март", "апрель", "май", "июнь",
        "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь",
    )

    fun monthTitle(year: Int, month: Int) = "${MONTHS[month - 1]} $year"

    /** 09.11.2026 */
    fun dateText(date: LocalDate) = "%02d.%02d.%04d".format(date.dayOfMonth, date.monthValue, date.year)
}
