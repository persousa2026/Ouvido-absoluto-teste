package com.ouvidoabsoluto.ai.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class MicrophoneRecorder {
    val sampleRate = 44100
    private val running = AtomicBoolean(false)
    private var record: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun start(onFrame: (ShortArray) -> Unit) {
        if (!running.compareAndSet(false, true)) return
        val frameSize = 4096
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, frameSize * 2))
        record?.startRecording()
        thread(name = "OuvidoAbsoluto-Microphone", isDaemon = true) {
            val buffer = ShortArray(frameSize)
            while (running.get()) {
                val count = record?.read(buffer, 0, buffer.size) ?: 0
                if (count > 0) onFrame(buffer.copyOf(count))
            }
        }
    }

    fun stop() {
        running.set(false)
        try { record?.stop() } catch (_: Exception) {}
        record?.release(); record = null
    }
}
