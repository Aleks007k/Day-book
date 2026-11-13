package com.daybook.app.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daybook.app.data.Pin
import com.daybook.app.data.Prefs
import kotlinx.coroutines.delay

/** Заперт ли дневник и сколько неверных попыток было. */
class LockState(private val prefs: Prefs) {
    var locked by mutableStateOf(prefs.pinEnabled)
        private set
    var input by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    private var wrong by mutableIntStateOf(0)
    var pausedUntil by mutableLongStateOf(0L)
        private set

    /** Отпечаток уже предлагали для этого запирания (чтобы не показывать окно по кругу). */
    var biometricOffered = false

    fun lock() {
        if (!prefs.pinEnabled) return
        locked = true
        input = ""
        error = null
        biometricOffered = false
    }

    fun unlock() {
        locked = false
        input = ""
        error = null
        wrong = 0
    }

    fun digit(d: Char) {
        if (SystemClock.elapsedRealtime() < pausedUntil) return
        if (input.length >= Pin.LENGTH) return
        input += d
        error = null
        if (input.length == Pin.LENGTH) {
            if (prefs.checkPin(input)) {
                unlock()
            } else {
                wrong++
                input = ""
                if (wrong >= Pin.MAX_TRIES) {
                    wrong = 0
                    pausedUntil = SystemClock.elapsedRealtime() + Pin.PAUSE_MS
                    error = "Много неверных попыток. Подождите 30 секунд."
                } else {
                    error = "Неверный код"
                }
            }
        }
    }

    fun backspace() {
        input = input.dropLast(1)
    }
}

/** Экран замка: код или отпечаток. */
@Composable
fun LockScreen(lock: LockState, biometric: Boolean, onBiometric: () -> Unit) {
    var now by androidx.compose.runtime.remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(lock.pausedUntil) {
        while (SystemClock.elapsedRealtime() < lock.pausedUntil) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
        now = SystemClock.elapsedRealtime()
    }
    val wait = ((lock.pausedUntil - now + 999) / 1000).coerceAtLeast(0)
    PinPad(
        title = "Day-book",
        subtitle = when {
            wait > 0 -> "Подождите $wait с"
            lock.error != null -> lock.error!!
            else -> "Введите код"
        },
        error = lock.error != null || wait > 0,
        entered = lock.input.length,
        onDigit = lock::digit,
        onBackspace = lock::backspace,
        onBiometric = if (biometric) onBiometric else null,
    )
}

/** Клавиатура из цифр с точками сверху. Используется и для замка, и для установки кода. */
@Composable
fun PinPad(
    title: String,
    subtitle: String,
    error: Boolean,
    entered: Int,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (onCancel != null) {
            Box(Modifier.align(Alignment.Start)) { SquareButton(Icons.back, onCancel) }
        } else {
            Spacer(Modifier.height(46.dp))
        }
        Spacer(Modifier.weight(1f))
        Txt(title, Type.title, p.text, align = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Txt(subtitle, Type.label, if (error) p.rec else p.muted, align = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(Pin.LENGTH) { i ->
                val filled = i < entered
                Box(
                    Modifier.size(14.dp).clip(CircleShape)
                        .background(if (filled) p.button else p.bg)
                        .border(1.5.dp, if (filled) p.button else p.line, CircleShape),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        val rows = listOf("123", "456", "789")
        for (r in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { for (c in r) Key(c.toString()) { onDigit(c) } }
            Spacer(Modifier.height(14.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (onBiometric != null) {
                KeyIcon(Icons.fingerprint, onBiometric)
            } else {
                Spacer(Modifier.size(76.dp))
            }
            Key("0") { onDigit('0') }
            KeyIcon(Icons.backspace, onBackspace)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(76.dp).clip(CircleShape).background(p.plate).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Txt(label, Type.pinDigit, p.text) }
}

@Composable
private fun KeyIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.size(76.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, p.text, 28.dp) }
}
