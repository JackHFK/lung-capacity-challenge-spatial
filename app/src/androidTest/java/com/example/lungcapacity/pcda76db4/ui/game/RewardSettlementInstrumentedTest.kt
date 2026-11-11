package com.example.lungcapacity.pcda76db4.ui.game

import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lungcapacity.pcda76db4.data.repository.FakeGameRepository
import com.example.lungcapacity.pcda76db4.domain.model.ChallengePhase
import com.example.lungcapacity.pcda76db4.domain.model.Difficulty
import com.example.lungcapacity.pcda76db4.domain.model.GamePage
import com.example.lungcapacity.pcda76db4.domain.model.GameSnapshot
import com.example.lungcapacity.pcda76db4.domain.model.GrowthNodeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RewardSettlementInstrumentedTest {
    @Test
    fun sharedHomeAndStageWalletRetainsRewardsAcrossRetryReturnAndPurchase() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val repository = FakeGameRepository.shared
        val original = repository.state.value
        val homeStore = ViewModelStore()
        val stageStore = ViewModelStore()
        lateinit var home: GameViewModel
        lateinit var stage: GameViewModel
        try {
            withContext(Dispatchers.Main) {
                repository.update { GameSnapshot() }
                home = ViewModelProvider(homeStore, GameViewModelFactory())[GameViewModel::class.java]
                stage = ViewModelProvider(stageStore, GameViewModelFactory())[GameViewModel::class.java]
                assertEquals(0, home.uiState.value.coins)
                home.onEvent(GameEvent.SelectGrowth(GrowthNodeId.FORCE))
                home.onEvent(GameEvent.UpgradeGrowth)
                assertEquals(0, repository.state.value.coins)
                assertEquals(0, repository.state.value.growthRanks[GrowthNodeId.FORCE])
                assertFalse(growthDetail(GrowthNodeId.FORCE, 0, repository.state.value.growthRanks)!!.upgradeEnabled)
                home.onEvent(GameEvent.SelectDifficulty(Difficulty.EASY))
                home.onEvent(GameEvent.StartChallenge)
                stage.onStageMounted(true)
            }
            withTimeout(STAGE_FLOW_TIMEOUT_MILLIS) {
                stage.uiState.first { it.phase == ChallengePhase.RUNNING }
            }
            withContext(Dispatchers.Main) {
                stage.onStageFrame(20f)
                repeat(7) { stage.onCandleExtinguished() }
                assertEquals(0, repository.state.value.coins)
                assertEquals(1, repository.state.value.remainingCandles)
                stage.onCandleExtinguished()
                repeat(3) { stage.onCandleExtinguished() }
            }
            val firstResult = withTimeout(5_000) { home.uiState.first { it.phase == ChallengePhase.RESULT } }
            assertEquals(Difficulty.EASY, firstResult.difficulty)
            assertEquals(14, firstResult.reward)
            assertEquals(14, firstResult.coins)

            withContext(Dispatchers.Main) {
                stage.onEvent(GameEvent.RetryChallenge)
                assertEquals(14, repository.state.value.coins)
                assertEquals(0, repository.state.value.reward)
            }
            withTimeout(STAGE_FLOW_TIMEOUT_MILLIS) {
                stage.uiState.first { it.phase == ChallengePhase.RUNNING }
            }
            withContext(Dispatchers.Main) {
                stage.onStageFrame(20f)
                repeat(8) { stage.onCandleExtinguished() }
                repeat(3) { stage.onCandleExtinguished() }
                stage.onEvent(GameEvent.ReturnHome)
            }
            val returned = withTimeout(5_000) { home.uiState.first { it.phase == ChallengePhase.HOME } }
            assertEquals(28, returned.coins)
            withContext(Dispatchers.Main) {
                home.onEvent(GameEvent.SelectPage(GamePage.GROWTH))
                home.onEvent(GameEvent.SelectGrowth(GrowthNodeId.FORCE))
                assertEquals(28, repository.state.value.coins)
                home.onEvent(GameEvent.UpgradeGrowth)
            }
            val purchased = withTimeout(5_000) { stage.uiState.first { it.coins == 13 } }
            assertEquals(1, purchased.growthRanks[GrowthNodeId.FORCE])
            Log.i(
                "WalletSettlement",
                "easy reward=${firstResult.reward} firstBalance=${firstResult.coins} " +
                    "afterRetry=${returned.coins} afterPurchase=${purchased.coins}",
            )
        } finally {
            withContext(Dispatchers.Main) {
                homeStore.clear()
                stageStore.clear()
                repository.update { original }
            }
        }
    }
}

private const val STAGE_FLOW_TIMEOUT_MILLIS = 12_000L
