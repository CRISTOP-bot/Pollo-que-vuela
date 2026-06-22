package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HighScoreDao {
    @Query("SELECT * FROM high_scores ORDER BY score DESC, timestamp ASC LIMIT 10")
    fun getTopScores(): Flow<List<HighScoreEntity>>

    @Query("SELECT * FROM high_scores ORDER BY score DESC LIMIT 1")
    fun getHighScore(): Flow<HighScoreEntity?>

    @Insert
    suspend fun insertScore(score: HighScoreEntity)

    @Query("DELETE FROM high_scores")
    suspend fun clearScores()
}
