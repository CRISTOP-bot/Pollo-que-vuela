package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.database.AppDatabase
import com.example.data.repository.HighScoreRepository
import com.example.ui.game.GameScreen
import com.example.ui.game.GameViewModel
import com.example.ui.game.GameViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Room Database and Repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = HighScoreRepository(database.highScoreDao())
        
        // Instantiate GameViewModel with Factory
        val viewModel: GameViewModel by viewModels {
            GameViewModelFactory(repository)
        }
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    GameScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
