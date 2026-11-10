package com.daybook.app.speech

/**
 * Четыре языка дневника.
 * tag — код для распознавателя; offline — распознаётся прямо на телефоне
 * (по итогам этапа 1: RU, EN, DE — да; UK — только через интернет, Google).
 */
enum class Lang(val button: String, val tag: String, val offline: Boolean, val title: String) {
    RU("RU", "ru-RU", true, "русский"),
    UK("UK", "uk-UA", false, "украинский"),
    EN("EN", "en-US", true, "английский"),
    DE("DE", "de-DE", true, "немецкий");

    companion object {
        fun of(name: String?): Lang = entries.firstOrNull { it.name == name } ?: RU
    }
}
