package com.daybook.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Резервная копия: весь дневник одним файлом JSON (обычный текст —
 * откроется и через много лет). Код на вход в копию не попадает.
 */
object Backup {
    private const val FORMAT = "daybook"
    private const val VERSION = 1

    fun toJson(entries: List<Entry>): String {
        val arr = JSONArray()
        for (e in entries.sortedBy { it.createdAt }) {
            arr.put(
                JSONObject()
                    .put("createdAt", e.createdAt)
                    .put("lang", e.lang)
                    .put("text", e.text)
                    .put("updatedAt", e.updatedAt)
                    .put("tags", JSONArray(e.tags)),
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("entries", arr)
            .toString(1)
    }

    /** Разобрать файл копии. Бросает исключение с понятным текстом, если файл не тот. */
    fun parse(json: String): List<Entry> {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("Это не файл копии дневника.")
        }
        if (root.optString("format") != FORMAT) throw IllegalArgumentException("Это не файл копии дневника.")
        if (root.optInt("version", 0) > VERSION) {
            throw IllegalArgumentException("Копия сделана более новой версией приложения — сначала обновите приложение.")
        }
        val arr = root.optJSONArray("entries") ?: JSONArray()
        val out = mutableListOf<Entry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val created = o.getLong("createdAt")
            val tagsArr = o.optJSONArray("tags")
            val tags = if (tagsArr == null) emptyList() else (0 until tagsArr.length()).map { tagsArr.optString(it) }
            out += Entry(
                0, created, o.optString("lang", "RU"), o.optString("text", ""), o.optLong("updatedAt", created),
                AutoTags.parse(tags.joinToString(",")),
            )
        }
        return out
    }

    /** Какие записи из копии ещё не в дневнике (одинаковые время и текст — пропускаем). */
    fun newOnly(fromBackup: List<Entry>, existing: List<Entry>): List<Entry> {
        val have = existing.map { it.createdAt to it.text }.toHashSet()
        return fromBackup.filter { (it.createdAt to it.text) !in have && it.text.isNotBlank() }
            .distinctBy { it.createdAt to it.text }
    }
}
