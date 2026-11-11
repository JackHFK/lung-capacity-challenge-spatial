package com.example.lungcapacity.pcda76db4.domain.model

enum class GamePage { TRIAL, GROWTH }

enum class Difficulty(
    val label: String,
    val candleCount: Int,
    val flameThreshold: Float,
    val title: String,
    val description: String,
) {
    EASY("简单", 8, 0.4f, "单层奶油蛋糕", "8 根蜡烛围绕草莓奶油蛋糕，适合熟悉呼气节奏。"),
    NORMAL("普通", 16, 0.6f, "双层奶油蛋糕", "两层台面各有 8 根蜡烛，需要稳定呼气并转头追踪。"),
    HARD("困难", 24, 0.8f, "三层浆果蛋糕", "三层台面各有 8 根蜡烛，目标高度与方向变化更大。"),
}

enum class GrowthNodeId {
    FORCE,
    DENSITY,
    SPREAD,
    LIFETIME,
    CHARGE,
    FIRST_SPLIT,
    CHILD_DRIFT,
    SECOND_BLOOM,
    EXTINGUISH_WAVE,
}

data class GrowthRequirement(val nodeId: GrowthNodeId, val rank: Int)

data class GrowthSpec(
    val id: GrowthNodeId,
    val label: String,
    val rune: String,
    val category: String,
    val maxRank: Int,
    val costs: List<Int>,
    val description: String,
    val prerequisites: List<GrowthRequirement> = emptyList(),
)

object GrowthCatalog {
    val specs: List<GrowthSpec> = listOf(
        GrowthSpec(
            GrowthNodeId.FORCE, "力度", "力", "基础调息", 5,
            listOf(15, 25, 40, 60, 85),
            "提高风纹的直线推进速度，让气泡更快抵达远端火焰。",
        ),
        GrowthSpec(
            GrowthNodeId.DENSITY, "密度", "密", "基础调息", 5,
            listOf(15, 25, 40, 60, 85),
            "增加单位时间内生成的气泡，并提升每枚气泡保留的能量。",
        ),
        GrowthSpec(
            GrowthNodeId.SPREAD, "扩散", "扩", "基础调息", 4,
            listOf(20, 35, 55, 80),
            "扩大气泡扇形覆盖，更容易同时触达相邻火焰。",
            listOf(GrowthRequirement(GrowthNodeId.DENSITY, 1)),
        ),
        GrowthSpec(
            GrowthNodeId.LIFETIME, "悬浮", "悬", "基础调息", 4,
            listOf(20, 35, 55, 80),
            "延长气泡在风道中的存续时间，降低远端气泡提前消散的概率。",
            listOf(GrowthRequirement(GrowthNodeId.DENSITY, 1)),
        ),
        GrowthSpec(
            GrowthNodeId.CHARGE, "蓄压", "蓄", "基础调息", 4,
            listOf(25, 45, 70, 100),
            "连续呼气达到稳定区间后，进一步放大直吹效果。",
            listOf(GrowthRequirement(GrowthNodeId.FORCE, 2)),
        ),
        GrowthSpec(
            GrowthNodeId.FIRST_SPLIT, "初生分裂", "裂", "风纹技艺", 1,
            listOf(120),
            "父泡抵达风道末端后分裂为两枚子泡，扩大远端覆盖。",
            listOf(
                GrowthRequirement(GrowthNodeId.DENSITY, 3),
                GrowthRequirement(GrowthNodeId.LIFETIME, 2),
            ),
        ),
        GrowthSpec(
            GrowthNodeId.CHILD_DRIFT, "偏流子泡", "偏", "风纹技艺", 1,
            listOf(90),
            "子泡分裂后向两侧偏流，追踪主风纹之外的相邻目标。",
            listOf(
                GrowthRequirement(GrowthNodeId.FIRST_SPLIT, 1),
                GrowthRequirement(GrowthNodeId.SPREAD, 2),
            ),
        ),
        GrowthSpec(
            GrowthNodeId.SECOND_BLOOM, "二次绽放", "绽", "风纹技艺", 1,
            listOf(180),
            "子泡再次绽放为微泡群，在远端形成第三代覆盖。",
            listOf(
                GrowthRequirement(GrowthNodeId.FIRST_SPLIT, 1),
                GrowthRequirement(GrowthNodeId.LIFETIME, 4),
            ),
        ),
        GrowthSpec(
            GrowthNodeId.EXTINGUISH_WAVE, "熄灭余波", "波", "风纹技艺", 1,
            listOf(160),
            "火焰熄灭时释放短促余波，压低相邻火焰的剩余热量。",
            listOf(
                GrowthRequirement(GrowthNodeId.FORCE, 4),
                GrowthRequirement(GrowthNodeId.CHARGE, 3),
            ),
        ),
    )

    private val byId = specs.associateBy { it.id }

    fun spec(id: GrowthNodeId): GrowthSpec = requireNotNull(byId[id])

    fun effect(id: GrowthNodeId, rank: Int): String = when (id) {
        GrowthNodeId.FORCE ->
            if (rank == 0) "气流推进维持基础速度" else "气流推进 +${rank * 4}%"
        GrowthNodeId.DENSITY ->
            if (rank == 0) "气泡频率与能量维持基础值"
            else "气泡频率 +${rank * 7}% · 能量 +${rank * 4}%"
        GrowthNodeId.SPREAD -> "气泡扩散角 ${3 + rank * 2}°"
        GrowthNodeId.LIFETIME ->
            if (rank == 0) "气泡维持基础存续时间" else "气泡存续 +${rank * 12}%"
        GrowthNodeId.CHARGE ->
            if (rank == 0) "稳定呼气不提供额外增幅" else "稳定呼气增幅 +${rank * 3}%"
        GrowthNodeId.FIRST_SPLIT ->
            if (rank == 0) "父泡保持单体飞行" else "父泡可分裂为 2 枚子泡"
        GrowthNodeId.CHILD_DRIFT ->
            if (rank == 0) "子泡沿主风向继续前进" else "子泡获得左右偏流"
        GrowthNodeId.SECOND_BLOOM ->
            if (rank == 0) "气泡最多延续到第二代" else "子泡可绽放为 3 枚微泡"
        GrowthNodeId.EXTINGUISH_WAVE ->
            if (rank == 0) "熄灭只影响当前目标" else "熄灭触发相邻余波"
    }
}

enum class ChallengePhase { HOME, COUNTDOWN, RUNNING, RESULT }

data class ChallengeConfig(
    val difficulty: Difficulty,
    val growthRanks: Map<GrowthNodeId, Int> = GrowthNodeId.entries.associateWith { 0 },
)

data class GameSnapshot(
    val page: GamePage = GamePage.TRIAL,
    val coins: Int = 0,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val growthRanks: Map<GrowthNodeId, Int> = GrowthNodeId.entries.associateWith { 0 },
    val selectedGrowthNodeId: GrowthNodeId? = null,
    val phase: ChallengePhase = ChallengePhase.HOME,
    val countdown: Int = 3,
    val elapsedSeconds: Float = 0f,
    val remainingCandles: Int = Difficulty.NORMAL.candleCount,
    val reward: Int = 0,
    val feedback: String = "",
)
