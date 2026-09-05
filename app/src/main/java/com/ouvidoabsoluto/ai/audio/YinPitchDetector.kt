package com.ouvidoabsoluto.ai.audio

import com.ouvidoabsoluto.ai.music.DetectedPitch
import com.ouvidoabsoluto.ai.music.PitchConverter
import kotlin.math.sqrt

class YinPitchDetector(private val sampleRate: Int = 44100, private val threshold: Double = 0.15) {
    fun detect(samples: ShortArray, minFrequency: Double = 70.0, maxFrequency: Double = 1000.0): DetectedPitch? {
        if (samples.size < 1024 || rms(samples) < 0.012) return null
        val x = DoubleArray(samples.size)
        val mean = samples.map { it.toDouble() }.average()
        for (i in samples.indices) x[i] = (samples[i] - mean) / Short.MAX_VALUE
        val minTau = (sampleRate / maxFrequency).toInt().coerceAtLeast(2)
        val maxTau = (sampleRate / minFrequency).toInt().coerceAtMost(x.size / 2)
        if (minTau >= maxTau) return null
        val diff = DoubleArray(maxTau + 1)
        val limit = x.size - maxTau
        for (tau in 1..maxTau) {
            var sum = 0.0
            for (i in 0 until limit) { val d = x[i] - x[i + tau]; sum += d * d }
            diff[tau] = sum
        }
        val cmnd = DoubleArray(diff.size); cmnd[0] = 1.0
        var running = 0.0
        for (tau in 1 until diff.size) { running += diff[tau]; cmnd[tau] = if (running == 0.0) 1.0 else diff[tau] * tau / running }
        var tau = minTau
        var found = -1
        while (tau <= maxTau) {
            if (cmnd[tau] < threshold) {
                while (tau + 1 <= maxTau && cmnd[tau + 1] < cmnd[tau]) tau++
                found = tau; break
            }
            tau++
        }
        if (found < 0) return null
        val confidence = (1.0 - cmnd[found]).coerceIn(0.0, 1.0)
        if (confidence < 0.70) return null
        val refinedTau = parabolicInterpolation(cmnd, found)
        if (refinedTau <= 0.0) return null
        val frequency = sampleRate.toDouble() / refinedTau
        if (frequency !in minFrequency..maxFrequency) return null
        return PitchConverter.fromFrequency(frequency, confidence)
    }

    private fun parabolicInterpolation(values: DoubleArray, tau: Int): Double {
        if (tau <= 0 || tau >= values.lastIndex) return tau.toDouble()
        val left = values[tau - 1]
        val center = values[tau]
        val right = values[tau + 1]
        val denominator = 2.0 * (2.0 * center - right - left)
        if (kotlin.math.abs(denominator) < 1e-12) return tau.toDouble()
        return tau + (right - left) / denominator
    }

    private fun rms(samples: ShortArray): Double {
        var sum = 0.0
        samples.forEach { val n = it.toDouble() / Short.MAX_VALUE; sum += n * n }
        return sqrt(sum / samples.size)
    }
}
