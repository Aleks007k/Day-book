package com.daybook.app

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.daybook.app.data.Pin
import com.daybook.app.data.ThemeMode
import com.daybook.app.ui.DarkPalette
import com.daybook.app.ui.DayBookTheme
import com.daybook.app.ui.EditorScreen
import com.daybook.app.ui.FeedScreen
import com.daybook.app.ui.LightPalette
import com.daybook.app.ui.LockScreen
import com.daybook.app.ui.LockState
import com.daybook.app.ui.NoticeOverlay
import com.daybook.app.ui.SettingsScreen
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private lateinit var state: DiaryState
    private lateinit var lock: LockState
    private var stoppedAt = 0L

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            state.startRecording()
        } else {
            state.notice = Notice(
                "Без доступа к микрофону дневник не сможет записывать голос. " +
                    "Разрешите его: Настройки телефона → Приложения → Day book → Разрешения → Микрофон.",
            )
        }
    }

    private val saveBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        state.notice = Notice(
            try {
                contentResolver.openOutputStream(uri, "wt")!!.use { it.write(state.backupJson().toByteArray()) }
                "Копия сохранена: ${state.entries.size} записей."
            } catch (e: Exception) {
                "Не удалось сохранить копию."
            },
        )
    }

    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        state.notice = Notice(
            try {
                val json = contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                state.restore(json)
            } catch (e: Exception) {
                "Не удалось открыть файл."
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = DiaryState(this)
        lock = LockState(state.prefs)
        applyRecentsPrivacy()
        setContent { App() }
    }

    override fun onStart() {
        super.onStart()
        state.refreshToday()
        if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > Pin.RELOCK_AFTER_MS) lock.lock()
    }

    override fun onResume() {
        super.onResume()
        if (lock.locked && state.prefs.biometric && !lock.biometricOffered && biometricAvailable()) {
            lock.biometricOffered = true
            showBiometric()
        }
    }

    override fun onStop() {
        super.onStop()
        // Ушли с экрана — запись заканчивается, всё сказанное уже сохранено.
        state.stopRecording()
        // Вернулись позже — снова пишем в «сегодня», чтобы не ошибиться днём.
        state.resetTarget()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    /** Если включён код, в списке недавних приложений не показываем текст дневника. */
    private fun applyRecentsPrivacy() {
        setRecentsScreenshotEnabled(!state.prefs.pinEnabled)
    }

    private fun biometricAvailable(): Boolean {
        val bm = getSystemService(BiometricManager::class.java) ?: return false
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showBiometric() {
        try {
            BiometricPrompt.Builder(this)
                .setTitle("Day book")
                .setSubtitle("Открыть дневник")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .setNegativeButton("Ввести код", mainExecutor) { _, _ -> }
                .build()
                .authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        lock.unlock()
                    }
                })
        } catch (e: Exception) {
            // Отпечаток недоступен — остаётся ввод кода.
        }
    }

    private fun onMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            state.startRecording()
        } else {
            askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    @Composable
    private fun App() {
        val dark = when (state.theme) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
        val palette = if (dark) DarkPalette else LightPalette
        val view = LocalView.current
        SideEffect {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        // Пока идёт запись, экран не гаснет.
        val recording = state.recording
        DisposableEffect(recording) {
            view.keepScreenOn = recording
            onDispose { view.keepScreenOn = false }
        }
        BackHandler(enabled = !lock.locked && state.screen != Screen.Feed) { state.screen = Screen.Feed }

        DayBookTheme(palette) {
            Box(Modifier.fillMaxSize().background(palette.bg)) {
                if (lock.locked) {
                    LockScreen(lock, state.prefs.biometric && biometricAvailable()) { showBiometric() }
                } else {
                    when (val s = state.screen) {
                        Screen.Feed -> FeedScreen(state, ::onMic)
                        is Screen.Edit -> EditorScreen(state, s.entryId)
                        Screen.Settings -> SettingsScreen(
                            state,
                            biometricAvailable = biometricAvailable(),
                            onPinChanged = ::applyRecentsPrivacy,
                            onSaveBackup = { saveBackup.launch("daybook-${LocalDate.now()}.json") },
                            onRestore = { openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        )
                    }
                }
                state.notice?.let { n -> NoticeOverlay(n) { state.notice = null } }
            }
        }
    }
}
