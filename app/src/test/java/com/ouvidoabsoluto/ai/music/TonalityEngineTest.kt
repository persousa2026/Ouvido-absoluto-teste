package com.ouvidoabsoluto.ai.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TonalityEngineTest {
    @Test
    fun pitchConverter_mapsA4() {
        val pitch = PitchConverter.fromFrequency(440.0, 1.0)
        assertEquals(NoteName.A, pitch.note)
        assertEquals(4, pitch.octave)
        assertTrue(kotlin.math.abs(pitch.cents) < 0.01)
    }

    @Test
    fun keyDetector_prefersCMajorForCMajorProfile() {
        val detector = KeyDetector()
        val chroma = doubleArrayOf(6.35,2.23,3.48,2.33,4.38,4.09,2.52,5.19,2.39,3.66,2.29,2.88)
        val best = detector.detect(chroma).first()
        assertEquals(MusicalKey(NoteName.C, KeyMode.MAJOR), best.key)
    }

    @Test
    fun relativeKey_mapsMajorAndMinorBothWays() {
        val gMajor = MusicalKey(NoteName.G, KeyMode.MAJOR)
        assertEquals(MusicalKey(NoteName.E, KeyMode.MINOR), gMajor.relative)
        assertEquals(gMajor, gMajor.relative.relative)
    }

    @Test
    fun scaleAndChord_areCorrectForDMajor() {
        val d = MusicalKey(NoteName.D, KeyMode.MAJOR)
        assertEquals(listOf(NoteName.D, NoteName.E, NoteName.FS, NoteName.G, NoteName.A, NoteName.B, NoteName.CS), d.scaleNotes)
        assertEquals(listOf(NoteName.D, NoteName.FS, NoteName.A), d.chordNotes)
    }

    @Test
    fun repeatedSingleNote_neverProducesStableKey() {
        val engine = TonalityEngine()
        var estimate: KeyEstimate? = null
        repeat(20) { index ->
            estimate = engine.addPitch(pitch(NoteName.C), index * 200L)
        }
        estimate = engine.finish(4200L)
        assertEquals(SampleQuality.INSUFFICIENT, estimate!!.sampleQuality)
        assertTrue(!estimate!!.stable)
        assertTrue(estimate!!.confidence <= 0.62)
    }

    @Test
    fun completeCMajorPhrase_hasEnoughVariety() {
        val engine = TonalityEngine()
        val melody = listOf(NoteName.C, NoteName.E, NoteName.G, NoteName.B, NoteName.C, NoteName.G, NoteName.E, NoteName.C)
        var time = 0L
        melody.forEach { note ->
            repeat(5) {
                engine.addPitch(pitch(note), time)
                time += 120L
            }
        }
        val result = engine.finish(time)
        assertEquals(SampleQuality.GOOD, result.sampleQuality)
        assertTrue(result.uniqueNotes >= 4)
    }

    private fun pitch(note: NoteName): DetectedPitch {
        val midi = 60 + note.ordinal
        val frequency = 440.0 * Math.pow(2.0, (midi - 69.0) / 12.0)
        return PitchConverter.fromFrequency(frequency, 0.96)
    }
}
