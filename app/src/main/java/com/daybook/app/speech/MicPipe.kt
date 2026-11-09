package com.daybook.app.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import java.io.IOException
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Режим C «свой микрофон»: приложение само пишет звук с микрофона и
 * непрерывно передаёт его распознавателю через трубу (pipe). Тогда
 * распознаватель не выключает микрофон на паузах — нет перезапусков,
 * разрывов и писка. Сеанс заканчивается, когда мы закрываем трубу.
 *
 * Звук никуда не сохраняется: он идёт только в распознаватель.
 */
class MicPipe(private val onLevel: (Float) -> Unit) {

    /** Конец трубы, который отдаём распознавателю. */
    var readSide: ParcelFileDescriptor? = null
        private set

    private var writeSide: ParcelFileDescriptor? = null
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    /** Открыть микрофон и трубу. Возвращает текст ошибки или null, если всё в порядке. */
    @SuppressLint("MissingPermission") // разрешение на микрофон проверяется до начала проверки
    fun open(): String? {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return "микрофон не даёт размер буфера ($minBuf)"
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 4,
            )
        } catch (e: Exception) {
            return "микрофон не открылся: ${e.javaClass.simpleName}"
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return "микрофон не открылся"
        }
        val pipe = try {
            ParcelFileDescriptor.createPipe()
        } catch (e: IOException) {
            rec.release()
            return "труба не создалась: ${e.javaClass.simpleName}"
        }
        record = rec
        readSide = pipe[0]
        writeSide = pipe[1]
        running = true
        rec.startRecording()
        val out = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        thread = Thread({
            val buf = ShortArray(minBuf / 2)
            val bytes = ByteArray(buf.size * 2)
            var frames = 0
            try {
                while (running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    var sum = 0.0
                    for (i in 0 until n) {
                        val s = buf[i].toInt()
                        bytes[2 * i] = (s and 0xFF).toByte()
                        bytes[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
                        sum += s.toDouble() * s
                    }
                    out.write(bytes, 0, n * 2)
                    // Уровень звука для полоски на экране — примерно раз в 0,1 с.
                    frames += n
                    if (frames >= SAMPLE_RATE / 10) {
                        frames = 0
                        val rms = sqrt(sum / n)
                        onLevel(if (rms > 1) (20 * log10(rms / 32768.0) / 6 + 10).toFloat() else -2f)
                    }
                }
            } catch (_: IOException) {
                // Распознаватель закрыл свой конец трубы — сеанс окончен.
            } finally {
                try {
                    out.close()
                } catch (_: IOException) {
                }
            }
        }, "daybook-mic").also { it.start() }
        return null
    }

    /** Закрыть микрофон и трубу: распознаватель увидит конец звука и допишет последний кусок. */
    fun close() {
        running = false
        try {
            record?.stop()
        } catch (_: Exception) {
        }
        thread?.join(500)
        thread = null
        record?.release()
        record = null
        try {
            writeSide?.close()
        } catch (_: IOException) {
        }
        writeSide = null
        try {
            readSide?.close()
        } catch (_: IOException) {
        }
        readSide = null
    }

    companion object {
        const val SAMPLE_RATE = 16000
    }
}
