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
}
