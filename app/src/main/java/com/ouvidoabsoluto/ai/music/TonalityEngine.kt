package com.ouvidoabsoluto.ai.music

import java.util.ArrayDeque
import kotlin.math.abs

data class NoteEvent(
    val pitchClass: Int,
    val startedAt: Long,
    val endedAt: Long,
    val averageConfidence: Double
) {
    val durationMs: Long get() = (endedAt - startedAt).coerceAtLeast(0L)
}

data class KeyEstimate(
    val best: KeyDetector.Candidate?,
    val alternatives: List<KeyDetector.Candidate>,
    val confidence: Double,
    val stable: Boolean,
    val events: Int,
    val phrases: Int,
    val relativeCandidate: KeyDetector.Candidate? = null,
    val relativeAmbiguous: Boolean = false
)

class TonalityEngine(
    private val minimumNoteDurationMs: Long = 120L,
    private val phraseSilenceMs: Long = 650L
) {
    private val detector = KeyDetector()
    private val events = mutableListOf<NoteEvent>()
    private var activePitchClass: Int? = null
    private var activeStartedAt = 0L
    private var activeLastSeenAt = 0L
    private var activeConfidenceSum = 0.0
    private var activeFrames = 0
    private var lastEventEndAt = 0L
    private var phraseEndings = IntArray(12)
    private var phraseCount = 0
    private val recentBest = ArrayDeque<MusicalKey>()

    @Synchronized
    fun reset() {
        events.clear()
        activePitchClass = null
        activeStartedAt = 0L
        activeLastSeenAt = 0L
        activeConfidenceSum = 0.0
        activeFrames = 0
        lastEventEndAt = 0L
        phraseEndings = IntArray(12)
        phraseCount = 0
        recentBest.clear()
    }

    @Synchronized
    fun addPitch(pitch: DetectedPitch, timestamp: Long): KeyEstimate {
        val pc = ((pitch.nearestMidi % 12) + 12) % 12
        if (activePitchClass == null) {
            begin(pc, pitch.confidence, timestamp)
        } else if (activePitchClass == pc) {
            activeLastSeenAt = timestamp
            activeConfidenceSum += pitch.confidence
            activeFrames++
        } else {
            closeActive(timestamp)
            begin(pc, pitch.confidence, timestamp)
        }
        return estimate(timestamp)
    }

    @Synchronized
    fun onSilence(timestamp: Long): KeyEstimate {
        if (activePitchClass != null && timestamp - activeLastSeenAt >= 180L) {
            val endingPc = activePitchClass
            closeActive(activeLastSeenAt)
            if (endingPc != null && timestamp - lastEventEndAt >= phraseSilenceMs) {
                phraseEndings[endingPc]++
                phraseCount++
            }
        }
        return estimate(timestamp)
    }

    @Synchronized
    fun finish(timestamp: Long): KeyEstimate {
        val endingPc = activePitchClass
        closeActive(timestamp)
        if (endingPc != null) {
            phraseEndings[endingPc]++
            phraseCount++
        }
        return estimate(timestamp)
    }

    private fun begin(pc: Int, confidence: Double, timestamp: Long) {
        activePitchClass = pc
        activeStartedAt = timestamp
        activeLastSeenAt = timestamp
        activeConfidenceSum = confidence
        activeFrames = 1
    }

    private fun closeActive(endAt: Long) {
        val pc = activePitchClass ?: return
        val duration = endAt - activeStartedAt
        if (duration >= minimumNoteDurationMs && activeFrames > 0) {
            events += NoteEvent(
                pitchClass = pc,
                startedAt = activeStartedAt,
                endedAt = endAt,
                averageConfidence = (activeConfidenceSum / activeFrames).coerceIn(0.0, 1.0)
            )
            lastEventEndAt = endAt
        }
        activePitchClass = null
        activeConfidenceSum = 0.0
        activeFrames = 0
    }

    private fun estimate(now: Long): KeyEstimate {
        val chroma = DoubleArray(12)
        val durations = DoubleArray(12)
        var totalDuration = 0.0
        var averagePitchConfidence = 0.0
        var confidenceCount = 0

        for (event in events) {
            val weight = event.durationMs.toDouble() * event.averageConfidence
            chroma[event.pitchClass] += weight
            durations[event.pitchClass] += event.durationMs.toDouble()
            totalDuration += event.durationMs
            averagePitchConfidence += event.averageConfidence
            confidenceCount++
        }

        val activePc = activePitchClass
        if (activePc != null) {
            val duration = (now - activeStartedAt).coerceAtLeast(0L)
            if (duration >= minimumNoteDurationMs && activeFrames > 0) {
                val confidence = (activeConfidenceSum / activeFrames).coerceIn(0.0, 1.0)
                val weight = duration * confidence
                chroma[activePc] += weight
                durations[activePc] += duration.toDouble()
                totalDuration += duration
                averagePitchConfidence += confidence
                confidenceCount++
            }
        }

        if (chroma.sum() <= 0.0) {
            return KeyEstimate(null, emptyList(), 0.0, false, events.size, phraseCount)
        }

        val base = detector.detect(chroma)
        val transitions = transitionCounts()
        val rescored = base.map { candidate ->
            val tonic = candidate.key.tonic.ordinal
            val third = (tonic + if (candidate.key.mode == KeyMode.MAJOR) 4 else 3) % 12
            val fifth = (tonic + 7) % 12
            val leadingTone = (tonic + 11) % 12
            val supertonic = (tonic + 2) % 12
            val tonicDuration = fraction(durations[tonic], totalDuration)
            val thirdDuration = fraction(durations[third], totalDuration)
            val fifthDuration = fraction(durations[fifth], totalDuration)
            val endingScore = if (phraseCount == 0) 0.0 else phraseEndings[tonic].toDouble() / phraseCount
            val modeThirdOpposite = (tonic + if (candidate.key.mode == KeyMode.MAJOR) 3 else 4) % 12
            val oppositeThird = fraction(durations[modeThirdOpposite], totalDuration)
            val modeEvidence = (thirdDuration * 2.2 - oppositeThird * 1.3).coerceIn(-0.20, 0.35)
            val leadingResolution = transitionScore(transitions, leadingTone, tonic)
            val stepResolution = transitionScore(transitions, supertonic, tonic)
            val tonicReturn = tonicReturnScore(tonic)

            val score = (
                candidate.score * 0.53 +
                    tonicDuration.coerceAtMost(0.35) * 0.62 +
                    fifthDuration.coerceAtMost(0.30) * 0.16 +
                    endingScore * 0.20 +
                    modeEvidence * 0.20 +
                    leadingResolution * 0.12 +
                    stepResolution * 0.05 +
                    tonicReturn * 0.10
                ).coerceIn(0.0, 1.0)
            KeyDetector.Candidate(candidate.key, score)
        }.sortedByDescending { it.score }

        val best = rescored.firstOrNull()
        val second = rescored.getOrNull(1)
        val relative = best?.let { winner -> rescored.firstOrNull { it.key == winner.key.relative } }
        val relativeGap = if (best != null && relative != null) abs(best.score - relative.score) else 1.0
        val relativeAmbiguous = best != null && relative != null && relativeGap < 0.075 && relative.score >= 0.50

        if (best != null) {
            recentBest.addLast(best.key)
            while (recentBest.size > 7) recentBest.removeFirst()
        }
        val stability = if (best == null || recentBest.isEmpty()) 0.0 else recentBest.count { it == best.key }.toDouble() / recentBest.size
        val margin = if (best != null && second != null) (best.score - second.score).coerceAtLeast(0.0) else 0.0
        val avgPitch = if (confidenceCount == 0) 0.0 else averagePitchConfidence / confidenceCount
        var rawConfidence = if (best == null) 0.0 else (
            best.score * 0.43 +
                margin.coerceAtMost(0.30) * 0.70 +
                stability * 0.20 +
                avgPitch * 0.10
            ).coerceIn(0.0, 1.0)

        if (relativeAmbiguous) rawConfidence = rawConfidence.coerceAtMost(0.68)
        val stable = !relativeAmbiguous && stability >= 0.65 && margin >= 0.035 && events.size >= 4
        return KeyEstimate(
            best = best,
            alternatives = rescored.drop(1).take(3),
            confidence = rawConfidence,
            stable = stable,
            events = events.size,
            phrases = phraseCount,
            relativeCandidate = relative,
            relativeAmbiguous = relativeAmbiguous
        )
    }

    private fun fraction(value: Double, total: Double): Double = if (total <= 0.0) 0.0 else value / total

    private fun transitionCounts(): Array<IntArray> {
        val counts = Array(12) { IntArray(12) }
        for (i in 0 until events.lastIndex) {
            val from = events[i].pitchClass
            val to = events[i + 1].pitchClass
            if (from != to) counts[from][to]++
        }
        return counts
    }

    private fun transitionScore(counts: Array<IntArray>, from: Int, to: Int): Double {
        val outgoing = counts[from].sum()
        if (outgoing == 0) return 0.0
        return (counts[from][to].toDouble() / outgoing).coerceIn(0.0, 1.0)
    }

    private fun tonicReturnScore(tonic: Int): Double {
        if (events.isEmpty()) return 0.0
        var returns = 0
        var opportunities = 0
        for (i in 1 until events.size) {
            if (events[i - 1].pitchClass != tonic) opportunities++
            if (events[i - 1].pitchClass != tonic && events[i].pitchClass == tonic) returns++
        }
        if (opportunities == 0) return 0.0
        return (returns.toDouble() / opportunities * 3.0).coerceIn(0.0, 1.0)
    }
}
