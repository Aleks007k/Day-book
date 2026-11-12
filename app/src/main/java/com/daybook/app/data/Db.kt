package com.daybook.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Записи дневника в файле SQLite внутри приложения (встроено в Android,
 * без сторонних библиотек). Каждое изменение сохраняется сразу.
 */
class Db(context: Context) : SQLiteOpenHelper(context, "daybook.db", null, 2) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at INTEGER NOT NULL,
                lang TEXT NOT NULL,
                text TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                tags TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX entries_created ON entries(created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Новые версии базы добавляют столбцы, не трогая записи.
        if (oldVersion < 2) db.execSQL("ALTER TABLE entries ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
    }

    fun all(): List<Entry> {
        val list = mutableListOf<Entry>()
        readableDatabase.rawQuery(
            "SELECT id, created_at, lang, text, updated_at, tags FROM entries ORDER BY created_at",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                list += Entry(
                    c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getLong(4),
                    AutoTags.parse(c.getString(5) ?: ""),
                )
            }
        }
        return list
    }

    /** Новая запись; возвращает её с номером из базы. */
    fun insert(createdAt: Long, lang: String, text: String): Entry {
        val now = System.currentTimeMillis()
        val id = writableDatabase.insertOrThrow("entries", null, values(createdAt, lang, text, now, emptyList()))
        return Entry(id, createdAt, lang, text, now)
    }

    fun update(e: Entry) {
        writableDatabase.update("entries", values(e.createdAt, e.lang, e.text, e.updatedAt, e.tags), "id = ?", arrayOf(e.id.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete("entries", "id = ?", arrayOf(id.toString()))
    }

    /** Добавить записи из копии в одной транзакции. */
    fun insertAll(items: List<Entry>): List<Entry> {
        val db = writableDatabase
        val out = mutableListOf<Entry>()
        db.beginTransaction()
        try {
            for (e in items) {
                val id = db.insertOrThrow("entries", null, values(e.createdAt, e.lang, e.text, e.updatedAt, e.tags))
                out += e.copy(id = id)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return out
    }

    private fun values(createdAt: Long, lang: String, text: String, updatedAt: Long, tags: List<String>) =
        ContentValues().apply {
            put("created_at", createdAt)
            put("lang", lang)
            put("text", text)
            put("updated_at", updatedAt)
            put("tags", AutoTags.store(tags))
        }
}
