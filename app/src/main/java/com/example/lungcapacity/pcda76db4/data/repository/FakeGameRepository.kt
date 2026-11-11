package com.example.lungcapacity.pcda76db4.data.repository

import com.example.lungcapacity.pcda76db4.domain.model.GameSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeGameRepository(
    initialState: GameSnapshot = GameSnapshot(),
) : GameRepository {
    private val mutableState = MutableStateFlow(initialState)
    override val state: StateFlow<GameSnapshot> = mutableState.asStateFlow()

    override fun update(transform: (GameSnapshot) -> GameSnapshot) {
        mutableState.update(transform)
    }

    companion object {
        val shared: FakeGameRepository by lazy { FakeGameRepository() }
    }
}
