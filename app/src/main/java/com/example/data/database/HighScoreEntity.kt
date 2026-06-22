package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "high_scores")
data class HighScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val score: Int,
    val playerName: String = "BIRD",
    val timestamp: Long = System.currentTimeMillis()
)
