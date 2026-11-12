package com.daybook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.daybook.app.BuildConfig
import com.daybook.app.DiaryState
import com.daybook.app.Notice
import com.daybook.app.Screen
import com.daybook.app.data.Pin
import com.daybook.app.data.ThemeMode
import com.daybook.app.speech.Lang
import com.daybook.app.speech.Recorder

/** Шаги установки кода. */
private sealed interface PinSetup {
    data object Enter : PinSetup
    data class Confirm(val first: String) : PinSetup
}

@Composable
fun SettingsScreen(
    state: DiaryState,
    biometricAvailable: Boolean,
    onPinChanged: () -> Unit,
    onSaveBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    var setup by remember { mutableStateOf<PinSetup?>(null) }
    var input by remember { mutableStateOf("") }
    var setupError by remember { mutableStateOf<String?>(null) }
    var pinOn by remember { mutableStateOf(state.prefs.pinEnabled) }
    var bioOn by remember { mutableStateOf(state.prefs.biometric) }

    val step = setup
    if (step != null) {
        fun onDigit(c: Char) {
            if (input.length >= Pin.LENGTH) return
            input += c
            if (input.length < Pin.LENGTH) return
            when (step) {
                PinSetup.Enter -> {
                    setup = PinSetup.Confirm(input)
                    setupError = null
                }
                is PinSetup.Confirm -> if (input == step.first) {
                    state.prefs.setPin(input)
                    pinOn = true
                    setup = null
                    onPinChanged()
                    if (biometricAvailable && !state.prefs.biometric) {
                        state.notice = Notice("Открывать дневник и отпечатком пальца?", "Да") {
                            state.prefs.biometric = true
                            bioOn = true
                        }
                    }
                } else {
                    setup = PinSetup.Enter
                    setupError = "Коды не совпали — попробуйте ещё раз"
                }
            }
            input = ""
        }
        PinPad(
            title = if (step is PinSetup.Confirm) "Повторите код" else "Придумайте код",
            subtitle = setupError ?: "4 цифры. Запомните его: восстановить код нельзя.",
            error = setupError != null,
            entered = input.length,
            onDigit = ::onDigit,
            onBackspace = { input = input.dropLast(1) },
            onCancel = {
                setup = null
                input = ""
                setupError = null
            },
        )
        return
    }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SquareButton(Icons.back, { state.screen = Screen.Feed })
            Spacer(Modifier.width(14.dp))
            Txt("Настройки", Type.screenTitle, p.text)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section("Тема")
            Segments(
                ThemeMode.entries, state.theme, { it.title }, { state.chooseTheme(it) },
                Modifier.fillMaxWidth(), style = Type.labelBold,
            )

            Section("Языки")
            ToggleRow(
                "Смена языка на лету",
                "Заговорили целой фразой по-английски или по-немецки — дневник попробует переключиться сам. " +
                    "Отдельные слова внутри фразы не ловит. Работает для языков, скачанных на телефон.",
                state.languageSwitch,
            ) { state.chooseLanguageSwitch(it) }
            Txt(
                "Офлайн-пакеты: русский, английский и немецкий распознаются прямо на телефоне. " +
                    "Если пакета нет — нажмите язык ниже, телефон скачает его (бесплатно, лучше по Wi-Fi). " +
                    "Украинский — только через интернет.",
                Type.meta, p.muted, Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (l in Lang.entries.filter { it.offline }) {
                    PlateButton(l.button, {
                        state.notice = Notice(
                            if (Recorder.requestDownload(context, l)) {
                                "Телефон проверит и при необходимости скачает пакет «${l.title}». Это может занять пару минут."
                            } else {
                                "Не получилось запустить скачивание."
                            },
                        )
                    }, Modifier.weight(1f), center = true)
                }
            }

            Section("Теги")
            ToggleRow(
                "Ставить теги сами",
                "После записи дневник подбирает 1–3 тега по словам: работа, семья, спорт, еда… " +
                    "Это поиск слов, а не понимание смысла — лишнее можно убрать, нужное добавить в записи.",
                state.autoTags,
            ) { state.chooseAutoTags(it) }

            Section("Код на вход")
            if (!pinOn) {
                Txt(
                    "Код из 4 цифр — замок от любопытных, а не шифрование. " +
                        "Если забыть код, восстановить его нельзя: держите резервную копию.",
                    Type.meta, p.muted, Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
                PrimaryButton("Включить код", { setup = PinSetup.Enter }, Modifier.fillMaxWidth())
            } else {
                if (biometricAvailable) {
                    ToggleRow("Открывать отпечатком", null, bioOn) {
                        state.prefs.biometric = it
                        bioOn = it
                    }
                }
                PlateButton("Сменить код", { setup = PinSetup.Enter }, Modifier.fillMaxWidth())
                PlateButton("Выключить код", {
                    state.notice = Notice("Выключить код? Дневник будет открываться без него.", "Выключить") {
                        state.prefs.clearPin()
                        pinOn = false
                        bioOn = false
                        onPinChanged()
                    }
                }, Modifier.fillMaxWidth())
            }

            Section("Резервная копия")
            Txt(
                "Весь дневник одним файлом. Сохраните его в облако (Google Диск) или отправьте себе — " +
                    "тогда записи не пропадут, даже если телефон потеряется. Восстановление добавляет " +
                    "записи из файла, не удаляя текущие.",
                Type.meta, p.muted, Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
            PrimaryButton("Сохранить копию", onSaveBackup, Modifier.fillMaxWidth())
            PlateButton("Восстановить из копии", onRestore, Modifier.fillMaxWidth())

            Spacer(Modifier.height(18.dp))
            Txt("Day book ${BuildConfig.VERSION_NAME}", Type.meta, p.dim, Modifier.padding(horizontal = 6.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(10.dp))
    Caption(title, Modifier.padding(start = 6.dp))
}
