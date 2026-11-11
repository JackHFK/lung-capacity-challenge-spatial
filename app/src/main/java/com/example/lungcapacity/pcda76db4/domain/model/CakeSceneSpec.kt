package com.example.lungcapacity.pcda76db4.domain.model

data class CakeTierSpec(
    val index: Int,
    val radius: Float,
    val baseY: Float,
    val height: Float,
    val surfaceY: Float,
    val nextRadius: Float,
)

data class CandlePlacement(
    val index: Int,
    val tierIndex: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val angleDegrees: Float,
)

val Difficulty.cakeTiers: List<CakeTierSpec>
    get() = when (this) {
        Difficulty.EASY -> listOf(
            CakeTierSpec(0, 0.985f, 0.36f, 0.59f, 0.97f, 0f),
        )
        Difficulty.NORMAL -> listOf(
            CakeTierSpec(0, 1f, 0.03f, 0.57f, 0.60f, 0.74f),
            CakeTierSpec(1, 0.74f, 0.58f, 0.68f, 1.26f, 0f),
        )
        Difficulty.HARD -> listOf(
            CakeTierSpec(0, 1f, 0.03f, 0.58f, 0.61f, 0.70f),
            CakeTierSpec(1, 0.70f, 0.59f, 0.48f, 1.07f, 0.45f),
            CakeTierSpec(2, 0.45f, 1.05f, 0.46f, 1.51f, 0f),
        )
    }
