package com.example.lungcapacity.pcda76db4.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.lungcapacity.pcda76db4.data.repository.FakeGameRepository
import com.example.lungcapacity.pcda76db4.domain.usecase.CalculateRewardUseCase
import com.example.lungcapacity.pcda76db4.domain.usecase.UpgradeGrowthUseCase

class GameViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(GameViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return GameViewModel(
            repository = FakeGameRepository.shared,
            upgradeGrowth = UpgradeGrowthUseCase(),
            calculateReward = CalculateRewardUseCase(),
        ) as T
    }
}
