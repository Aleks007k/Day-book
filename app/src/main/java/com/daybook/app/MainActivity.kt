package com.daybook.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private lateinit var controller: TestController

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            controller.start()
        } else {
            controller.notice = "Без доступа к микрофону проверка невозможна. " +
                "Разрешите его: Настройки → Приложения → Дневник → Разрешения."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        controller = TestController(this)
        controller.checkAllSupport()
        setContent {
            TestScreen(controller, onStart = ::startWithPermission)
        }
    }

    private fun startWithPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            controller.start()
        } else {
            askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onStop() {
        super.onStop()
        // Ушли с экрана (звонок, другое приложение) — завершаем проверку,
        // записанное остаётся в журнале.
        controller.stop("приложение ушло с экрана")
    }
}
