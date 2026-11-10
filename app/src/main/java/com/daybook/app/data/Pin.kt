package com.daybook.app.data

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Код на вход: 4 цифры. Храним не сам код, а его «отпечаток» (хэш с солью),
 * чтобы код нельзя было просто прочитать из настроек.
 * Это замок от любопытных, а не шифрование дневника.
 */
object Pin {
    const val LENGTH = 4
    const val MAX_TRIES = 5
    const val PAUSE_MS = 30_000L

    /** Через сколько в фоне снова спрашивать код. */
    const val RELOCK_AFTER_MS = 60_000L

    fun isValid(code: String) = code.length == LENGTH && code.all { it in '0'..'9' }

    fun newSalt(): String {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        return b.toHex()
    }

    fun hash(code: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        var d = (salt + ":" + code).toByteArray()
        // Несколько тысяч кругов — чтобы перебор 10 000 кодов не был мгновенным.
        repeat(20_000) { d = md.digest(d) }
        return d.toHex()
    }

    fun matches(code: String, salt: String, expectedHash: String) =
        isValid(code) && MessageDigest.isEqual(hash(code, salt).toByteArray(), expectedHash.toByteArray())

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
