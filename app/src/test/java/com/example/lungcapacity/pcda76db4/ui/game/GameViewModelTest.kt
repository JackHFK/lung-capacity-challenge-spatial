package com.example.lungcapacity.pcda76db4.ui.game

import com.example.lungcapacity.pcda76db4.data.repository.FakeGameRepository
import com.example.lungcapacity.pcda76db4.domain.model.ChallengePhase
import com.example.lungcapacity.pcda76db4.domain.model.Difficulty
import com.example.lungcapacity.pcda76db4.domain.model.GamePage
import com.example.lungcapacity.pcda76db4.domain.model.GameSnapshot
import com.example.lungcapacity.pcda76db4.domain.model.GrowthNodeId
import com.example.lungcapacity.pcda76db4.domain.usecase.CalculateRewardUseCase
import com.example.lungcapacity.pcda76db4.domain.usecase.UpgradeGrowthUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateLoadsTrialPage() = runTest(dispatcher) {
        val model = createModel()
        advanceUntilIdle()

        assertEquals(GamePage.TRIAL, model.uiState.value.page)
        assertEquals(0, GameUiState().coins)
        assertEquals(0, model.uiState.value.coins)
        assertEquals(Difficulty.NORMAL, model.uiState.value.difficulty)
    }

    @Test
    fun difficultySelectionUpdatesCandleCount() = runTest(dispatcher) {
        val model = createModel()

        model.onEvent(GameEvent.SelectDifficulty(Difficulty.HARD))
        advanceUntilIdle()

        assertEquals(Difficulty.HARD, model.uiState.value.difficulty)
        assertEquals(24, model.uiState.value.remainingCandles)
    }

    @Test
    fun selectingGrowthOnlyOpensDetailWithoutSpending() = runTest(dispatcher) {
        val model = createModel()

        model.onEvent(GameEvent.SelectPage(GamePage.GROWTH))
        model.onEvent(GameEvent.SelectGrowth(GrowthNodeId.FORCE))
        advanceUntilIdle()

        assertEquals(0, model.uiState.value.coins)
        assertNotNull(model.uiState.value.selectedGrowth)
        assertEquals(0, model.uiState.value.selectedGrowth?.rank)
        assertFalse(model.uiState.value.selectedGrowth!!.upgradeEnabled)
        model.onEvent(GameEvent.UpgradeGrowth)
        advanceUntilIdle()
        assertEquals(0, model.uiState.value.coins)
        assertEquals(0, model.uiState.value.growthRanks[GrowthNodeId.FORCE])
    }

    @Test
    fun explicitRepeatedUpgradeConsumesEachRankCost() = runTest(dispatcher) {
        val model = createModel(FakeGameRepository(GameSnapshot(coins = 40)))

        model.onEvent(GameEvent.SelectGrowth(GrowthNodeId.FORCE))
        model.onEvent(GameEvent.UpgradeGrowth)
        model.onEvent(GameEvent.UpgradeGrowth)
        advanceUntilIdle()

        assertEquals(0, model.uiState.value.coins)
        assertEquals(2, model.uiState.value.growthRanks[GrowthNodeId.FORCE])
        assertEquals("气流推进 +8%", model.uiState.value.selectedGrowth?.currentEffect)
    }

    @Test
    fun startingChallengeWaitsForTheStageAssemblyLead() = runTest(dispatcher) {
        val model = createModel()

        model.onEvent(GameEvent.StartChallenge)
        advanceUntilIdle()

        assertEquals(ChallengePhase.COUNTDOWN, model.uiState.value.phase)
        assertEquals(0, model.uiState.value.countdown)
        assertEquals(16, model.uiState.value.remainingCandles)
    }

    @Test
    fun countdownAppearsThreeSecondsAfterAssemblyStarts() = runTest(dispatcher) {
        val model = createModel()
        model.onEvent(GameEvent.StartChallenge)
        model.onStageMounted(true)
        runCurrent()

        assertEquals(ChallengePhase.COUNTDOWN, model.uiState.value.phase)
        assertEquals(0, model.uiState.value.countdown)

        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(0, model.uiState.value.countdown)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(3, model.uiState.value.countdown)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, model.uiState.value.countdown)
    }

    @Test
    fun candleHitsCompleteTheRunWithoutAnyDebugPhase() = runTest(dispatcher) {
        val model = createModel()
        model.onEvent(GameEvent.StartChallenge)
        model.onStageMounted(true)
        advanceUntilIdle()
        model.onStageFrame(40f)

        repeat(16) { model.onCandleExtinguished() }
        advanceUntilIdle()

        assertEquals(ChallengePhase.RESULT, model.uiState.value.phase)
        assertEquals(0, model.uiState.value.remainingCandles)
        assertEquals(27, model.uiState.value.reward)
    }

    @Test
    fun easyCompletionCreditsHomeAndStageOnlyOnce() = runTest(dispatcher) {
        val repository = FakeGameRepository()
        val home = createModel(repository)
        val stage = createModel(repository)
        home.onEvent(GameEvent.SelectDifficulty(Difficulty.EASY))
        home.onEvent(GameEvent.StartChallenge)
        stage.onStageMounted(true)
        advanceUntilIdle()
        stage.onStageFrame(20f)

        stage.onCandleExtinguished()
        advanceUntilIdle()
        assertEquals(7, home.uiState.value.remainingCandles)
        assertEquals(0, home.uiState.value.coins)
        repeat(7) { stage.onCandleExtinguished() }
        repeat(3) { stage.onCandleExtinguished() }
        advanceUntilIdle()

        assertEquals(14, stage.uiState.value.reward)
        assertEquals(14, stage.uiState.value.coins)
        assertEquals(14, home.uiState.value.coins)
        stage.onEvent(GameEvent.ReturnHome)
        stage.onCandleExtinguished()
        advanceUntilIdle()
        assertEquals(14, home.uiState.value.coins)
    }

    @Test
    fun retryAccumulatesBothRunRewardsWithoutRecrediting() = runTest(dispatcher) {
        val model = createModel()
        model.onEvent(GameEvent.StartChallenge)
        model.onStageMounted(true)
        advanceUntilIdle()
        model.onStageFrame(40f)
        repeat(16) { model.onCandleExtinguished() }
        advanceUntilIdle()
        assertEquals(27, model.uiState.value.coins)

        model.onEvent(GameEvent.RetryChallenge)
        runCurrent()
        assertEquals(0, model.uiState.value.reward)
        assertEquals(27, model.uiState.value.coins)
        advanceUntilIdle()
        model.onStageFrame(40f)
        repeat(16) { model.onCandleExtinguished() }
        repeat(3) { model.onCandleExtinguished() }
        model.onEvent(GameEvent.ReturnHome)
        advanceUntilIdle()
        assertEquals(54, model.uiState.value.coins)
    }

    @Test
    fun earnedCoinsCanFundAnUpgradeWithoutResettingTheWallet() = runTest(dispatcher) {
        val model = createModel()
        model.onEvent(GameEvent.SelectDifficulty(Difficulty.EASY))
        model.onEvent(GameEvent.StartChallenge)
        model.onStageMounted(true)
        advanceUntilIdle()
        model.onStageFrame(0.1f)
        repeat(8) { model.onCandleExtinguished() }
        model.onEvent(GameEvent.ReturnHome)
        model.onEvent(GameEvent.SelectPage(GamePage.GROWTH))
        model.onEvent(GameEvent.SelectGrowth(GrowthNodeId.FORCE))
        advanceUntilIdle()
        assertEquals(26, model.uiState.value.coins)
        assertEquals(0, model.uiState.value.growthRanks[GrowthNodeId.FORCE])

        model.onEvent(GameEvent.UpgradeGrowth)
        advanceUntilIdle()
        assertEquals(11, model.uiState.value.coins)
        assertEquals(1, model.uiState.value.growthRanks[GrowthNodeId.FORCE])
        model.onEvent(GameEvent.StartChallenge)
        advanceUntilIdle()
        assertEquals(11, model.uiState.value.coins)
    }

    @Test
    fun difficultySelectionDeterminesRewardAndCredit() = runTest(dispatcher) {
        val expected = listOf(
            Triple(Difficulty.EASY, 20f, 14),
            Triple(Difficulty.NORMAL, 40f, 27),
            Triple(Difficulty.HARD, 60f, 45),
        )
        for ((difficulty, seconds, reward) in expected) {
            val model = createModel()
            model.onEvent(GameEvent.SelectDifficulty(difficulty))
            model.onEvent(GameEvent.StartChallenge)
            model.onStageMounted(true)
            advanceUntilIdle()
            assertEquals(difficulty.candleCount, model.uiState.value.remainingCandles)
            model.onStageFrame(seconds)
            repeat(difficulty.candleCount) { model.onCandleExtinguished() }
            advanceUntilIdle()
            assertEquals(reward, model.uiState.value.reward)
            assertEquals(reward, model.uiState.value.coins)
        }
    }

    @Test
    fun incompleteChallengeCallbacksNeverCreditCoins() = runTest(dispatcher) {
        val model = createModel()
        model.onCandleExtinguished()
        model.onEvent(GameEvent.SelectDifficulty(Difficulty.EASY))
        model.onEvent(GameEvent.StartChallenge)
        repeat(8) { model.onCandleExtinguished() }
        advanceUntilIdle()
        assertEquals(ChallengePhase.COUNTDOWN, model.uiState.value.phase)
        assertEquals(8, model.uiState.value.remainingCandles)
        assertEquals(0, model.uiState.value.coins)
        assertEquals(0, model.uiState.value.reward)
    }

    @Test
    fun failedMicrophoneStartupReturnsHomeWithoutCountdownOrRewards() = runTest(dispatcher) {
        val repository = FakeGameRepository(GameSnapshot(coins = 21))
        val model = createModel(repository)
        model.onEvent(GameEvent.StartChallenge)
        model.onStageMounted(false)
        advanceUntilIdle()
        model.onCandleExtinguished()
        assertEquals(ChallengePhase.HOME, model.uiState.value.phase)
        assertEquals(21, model.uiState.value.coins)
        assertEquals(0, model.uiState.value.reward)
        assertEquals(Difficulty.NORMAL.candleCount, model.uiState.value.remainingCandles)
        assertFalse(model.uiState.value.feedback.isEmpty())
    }

    @Test
    fun stageStartupFailureIsVisibleInTheExistingHomeModel() = runTest(dispatcher) {
        val repository = FakeGameRepository()
        val home = createModel(repository)
        val stage = createModel(repository)
        home.onEvent(GameEvent.StartChallenge)
        stage.onStageMounted(false)
        advanceUntilIdle()
        assertEquals("无法启用麦克风，请检查录音权限", home.uiState.value.feedback)
        assertEquals(stage.uiState.value.feedback, home.uiState.value.feedback)
    }

    @Test
    fun reselectingTheSameGrowthNodeClearsItsPreviousFeedback() = runTest(dispatcher) {
        val model = createModel(FakeGameRepository(GameSnapshot(coins = 40)))
        model.onEvent(GameEvent.SelectGrowth(GrowthNodeId.FORCE))
        model.onEvent(GameEvent.UpgradeGrowth)
        advanceUntilIdle()
        assertFalse(model.uiState.value.feedback.isEmpty())
        model.onEvent(GameEvent.SelectGrowth(GrowthNodeId.FORCE))
        advanceUntilIdle()
        assertEquals("", model.uiState.value.feedback)
        assertEquals(25, model.uiState.value.coins)
        assertEquals(1, model.uiState.value.selectedGrowth?.rank)
    }

    private fun createModel(repository: FakeGameRepository = FakeGameRepository()): GameViewModel = GameViewModel(
        repository = repository,
        upgradeGrowth = UpgradeGrowthUseCase(),
        calculateReward = CalculateRewardUseCase(),
    )
}
