package com.ouvidoabsoluto.ai.music

import kotlin.math.ln
import kotlin.math.roundToInt

enum class NoteName(val pt: String, val symbol: String) {
    C("Dó", "C"), CS("Dó♯", "C#"), D("Ré", "D"), DS("Ré♯", "D#"), E("Mi", "E"), F("Fá", "F"),
    FS("Fá♯", "F#"), G("Sol", "G"), GS("Sol♯", "G#"), A("Lá", "A"), AS("Lá♯", "A#"), B("Si", "B")
}

enum class KeyMode { MAJOR, MINOR }

data class MusicalKey(val tonic: NoteName, val mode: KeyMode) {
    val displayName: String get() = "${tonic.pt} ${if (mode == KeyMode.MAJOR) "Maior" else "Menor"}"
    val symbol: String get() = tonic.symbol + if (mode == KeyMode.MINOR) "m" else ""

    val relative: MusicalKey
        get() = if (mode == KeyMode.MAJOR) {
            MusicalKey(NoteName.entries[(tonic.ordinal + 9) % 12], KeyMode.MINOR)
        } else {
            MusicalKey(NoteName.entries[(tonic.ordinal + 3) % 12], KeyMode.MAJOR)
        }

    val scaleNotes: List<NoteName>
        get() {
            val intervals = if (mode == KeyMode.MAJOR) intArrayOf(0, 2, 4, 5, 7, 9, 11) else intArrayOf(0, 2, 3, 5, 7, 8, 10)
            return intervals.map { NoteName.entries[(tonic.ordinal + it) % 12] }
        }

    val chordNotes: List<NoteName>
        get() {
            val third = if (mode == KeyMode.MAJOR) 4 else 3
            return listOf(0, third, 7).map { NoteName.entries[(tonic.ordinal + it) % 12] }
        }
}

data class DetectedPitch(
    val frequency: Double,
    val midiFloat: Double,
    val nearestMidi: Int,
    val note: NoteName,
    val octave: Int,
    val cents: Double,
    val confidence: Double
)

object PitchConverter {
    fun fromFrequency(frequency: Double, confidence: Double): DetectedPitch {
        val midiFloat = 69.0 + 12.0 * (ln(frequency / 440.0) / ln(2.0))
        val midi = midiFloat.roundToInt()
        val pitchClass = ((midi % 12) + 12) % 12
        return DetectedPitch(frequency, midiFloat, midi, NoteName.entries[pitchClass], midi / 12 - 1, (midiFloat - midi) * 100.0, confidence)
    }
}
