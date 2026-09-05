package com.ouvidoabsoluto.ai.music

import kotlin.math.sqrt

class KeyDetector {
    private val major = doubleArrayOf(6.35,2.23,3.48,2.33,4.38,4.09,2.52,5.19,2.39,3.66,2.29,2.88)
    private val minor = doubleArrayOf(6.33,2.68,3.52,5.38,2.60,3.53,2.54,4.75,3.98,2.69,3.34,3.17)

    data class Candidate(val key: MusicalKey, val score: Double)

    fun detect(chroma: DoubleArray): List<Candidate> {
        if (chroma.sum() <= 0.0) return emptyList()
        val normalized = chroma.copyOf(); val total = normalized.sum(); normalized.indices.forEach { normalized[it] /= total }
        val out = mutableListOf<Candidate>()
        NoteName.entries.forEach { tonic ->
            out += Candidate(MusicalKey(tonic, KeyMode.MAJOR), normalizedCorrelation(normalized, rotated(major, tonic.ordinal)))
            out += Candidate(MusicalKey(tonic, KeyMode.MINOR), normalizedCorrelation(normalized, rotated(minor, tonic.ordinal)))
        }
        return out.sortedByDescending { it.score }
    }

    private fun rotated(profile: DoubleArray, tonic: Int) = DoubleArray(12) { pc -> profile[(pc - tonic + 12) % 12] }
    private fun normalizedCorrelation(a: DoubleArray, b: DoubleArray): Double {
        val ma = a.average(); val mb = b.average(); var n = 0.0; var da = 0.0; var db = 0.0
        for (i in a.indices) { val x = a[i] - ma; val y = b[i] - mb; n += x*y; da += x*x; db += y*y }
        val d = sqrt(da*db); return if (d == 0.0) 0.0 else ((n/d)+1.0)/2.0
    }
}
