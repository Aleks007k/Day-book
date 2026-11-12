package com.daybook.app.data

import android.content.Context
import com.daybook.app.speech.Lang

enum class ThemeMode(val title: String) { SYSTEM("как в телефоне"), DARK("тёмная"), LIGHT("светлая") }

/** Настройки дневника (небольшие значения, не записи). */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("daybook", Context.MODE_PRIVATE)

    var lang: Lang
        get() = Lang.of(p.getString("lang", null))
        set(v) = p.edit().putString("lang", v.name).apply()

    var theme: ThemeMode
        get() = ThemeMode.entries.firstOrNull { it.name == p.getString("theme", null) } ?: ThemeMode.SYSTEM
        set(v) = p.edit().putString("theme", v.name).apply()

    /** Смена языка на лету (только для языков, распознаваемых на телефоне). */
    var languageSwitch: Boolean
        get() = p.getBoolean("switch", true)
        set(v) = p.edit().putBoolean("switch", v).apply()

    /** Ставить теги самим по словарю (AutoTags). */
    var autoTags: Boolean
        get() = p.getBoolean("auto_tags", true)
        set(v) = p.edit().putBoolean("auto_tags", v).apply()

    /** Старым записям (до появления тегов) теги уже подобраны. */
    var tagsBackfilled: Boolean
        get() = p.getBoolean("tags_backfilled", false)
        set(v) = p.edit().putBoolean("tags_backfilled", v).apply()

    /** Свои теги владельца (кроме словаря), через запятую. */
    var customTags: List<String>
        get() = AutoTags.parse(p.getString("custom_tags", "") ?: "")
        set(v) = p.edit().putString("custom_tags", AutoTags.store(v)).apply()

    /** Предупреждение «украинский идёт через интернет» уже показано. */
    var ukNoticeShown: Boolean
        get() = p.getBoolean("uk_notice", false)
        set(v) = p.edit().putBoolean("uk_notice", v).apply()

    val pinEnabled: Boolean get() = p.getString("pin_hash", null) != null

    fun setPin(code: String) {
        val salt = Pin.newSalt()
        p.edit().putString("pin_salt", salt).putString("pin_hash", Pin.hash(code, salt)).apply()
    }

    fun clearPin() {
        p.edit().remove("pin_salt").remove("pin_hash").putBoolean("biometric", false).apply()
    }

    fun checkPin(code: String): Boolean {
        val salt = p.getString("pin_salt", null) ?: return false
        val hash = p.getString("pin_hash", null) ?: return false
        return Pin.matches(code, salt, hash)
    }

    var biometric: Boolean
        get() = p.getBoolean("biometric", false)
        set(v) = p.edit().putBoolean("biometric", v).apply()
}
