package com.example.lungcapacity.pcda76db4.domain.usecase

import com.example.lungcapacity.pcda76db4.domain.model.Difficulty
import com.example.lungcapacity.pcda76db4.domain.model.cakeTiers
import kotlin.math.pow

data class AssemblySample(
    val visible: Boolean,
    val progress: Float,
    val verticalOffset: Float,
)

data class CandleInsertionSample(
    val visible: Boolean,
    val inserted: Boolean,
    val progress: Float,
    val verticalOffset: Float,
    val tiltRadians: Float,
)

enum class CakeAssemblyPartKind {
    SUPPORT,
    LARGE_BODY,
    SMALL,
}

object CakeAssemblyTimeline {
    const val CANDLE_LIFT = 0.55f

    fun assemblySeconds(difficulty: Difficulty): Float {
        val timing = timing(difficulty)
        val supportEnd = timing.support.endSeconds(
            difficulty = difficulty,
            durationFraction = SOURCE_PART_DURATION_FRACTION,
        )
        val bodyEnd = timing.largeBodies.maxOf { track ->
            track.endSeconds(difficulty, SOURCE_PART_DURATION_FRACTION)
        }
        val smallPartEnd = timing.smallParts.mapIndexed { tierIndex, track ->
            track.endSeconds(difficulty, SOURCE_PART_DURATION_FRACTION) +
                lastSmallPartSequence(difficulty, tierIndex) *
                timing.smallPartStaggerSeconds
        }.max()
        val candleEnd = timing.candles.maxOf { track ->
            track.endSeconds(difficulty, SOURCE_CANDLE_DURATION_FRACTION) +
                (CANDLES_PER_TIER - 1) * timing.candleSlotStaggerSeconds
        }
        return maxOf(supportEnd, bodyEnd, smallPartEnd, candleEnd)
    }

    fun part(
        elapsedSeconds: Float,
        difficulty: Difficulty,
        tierIndex: Int,
        kind: CakeAssemblyPartKind,
        sequence: Int,
        travel: Float,
    ): AssemblySample {
        require(tierIndex in difficulty.cakeTiers.indices)
        require(sequence >= 0)
        val timing = timing(difficulty)
        val track = when (kind) {
            CakeAssemblyPartKind.SUPPORT -> timing.support
            CakeAssemblyPartKind.LARGE_BODY -> timing.largeBodies[tierIndex]
            CakeAssemblyPartKind.SMALL -> timing.smallParts[tierIndex]
        }
        val start = track.startSeconds + if (kind == CakeAssemblyPartKind.SMALL) {
            sequence * timing.smallPartStaggerSeconds
        } else {
            0f
        }
        val transitionSeconds = track.durationSeconds(
            difficulty = difficulty,
            durationFraction = SOURCE_PART_DURATION_FRACTION,
        )
        val linear = ((elapsedSeconds - start) / transitionSeconds).coerceIn(0f, 1f)
        val eased = easeOutCubic(linear)
        val sourceTravel = if (travel == 0f) 0.5f else travel
        return AssemblySample(
            visible = elapsedSeconds >= start,
            progress = eased,
            verticalOffset = (1f - eased) * sourceTravel,
        )
    }

    fun candle(
        elapsedSeconds: Float,
        difficulty: Difficulty,
        tierIndex: Int,
        slotIndex: Int,
    ): CandleInsertionSample {
        require(tierIndex in difficulty.cakeTiers.indices)
        require(slotIndex in 0 until CANDLES_PER_TIER)
        val timing = timing(difficulty)
        val track = timing.candles[tierIndex]
        val duration = track.durationSeconds(
            difficulty = difficulty,
            durationFraction = SOURCE_CANDLE_DURATION_FRACTION,
        )
        val start = track.startSeconds + slotIndex * timing.candleSlotStaggerSeconds
        val end = start + duration
        val linear = if (elapsedSeconds >= end) {
            1f
        } else {
            ((elapsedSeconds - start) / duration).coerceIn(0f, 1f)
        }
        val eased = easeOutCubic(linear)
        return CandleInsertionSample(
            visible = elapsedSeconds >= start,
            inserted = elapsedSeconds >= end,
            progress = linear,
            verticalOffset = CANDLE_LIFT * (1f - eased),
            tiltRadians = 0.13f * (1f - eased) *
                if (slotIndex % 2 == 0) 1f else -1f,
        )
    }

    private fun lastSmallPartSequence(
        difficulty: Difficulty,
        tierIndex: Int,
    ): Int =
        when (difficulty) {
            Difficulty.EASY -> 12
            Difficulty.NORMAL -> if (tierIndex == 0) 2 else 10
            Difficulty.HARD -> listOf(30, 24, 14)[tierIndex]
        }

    private fun timing(difficulty: Difficulty): DifficultyTiming =
        requireNotNull(TIMINGS[difficulty])

    private fun sourcePaceSeconds(difficulty: Difficulty): Float =
        when (difficulty) {
            Difficulty.EASY -> 3.4f
            Difficulty.NORMAL -> 4.2f
            Difficulty.HARD -> 5f
        }

    private fun easeOutCubic(value: Float): Float = 1f - (1f - value).pow(3)

    private fun TrackTiming.durationSeconds(
        difficulty: Difficulty,
        durationFraction: Float,
    ): Float =
        sourcePaceSeconds(difficulty) * durationFraction * durationScale

    private fun TrackTiming.endSeconds(
        difficulty: Difficulty,
        durationFraction: Float,
    ): Float =
        startSeconds + durationSeconds(difficulty, durationFraction)

    private data class TrackTiming(
        val startSeconds: Float,
        val durationScale: Float,
    )

    private data class DifficultyTiming(
        val support: TrackTiming,
        val largeBodies: List<TrackTiming>,
        val smallParts: List<TrackTiming>,
        val candles: List<TrackTiming>,
        val smallPartStaggerSeconds: Float,
        val candleSlotStaggerSeconds: Float,
    )

    private const val SOURCE_PART_DURATION_FRACTION = 0.05f
    private const val SOURCE_CANDLE_DURATION_FRACTION = 0.07f
    private const val CANDLES_PER_TIER = 8

    private val TIMINGS = mapOf(
        Difficulty.EASY to DifficultyTiming(
            support = TrackTiming(startSeconds = 0f, durationScale = 3f),
            largeBodies = listOf(
                TrackTiming(startSeconds = 0.3f, durationScale = 4f),
            ),
            smallParts = listOf(
                TrackTiming(startSeconds = 0.6f, durationScale = 3f),
            ),
            candles = listOf(
                TrackTiming(startSeconds = 1f, durationScale = 3f),
            ),
            smallPartStaggerSeconds = 0.03f,
            candleSlotStaggerSeconds = 0.05f,
        ),
        Difficulty.NORMAL to DifficultyTiming(
            support = TrackTiming(startSeconds = 0f, durationScale = 1.5f),
            largeBodies = listOf(
                TrackTiming(startSeconds = 0.2f, durationScale = 2f),
                TrackTiming(startSeconds = 0.5f, durationScale = 2f),
            ),
            smallParts = listOf(
                TrackTiming(startSeconds = 0.8f, durationScale = 3f),
                TrackTiming(startSeconds = 1.1f, durationScale = 4f),
            ),
            candles = listOf(
                TrackTiming(startSeconds = 1.5f, durationScale = 2f),
                TrackTiming(startSeconds = 1.8f, durationScale = 2f),
            ),
            smallPartStaggerSeconds = 0.03f,
            candleSlotStaggerSeconds = 0.05f,
        ),
        Difficulty.HARD to DifficultyTiming(
            support = TrackTiming(startSeconds = 0f, durationScale = 1.5f),
            largeBodies = listOf(
                TrackTiming(startSeconds = 0.2f, durationScale = 2f),
                TrackTiming(startSeconds = 0.5f, durationScale = 2f),
                TrackTiming(startSeconds = 0.8f, durationScale = 2f),
            ),
            smallParts = listOf(
                TrackTiming(startSeconds = 0.7f, durationScale = 2.5f),
                TrackTiming(startSeconds = 1.2f, durationScale = 2.5f),
                TrackTiming(startSeconds = 1.7f, durationScale = 2.5f),
            ),
            candles = listOf(
                TrackTiming(startSeconds = 1.2f, durationScale = 2f),
                TrackTiming(startSeconds = 1.7f, durationScale = 2f),
                TrackTiming(startSeconds = 2.2f, durationScale = 2f),
            ),
            smallPartStaggerSeconds = 0.03f,
            candleSlotStaggerSeconds = 0.05f,
        ),
    )
}
