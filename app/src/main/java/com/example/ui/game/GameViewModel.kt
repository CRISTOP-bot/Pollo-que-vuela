package com.example.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.HighScoreRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class GameState {
    MENU,
    PLAYING,
    GAME_OVER,
    HIGHSCORES
}

enum class BirdSkin(val displayName: String, val primaryColorHex: Long, val eyeColorHex: Long, val beakColorHex: Long) {
    YELLOW("Clásico Amarillo", 0xFFFFEB3B, 0xFF000000, 0xFFFF9800),
    RED("Carmesí Feroz", 0xFFE53935, 0xFFFFFFFF, 0xFFFFB300),
    BLUE("Neo Celeste", 0xFF00E5FF, 0xFFFFFFFF, 0xFFFF7043),
    GOLD("Águila Dorada", 0xFFFFD700, 0xFF000000, 0xFFFF3D00)
}

enum class BackgroundStyle(val displayName: String, val skyColorHex: Long, val groundColorHex: Long, val pipeColorHex: Long) {
    DAY("Día Soleado", 0xFF4FC3F7, 0xFF8D6E63, 0xFF4CAF50),
    NIGHT("Noche Estrellada", 0xFF0D47A1, 0xFF4E342E, 0xFF2E7D32),
    SPACE("Dimensión Vacía", 0xFF120024, 0xFF212121, 0xFF7B1FA2)
}

data class Pipe(
    val id: Int,
    val x: Float,
    val gapY: Float,
    val passed: Boolean = false
)

data class GameParticle(
    val id: Long,
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val color: Long,
    val alpha: Float,
    val size: Float,
    val maxLife: Float,
    var currentLife: Float
)

class GameViewModel(private val repository: HighScoreRepository) : ViewModel() {

    // Constants for Virtual Resolution: 360f x 640f
    val virtualWidth = 360f
    val virtualHeight = 640f
    val groundY = 540f
    val birdX = 100f
    val birdRadius = 14f

    private val gravity = 850f
    private val jumpImpulse = -280f
    private val pipeSpeed = 150f
    private val pipeGapSize = 145f
    private val pipeWidth = 60f
    private val distanceBetweenPipes = 230f

    // Observables
    private val _gameState = MutableStateFlow(GameState.MENU)
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _birdY = MutableStateFlow(250f)
    val birdY: StateFlow<Float> = _birdY.asStateFlow()

    private val _birdVelocity = MutableStateFlow(0f)
    val birdVelocity: StateFlow<Float> = _birdVelocity.asStateFlow()

    private val _pipes = MutableStateFlow<List<Pipe>>(emptyList())
    val pipes: StateFlow<List<Pipe>> = _pipes.asStateFlow()

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _isNewHighScore = MutableStateFlow(false)
    val isNewHighScore: StateFlow<Boolean> = _isNewHighScore.asStateFlow()

    private val _particles = MutableStateFlow<List<GameParticle>>(emptyList())
    val particles: StateFlow<List<GameParticle>> = _particles.asStateFlow()

    private val _selectedSkin = MutableStateFlow(BirdSkin.YELLOW)
    val selectedSkin: StateFlow<BirdSkin> = _selectedSkin.asStateFlow()

    private val _activeBackground = MutableStateFlow(BackgroundStyle.DAY)
    val activeBackground: StateFlow<BackgroundStyle> = _activeBackground.asStateFlow()

    // Room Score updates mapped
    val currentHighScore: StateFlow<Int> = repository.highScore
        .map { it?.score ?: 0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val topScores = repository.topScores.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var gameJob: Job? = null
    private var particleJob: Job? = null
    private var lastFrameTime = 0L
    private var distanceSinceLastPipe = 230f // immediate spawn
    private var pipeIdCounter = 0
    private var particleIdCounter = 0L

    init {
        // Run particle system independently to keep trails/explosions rendering smoothly
        startParticleSystem()
    }

    fun selectSkin(skin: BirdSkin) {
        _selectedSkin.value = skin
    }

    fun selectBackground(bg: BackgroundStyle) {
        _activeBackground.value = bg
    }

    fun navigateToMenu() {
        _gameState.value = GameState.MENU
    }

    fun navigateToHighScores() {
        _gameState.value = GameState.HIGHSCORES
    }

    fun startGame() {
        _gameState.value = GameState.PLAYING
        _birdY.value = 250f
        _birdVelocity.value = 0f
        _pipes.value = emptyList()
        _score.value = 0
        _isNewHighScore.value = false
        _particles.value = emptyList()
        distanceSinceLastPipe = distanceBetweenPipes // Spawn pipe early
        pipeIdCounter = 0
        
        lastFrameTime = System.currentTimeMillis()
        
        gameJob?.cancel()
        gameJob = viewModelScope.launch {
            while (isActive && _gameState.value == GameState.PLAYING) {
                updatePhysics()
                delay(12) // higher precision for smoother movement (approx 80hz physics updates)
            }
        }
    }

    fun birdJump() {
        if (_gameState.value == GameState.PLAYING) {
            _birdVelocity.value = jumpImpulse
            // Emit trail particles
            emitJumpTrail()
        }
    }

    private fun updatePhysics() {
        val now = System.currentTimeMillis()
        val dt = (now - lastFrameTime).coerceIn(0L, 100L) / 1000f
        lastFrameTime = now

        // Gravity affects bird velocity
        val currVel = _birdVelocity.value + gravity * dt
        _birdVelocity.value = currVel

        // Velocity affects bird position
        val nextY = (_birdY.value + currVel * dt).coerceAtLeast(0f)
        _birdY.value = nextY

        // Ground Collision
        if (nextY + birdRadius >= groundY) {
            triggerDisasterExplosion(_birdY.value)
            endGame()
            return
        }

        // Move Pipes
        val currentPipes = _pipes.value.toMutableList()
        val currSpeed = pipeSpeed + (_score.value * 2.5f).coerceAtMost(60f) // speed ramps slightly over time

        for (i in currentPipes.indices) {
            val p = currentPipes[i]
            currentPipes[i] = p.copy(x = p.x - currSpeed * dt)
        }

        // Check passing pipe
        for (i in currentPipes.indices) {
            val p = currentPipes[i]
            if (!p.passed && p.x + pipeWidth / 2f < birdX) {
                currentPipes[i] = p.copy(passed = true)
                val newScore = _score.value + 1
                _score.value = newScore
                triggerScoreBurst()
                
                // If we exceed our current record
                if (newScore > currentHighScore.value) {
                    _isNewHighScore.value = true
                }
            }
        }

        // Filter out off-screen pipes
        val visiblePipes = currentPipes.filter { it.x > -pipeWidth }

        // Spawning check
        distanceSinceLastPipe += currSpeed * dt
        val finalPipes = visiblePipes.toMutableList()
        if (distanceSinceLastPipe >= distanceBetweenPipes) {
            distanceSinceLastPipe = 0f
            
            // Randomize Gap centerY (gapY). Min height top pipe 80f, ground is 540f.
            // With a gap size of 145f, gap can center between:
            // Top bounds: 80 + gap/2 = 152.5f
            // Ground bounds: 540 - 80 - gap/2 = 387.5f
            val minCenter = 150f
            val maxCenter = 390f
            val gapY = Random.nextFloat() * (maxCenter - minCenter) + minCenter
            
            finalPipes.add(
                Pipe(
                    id = pipeIdCounter++,
                    x = virtualWidth,
                    gapY = gapY,
                    passed = false
                )
            )
        }

        _pipes.value = finalPipes

        // Check collision with obstacles
        if (checkCollision(nextY, finalPipes)) {
            triggerDisasterExplosion(nextY)
            endGame()
            return
        }
    }

    private fun checkCollision(birdYVal: Float, activePipes: List<Pipe>): Boolean {
        // Use standard circle collision or slightly tighter box bounds for friendly gameplay
        val hitboxOffset = 2f
        val bLeft = birdX - birdRadius + hitboxOffset
        val bRight = birdX + birdRadius - hitboxOffset
        val bTop = birdYVal - birdRadius + hitboxOffset
        val bBottom = birdYVal + birdRadius - hitboxOffset

        for (p in activePipes) {
            val pLeft = p.x - pipeWidth / 2f
            val pRight = p.x + pipeWidth / 2f

            val topPipeBottom = p.gapY - pipeGapSize / 2f
            val bottomPipeTop = p.gapY + pipeGapSize / 2f

            // Check horizontal overlap
            if (bRight > pLeft && bLeft < pRight) {
                // Collided with top tube
                if (bTop < topPipeBottom) {
                    return true
                }
                // Collided with bottom tube
                if (bBottom > bottomPipeTop) {
                    return true
                }
            }
        }
        return false
    }

    private fun endGame() {
        _gameState.value = GameState.GAME_OVER
        gameJob?.cancel()

        val finalScore = _score.value
        viewModelScope.launch {
            repository.insertScore(finalScore, "BIRD")
        }
    }

    fun clearHighScoreTable() {
        viewModelScope.launch {
            repository.clearScores()
        }
    }

    // --- Particle FX Engine ---

    private fun startParticleSystem() {
        particleJob?.cancel()
        particleJob = viewModelScope.launch {
            var lastUpdate = System.currentTimeMillis()
            while (isActive) {
                val now = System.currentTimeMillis()
                val dt = (now - lastUpdate).coerceAtLeast(1L) / 1000f
                lastUpdate = now

                val currentList = _particles.value
                if (currentList.isNotEmpty()) {
                    val updated = currentList.mapNotNull { p ->
                        val nextLife = p.currentLife + dt
                        if (nextLife >= p.maxLife) {
                            null
                        } else {
                            val nextX = p.x + p.vx * dt
                            val nextY = p.y + p.vy * dt
                            p.copy(
                                x = nextX,
                                y = nextY,
                                alpha = 1.0f - (nextLife / p.maxLife),
                                currentLife = nextLife
                            )
                        }
                    }
                    _particles.value = updated
                }
                delay(16)
            }
        }
    }

    private fun emitJumpTrail() {
        val count = 3
        val currentList = _particles.value.toMutableList()
        val skinColor = _selectedSkin.value.primaryColorHex
        for (i in 0 until count) {
            currentList.add(
                GameParticle(
                    id = particleIdCounter++,
                    x = birdX - 10f,
                    y = _birdY.value + Random.nextFloat() * 10f - 5f,
                    vx = -120f - Random.nextFloat() * 80f,
                    vy = Random.nextFloat() * 40f - 20f,
                    color = skinColor,
                    alpha = 1f,
                    size = 5f + Random.nextFloat() * 5f,
                    maxLife = 0.4f + Random.nextFloat() * 0.3f,
                    currentLife = 0f
                )
            )
        }
        _particles.value = currentList
    }

    private fun triggerScoreBurst() {
        val count = 12
        val currentList = _particles.value.toMutableList()
        val scoreColor = 0xFFFFFFFF // Golden or White star spark
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val magnitude = 100f + Random.nextFloat() * 150f
            currentList.add(
                GameParticle(
                    id = particleIdCounter++,
                    x = birdX + 25f,
                    y = _birdY.value,
                    vx = Math.cos(angle.toDouble()).toFloat() * magnitude,
                    vy = Math.sin(angle.toDouble()).toFloat() * magnitude,
                    color = scoreColor,
                    alpha = 1f,
                    size = 4f + Random.nextFloat() * 6f,
                    maxLife = 0.5f + Random.nextFloat() * 0.4f,
                    currentLife = 0f
                )
            )
        }
        _particles.value = currentList
    }

    private fun triggerDisasterExplosion(explosionY: Float) {
        val count = 24
        val currentList = _particles.value.toMutableList()
        val skinColor = _selectedSkin.value.primaryColorHex
        val beakColor = _selectedSkin.value.beakColorHex
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val magnitude = 120f + Random.nextFloat() * 200f
            val col = if (Random.nextBoolean()) skinColor else beakColor
            currentList.add(
                GameParticle(
                    id = particleIdCounter++,
                    x = birdX,
                    y = explosionY,
                    vx = Math.cos(angle.toDouble()).toFloat() * magnitude,
                    vy = Math.sin(angle.toDouble()).toFloat() * magnitude,
                    color = col,
                    alpha = 1f,
                    size = 6f + Random.nextFloat() * 8f,
                    maxLife = 0.7f + Random.nextFloat() * 0.5f,
                    currentLife = 0f
                )
            )
        }
        _particles.value = currentList
    }

    override fun onCleared() {
        super.onCleared()
        gameJob?.cancel()
        particleJob?.cancel()
    }
}
