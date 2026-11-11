package com.example.lungcapacity.pcda76db4.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lungcapacity.pcda76db4.data.repository.GameRepository
import com.example.lungcapacity.pcda76db4.domain.model.ChallengeConfig
import com.example.lungcapacity.pcda76db4.domain.model.ChallengePhase
import com.example.lungcapacity.pcda76db4.domain.model.GamePage
import com.example.lungcapacity.pcda76db4.domain.model.GameSnapshot
import com.example.lungcapacity.pcda76db4.domain.usecase.CalculateRewardUseCase
import com.example.lungcapacity.pcda76db4.domain.usecase.UpgradeGrowthUseCase
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GameViewModel(
    private val repository: GameRepository,
    private val upgradeGrowth: UpgradeGrowthUseCase,
    private val calculateReward: CalculateRewardUseCase,
) : ViewModel() {
    private val mutableStageRequests = MutableSharedFlow<ChallengeConfig>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val stageRequests = mutableStageRequests.asSharedFlow()

    val uiState = repository.state.map { it.toUiState() }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = repository.state.value.toUiState(),
    )

    fun onEvent(event: GameEvent) {
        when (event) {
            is GameEvent.SelectPage ->
                repository.update { it.copy(page = event.page, selectedGrowthNodeId = null) }
            is GameEvent.SelectDifficulty ->
                repository.update {
                    it.copy(
                        difficulty = event.difficulty,
                        remainingCandles = event.difficulty.candleCount,
                    )
                }
            is GameEvent.SelectGrowth -> {
                repository.update { it.copy(selectedGrowthNodeId = event.id, feedback = "") }
            }
            GameEvent.CloseGrowth -> repository.update { it.copy(selectedGrowthNodeId = null) }
            GameEvent.UpgradeGrowth -> {
                val result = upgradeGrowth(repository.state.value)
                repository.update { result.snapshot.copy(feedback = result.message) }
            }
            GameEvent.StartChallenge -> requestStage(repository.state.value)
            GameEvent.RetryChallenge -> {
                repository.update {
                    it.copy(
                        phase = ChallengePhase.COUNTDOWN,
                        countdown = 0,
                        elapsedSeconds = 0f,
                        remainingCandles = it.difficulty.candleCount,
                        reward = 0,
                    )
                }
                beginCountdown()
            }
            GameEvent.ReturnHome -> repository.update {
                it.copy(
                    phase = ChallengePhase.HOME,
                    page = GamePage.TRIAL,
                    selectedGrowthNodeId = null,
                )
            }
        }
    }

    fun onStageMounted(microphoneStarted: Boolean) {
        if (microphoneStarted) {
            beginCountdown()
        } else {
            repository.update {
                it.copy(phase = ChallengePhase.HOME, feedback = "无法启用麦克风，请检查录音权限")
            }
        }
    }

    fun onStageFrame(elapsed: Float) {
        if (repository.state.value.phase != ChallengePhase.RUNNING) return
        repository.update { it.copy(elapsedSeconds = elapsed) }
    }

    fun onCandleExtinguished() {
        repository.update { snapshot ->
            if (snapshot.phase != ChallengePhase.RUNNING || snapshot.remainingCandles <= 0) {
                return@update snapshot
            }
            val remaining = snapshot.remainingCandles - 1
            if (remaining > 0) {
                snapshot.copy(remainingCandles = remaining)
            } else {
                val reward = calculateReward(
                    snapshot.difficulty,
                    snapshot.elapsedSeconds,
                )
                snapshot.copy(
                    phase = ChallengePhase.RESULT,
                    remainingCandles = 0,
                    reward = reward,
                    coins = snapshot.coins + reward,
                )
            }
        }
    }

    private fun requestStage(snapshot: GameSnapshot) {
        repository.update {
            it.copy(
                phase = ChallengePhase.COUNTDOWN,
                countdown = 0,
                elapsedSeconds = 0f,
                remainingCandles = snapshot.difficulty.candleCount,
                reward = 0,
                feedback = "",
            )
        }
        mutableStageRequests.tryEmit(
            ChallengeConfig(
                difficulty = snapshot.difficulty,
                growthRanks = snapshot.growthRanks,
            ),
        )
    }

    private fun beginCountdown() {
        viewModelScope.launch {
            repository.update {
                it.copy(
                    phase = ChallengePhase.COUNTDOWN,
                    countdown = 0,
                    elapsedSeconds = 0f,
                    remainingCandles = it.difficulty.candleCount,
                    feedback = "",
                )
            }
            delay(ASSEMBLY_LEAD_MILLIS)
            for (value in 3 downTo 1) {
                repository.update { it.copy(countdown = value) }
                delay(1_000)
            }
            repository.update { it.copy(phase = ChallengePhase.RUNNING, countdown = 0) }
        }
    }
}

private fun GameSnapshot.toUiState(): GameUiState = GameUiState(
    page = page,
    coins = coins,
    difficulty = difficulty,
    growthRanks = growthRanks,
    selectedGrowth = growthDetail(
        selectedGrowthNodeId,
        coins,
        growthRanks,
    ),
    feedback = feedback,
    phase = phase,
    countdown = countdown,
    elapsedSeconds = elapsedSeconds,
    remainingCandles = remainingCandles,
    reward = reward,
)

private const val ASSEMBLY_LEAD_MILLIS = 3_000L
