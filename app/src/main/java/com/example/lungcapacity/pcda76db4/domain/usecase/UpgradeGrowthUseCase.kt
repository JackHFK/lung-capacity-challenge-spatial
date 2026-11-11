package com.example.lungcapacity.pcda76db4.domain.usecase

import com.example.lungcapacity.pcda76db4.domain.model.GameSnapshot
import com.example.lungcapacity.pcda76db4.domain.model.GrowthCatalog

data class UpgradeResult(
    val snapshot: GameSnapshot,
    val message: String,
    val upgraded: Boolean,
)

class UpgradeGrowthUseCase {
    operator fun invoke(snapshot: GameSnapshot): UpgradeResult {
        val id = snapshot.selectedGrowthNodeId
            ?: return UpgradeResult(snapshot, "请先选择一枚能力符印", false)
        val spec = GrowthCatalog.spec(id)
        val rank = snapshot.growthRanks[id] ?: 0
        if (rank >= spec.maxRank) {
            return UpgradeResult(snapshot, "该能力已经达到最高等级", false)
        }
        val unmet = spec.prerequisites.filter { requirement ->
            (snapshot.growthRanks[requirement.nodeId] ?: 0) < requirement.rank
        }
        if (unmet.isNotEmpty()) {
            val text = unmet.joinToString(" · ") {
                "${GrowthCatalog.spec(it.nodeId).label} ${it.rank} 级"
            }
            return UpgradeResult(snapshot, "仍需：$text", false)
        }
        val cost = spec.costs[rank]
        if (snapshot.coins < cost) {
            return UpgradeResult(snapshot, "还差 ${cost - snapshot.coins} 金币", false)
        }
        return UpgradeResult(
            snapshot = snapshot.copy(
                coins = snapshot.coins - cost,
                growthRanks = snapshot.growthRanks + (id to rank + 1),
            ),
            message = "升级完成 · ${GrowthCatalog.effect(id, rank + 1)}",
            upgraded = true,
        )
    }
}
