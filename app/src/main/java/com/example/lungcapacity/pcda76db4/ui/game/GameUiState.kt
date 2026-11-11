package com.example.lungcapacity.pcda76db4.ui.game

import com.example.lungcapacity.pcda76db4.domain.model.ChallengePhase
import com.example.lungcapacity.pcda76db4.domain.model.Difficulty
import com.example.lungcapacity.pcda76db4.domain.model.GamePage
import com.example.lungcapacity.pcda76db4.domain.model.GrowthCatalog
import com.example.lungcapacity.pcda76db4.domain.model.GrowthNodeId
import com.example.lungcapacity.pcda76db4.domain.model.GrowthSpec

data class GrowthDetailUi(
    val spec: GrowthSpec,
    val rank: Int,
    val currentEffect: String,
    val nextEffect: String,
    val prerequisiteText: String,
    val cost: Int?,
    val upgradeEnabled: Boolean,
    val upgradeLabel: String,
)

data class GameUiState(
    val page: GamePage = GamePage.TRIAL,
    val coins: Int = 0,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val growthRanks: Map<GrowthNodeId, Int> = GrowthNodeId.entries.associateWith { 0 },
    val selectedGrowth: GrowthDetailUi? = null,
    val feedback: String = "",
    val phase: ChallengePhase = ChallengePhase.HOME,
    val countdown: Int = 3,
    val elapsedSeconds: Float = 0f,
    val remainingCandles: Int = Difficulty.NORMAL.candleCount,
    val reward: Int = 0,
)

sealed interface GameEvent {
    data class SelectPage(val page: GamePage) : GameEvent
    data class SelectDifficulty(val difficulty: Difficulty) : GameEvent
    data class SelectGrowth(val id: GrowthNodeId) : GameEvent
    data object CloseGrowth : GameEvent
    data object UpgradeGrowth : GameEvent
    data object StartChallenge : GameEvent
    data object RetryChallenge : GameEvent
    data object ReturnHome : GameEvent
}

internal fun growthDetail(
    id: GrowthNodeId?,
    coins: Int,
    ranks: Map<GrowthNodeId, Int>,
): GrowthDetailUi? {
    id ?: return null
    val spec = GrowthCatalog.spec(id)
    val rank = ranks[id] ?: 0
    val unmet = spec.prerequisites.filter { (ranks[it.nodeId] ?: 0) < it.rank }
    val prerequisite = if (spec.prerequisites.isEmpty()) {
        "无前置条件"
    } else {
        spec.prerequisites.joinToString(" · ") {
            "${GrowthCatalog.spec(it.nodeId).label} ${it.rank} 级"
        }
    }
    val cost = spec.costs.getOrNull(rank)
    val enabled = rank < spec.maxRank && unmet.isEmpty() && cost != null && coins >= cost
    val label = when {
        rank >= spec.maxRank -> "已满级"
        unmet.isNotEmpty() -> "前置未满足"
        cost != null && coins < cost -> "金币不足"
        else -> "升级"
    }
    return GrowthDetailUi(
        spec = spec,
        rank = rank,
        currentEffect = GrowthCatalog.effect(id, rank),
        nextEffect = GrowthCatalog.effect(id, (rank + 1).coerceAtMost(spec.maxRank)),
        prerequisiteText = prerequisite,
        cost = cost,
        upgradeEnabled = enabled,
        upgradeLabel = label,
    )
}
