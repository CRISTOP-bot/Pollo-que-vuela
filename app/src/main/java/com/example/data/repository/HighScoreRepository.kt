package com.example.data.repository

import com.example.data.database.HighScoreDao
import com.example.data.database.HighScoreEntity
import kotlinx.coroutines.flow.Flow

class HighScoreRepository(private val highScoreDao: HighScoreDao) {
    val topScores: Flow<List<HighScoreEntity>> = highScoreDao.getTopScores()
    val highScore: Flow<HighScoreEntity?> = highScoreDao.getHighScore()

    suspend fun insertScore(score: Int, playerName: String = "BIRD") {
        highScoreDao.insertScore(HighScoreEntity(score = score, playerName = playerName))
    }

    suspend fun clearScores() {
        highScoreDao.clearScores()
    }
}
