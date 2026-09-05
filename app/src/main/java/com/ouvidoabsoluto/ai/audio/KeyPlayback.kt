package com.ouvidoabsoluto.ai.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.ouvidoabsoluto.ai.music.KeyMode
import com.ouvidoabsoluto.ai.music.MusicalKey
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class KeyPlayback(
    private val sampleRate: Int = 44100
) {
    fun playTonic(key: MusicalKey) {
        val midi = 60 + key.tonic.ordinal
        playFrequencies(listOf(midiToFrequency(midi)), 1200)
    }

    fun playChord(key: MusicalKey) {
        val root = 60 + key.tonic.ordinal
        val third = root + if (key.mode == KeyMode.MAJOR) 4 else 3
        val fifth = root + 7
        playFrequencies(listOf(root, third, fifth).map(::midiToFrequency), 1500)
    }

    fun playScale(key: MusicalKey) {
        val intervals = if (key.mode == KeyMode.MAJOR) {
            intArrayOf(0, 2, 4, 5, 7, 9, 11, 12)
        } else {
            intArrayOf(0, 2, 3, 5, 7, 8, 10, 12)
        }
        val root = 60 + key.tonic.ordinal
        thread(name = "OuvidoAbsoluto-KeyPlayback", isDaemon = true) {
            intervals.forEach { interval ->
                playFrequenciesBlocking(listOf(midiToFrequency(root + interval)), 330)
                Thread.sleep(35)
            }
        }
    }

    private fun playFrequencies(frequencies: List<Double>, durationMs: Int) {
        thread(name = "OuvidoAbsoluto-KeyPlayback", isDaemon = true) {
            playFrequenciesBlocking(frequencies, durationMs)
        }
    }

    private fun playFrequenciesBlocking(frequencies: List<Double>, durationMs: Int) {
        if (frequencies.isEmpty()) return
        val count = (sampleRate * durationMs / 1000.0).toInt().coerceAtLeast(1)
        val pcm = ShortArray(count)
        val fade = (sampleRate * 0.02).toInt().coerceAtLeast(1)
        for (i in pcm.indices) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0
            frequencies.forEach { frequency -> sample += sin(2.0 * PI * frequency * t) }
            sample /= frequencies.size
            val edgeGain = when {
                i < fade -> i.toDouble() / fade
                i > pcm.lastIndex - fade -> (pcm.lastIndex - i).coerceAtLeast(0).toDouble() / fade
                else -> 1.0
            }
            pcm[i] = (sample * edgeGain * Short.MAX_VALUE * 0.55).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            val waitMs = durationMs.toLong() + 80L
            Thread.sleep(waitMs)
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private fun midiToFrequency(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)
}
