package com.ouvidoabsoluto.ai.audio

import com.ouvidoabsoluto.ai.music.DetectedPitch
import com.ouvidoabsoluto.ai.music.PitchConverter
import java.util.ArrayDeque
import kotlin.math.abs

class VocalPitchProcessor(
    sampleRate: Int,
    private val minConfidence: Double = 0.78,
    private val medianWindow: Int = 5
) {
    private val detector = YinPitchDetector(sampleRate)
    private val recent = ArrayDeque<Double>()
    private var previousMidi: Double? = null

    fun process(samples: ShortArray): DetectedPitch? {
        val raw = detector.detect(samples) ?: return null
        if (raw.confidence < minConfidence) return null

        recent.addLast(raw.frequency)
        while (recent.size > medianWindow) recent.removeFirst()
        val smoothedFrequency = recent.toList().sorted()[recent.size / 2]
        var pitch = PitchConverter.fromFrequency(smoothedFrequency, raw.confidence)

        val previous = previousMidi
        if (previous != null) {
            val candidates = listOf(pitch.midiFloat - 12.0, pitch.midiFloat, pitch.midiFloat + 12.0)
            val nearest = candidates.minBy { abs(it - previous) }
            // Corrige apenas saltos com aparência de erro de oitava; preserva saltos melódicos reais.
            if (abs(pitch.midiFloat - previous) > 8.0 && abs(nearest - previous) < 3.5) {
                val correctedFrequency = 440.0 * Math.pow(2.0, (nearest - 69.0) / 12.0)
                pitch = PitchConverter.fromFrequency(correctedFrequency, raw.confidence)
            }
        }

        previousMidi = pitch.midiFloat
        return pitch
    }

    fun reset() {
        recent.clear()
        previousMidi = null
    }
}
