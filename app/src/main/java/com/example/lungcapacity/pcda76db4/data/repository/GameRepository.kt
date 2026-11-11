package com.example.lungcapacity.pcda76db4.data.repository

import com.example.lungcapacity.pcda76db4.domain.model.GameSnapshot
import kotlinx.coroutines.flow.StateFlow

interface GameRepository {
    val state: StateFlow<GameSnapshot>
    fun update(transform: (GameSnapshot) -> GameSnapshot)
}
