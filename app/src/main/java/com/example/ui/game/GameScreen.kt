package com.example.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.HighScoreEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val birdY by viewModel.birdY.collectAsStateWithLifecycle()
    val birdVelocity by viewModel.birdVelocity.collectAsStateWithLifecycle()
    val pipes by viewModel.pipes.collectAsStateWithLifecycle()
    val score by viewModel.score.collectAsStateWithLifecycle()
    val highScore by viewModel.currentHighScore.collectAsStateWithLifecycle()
    val isNewHighScore by viewModel.isNewHighScore.collectAsStateWithLifecycle()
    val particles by viewModel.particles.collectAsStateWithLifecycle()
    val selectedSkin by viewModel.selectedSkin.collectAsStateWithLifecycle()
    val activeBg by viewModel.activeBackground.collectAsStateWithLifecycle()
    val topScores by viewModel.topScores.collectAsStateWithLifecycle()

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                if (gameState == GameState.PLAYING) {
                    viewModel.birdJump()
                }
            }
            .testTag("game_screen_container")
    ) {
        // Core game rendering Canvas
        GameCanvas(
            gameState = gameState,
            birdY = birdY,
            birdVelocity = birdVelocity,
            pipes = pipes,
            particles = particles,
            selectedSkin = selectedSkin,
            activeBg = activeBg,
            virtualWidth = viewModel.virtualWidth,
            virtualHeight = viewModel.virtualHeight,
            groundY = viewModel.groundY,
            birdRadius = viewModel.birdRadius,
            birdX = viewModel.birdX,
            modifier = Modifier.fillMaxSize()
        )

        // Game HUD and Menu overlays
        when (gameState) {
            GameState.MENU -> {
                MenuOverlay(
                    selectedSkin = selectedSkin,
                    activeBg = activeBg,
                    highScore = highScore,
                    onStartGame = { viewModel.startGame() },
                    onNavigateToHighScores = { viewModel.navigateToHighScores() },
                    onSkinChanged = { viewModel.selectSkin(it) },
                    onBgChanged = { viewModel.selectBackground(it) }
                )
            }
            GameState.PLAYING -> {
                PlayingHUD(
                    score = score,
                    isNewHighScore = isNewHighScore,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
            GameState.GAME_OVER -> {
                GameOverOverlay(
                    score = score,
                    highScore = highScore,
                    isNewHighScore = isNewHighScore,
                    onRestart = { viewModel.startGame() },
                    onBackToMenu = { viewModel.navigateToMenu() }
                )
            }
            GameState.HIGHSCORES -> {
                HighScoresOverlay(
                    topScores = topScores,
                    onBack = { viewModel.navigateToMenu() },
                    onClear = { viewModel.clearHighScoreTable() }
                )
            }
        }
    }
}

@Composable
fun GameCanvas(
    gameState: GameState,
    birdY: Float,
    birdVelocity: Float,
    pipes: List<Pipe>,
    particles: List<GameParticle>,
    selectedSkin: BirdSkin,
    activeBg: BackgroundStyle,
    virtualWidth: Float,
    virtualHeight: Float,
    groundY: Float,
    birdRadius: Float,
    birdX: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Calculate uniform scaling keeping virtual resolution locked at 360 x 640
        val scaleX = canvasWidth / virtualWidth
        val scaleY = canvasHeight / virtualHeight

        scale(scaleX, scaleY, pivot = Offset(0f, 0f)) {
            // 1. Draw Sky Background
            val skyColor = Color(activeBg.skyColorHex)
            val skyGradient = Brush.verticalGradient(
                colors = listOf(
                    skyColor,
                    skyColor.copy(alpha = 0.85f),
                    skyColor.copy(alpha = 0.7f)
                ),
                startY = 0f,
                endY = groundY
            )
            drawRect(brush = skyGradient, topLeft = Offset(0f, 0f), size = Size(virtualWidth, groundY))

            // 2. Draw Decorative / Parallax Background elements
            when (activeBg) {
                BackgroundStyle.DAY -> {
                    // Draw warm sun
                    drawCircle(
                        color = Color(0xFFFFF176),
                        radius = 28f,
                        center = Offset(70f, 80f)
                    )
                    // Draw slow moving aesthetic clouds based on continuous clock ticks
                    val timeModulo = (System.currentTimeMillis() / 450) % 500
                    val cloudCol = Color.White.copy(alpha = 0.8f)
                    // Cloud 1
                    drawCircle(cloudCol, 18f, Offset(280f - timeModulo, 130f))
                    drawCircle(cloudCol, 24f, Offset(300f - timeModulo, 130f))
                    drawCircle(cloudCol, 16f, Offset(318f - timeModulo, 130f))

                    // Cloud 2 (offset)
                    val cloudOffset = (System.currentTimeMillis() / 700) % 450
                    drawCircle(cloudCol, 14f, Offset(120f - cloudOffset, 180f))
                    drawCircle(cloudCol, 18f, Offset(135f - cloudOffset, 180f))
                    drawCircle(cloudCol, 12f, Offset(148f - cloudOffset, 180f))
                }
                BackgroundStyle.NIGHT -> {
                    // Draw yellow crescent moon
                    drawCircle(Color(0xFFFFF59D), 20f, Offset(290f, 90f))
                    drawCircle(Color(activeBg.skyColorHex), 16f, Offset(280f, 86f)) // overlapping mask

                    // Draw glittering stars
                    val secSeed = System.currentTimeMillis() / 600
                    val stars = listOf(
                        Offset(40f, 50f), Offset(150f, 100f), Offset(220f, 60f),
                        Offset(90f, 200f), Offset(310f, 180f), Offset(50f, 270f)
                    )
                    stars.forEachIndexed { i, star ->
                        val alpha = if ((secSeed + i) % 3 == 0L) 0.3f else 1.0f
                        drawCircle(Color.White.copy(alpha = alpha), 2f, star)
                    }
                }
                BackgroundStyle.SPACE -> {
                    // Draw cosmic planetary rings
                    drawCircle(Color(0xFFFFCC80).copy(alpha = 0.5f), 35f, Offset(60f, 120f))
                    drawCircle(Color(activeBg.skyColorHex), 32f, Offset(60f, 120f))

                    // Glowing Nebula
                    val nebula = Brush.radialGradient(
                        colors = listOf(Color(0xFFEF5350).copy(alpha = 0.15f), Color.Transparent),
                        center = Offset(260f, 200f),
                        radius = 120f
                    )
                    drawCircle(brush = nebula, radius = 120f, center = Offset(260f, 200f))

                    // Distant starmaps
                    drawCircle(Color.White, 1.5f, Offset(140f, 60f))
                    drawCircle(Color(0xFF81D4FA), 2f, Offset(200f, 280f))
                    drawCircle(Color(0xFFE040FB), 1.5f, Offset(20f, 320f))
                }
            }

            // 3. Draw Tubes / Obstacles
            pipes.forEach { p ->
                val pLeft = p.x - 30f // Width = 60f
                val pipeColor = Color(activeBg.pipeColorHex)
                val pipeAccent = pipeColor.copy(alpha = 0.7f)
                val strokeW = 2.5f

                // --- Top Pipe ---
                val topBottom = p.gapY - 145f / 2f // Gap size = 145f

                // Body
                drawRect(color = pipeColor, topLeft = Offset(pLeft, 0f), size = Size(60f, topBottom))
                // Highlight / 3D shading bar
                drawRect(color = pipeAccent, topLeft = Offset(pLeft + 8f, 0f), size = Size(10f, topBottom))
                // Outline
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(pLeft, -5f),
                    size = Size(60f, topBottom + 5f),
                    style = Stroke(width = strokeW)
                )

                // Cap (draw at the low end of the top tube)
                val capTopHeight = 22f
                val capTopY = topBottom - capTopHeight
                val capLeft = p.x - 34f
                drawRect(color = pipeColor, topLeft = Offset(capLeft, capTopY), size = Size(68f, capTopHeight))
                drawRect(color = pipeAccent, topLeft = Offset(capLeft + 8f, capTopY), size = Size(10f, capTopHeight))
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(capLeft, capTopY),
                    size = Size(68f, capTopHeight),
                    style = Stroke(width = strokeW)
                )

                // --- Bottom Pipe ---
                val bottomTop = p.gapY + 145f / 2f
                val bottomHeight = groundY - bottomTop

                // Body
                drawRect(color = pipeColor, topLeft = Offset(pLeft, bottomTop), size = Size(60f, bottomHeight))
                // Highlight
                drawRect(color = pipeAccent, topLeft = Offset(pLeft + 8f, bottomTop), size = Size(10f, bottomHeight))
                // Outline
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(pLeft, bottomTop),
                    size = Size(60f, bottomHeight + 5f),
                    style = Stroke(width = strokeW)
                )

                // Cap (draw at top end of bottom tube)
                drawRect(color = pipeColor, topLeft = Offset(capLeft, bottomTop), size = Size(68f, capTopHeight))
                drawRect(color = pipeAccent, topLeft = Offset(capLeft + 8f, bottomTop), size = Size(10f, capTopHeight))
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(capLeft, bottomTop),
                    size = Size(68f, capTopHeight),
                    style = Stroke(width = strokeW)
                )
            }

            // 4. Draw Particles (Trails, Explosions or Score visual bursts)
            particles.forEach { part ->
                drawCircle(
                    color = Color(part.color).copy(alpha = part.alpha),
                    radius = part.size,
                    center = Offset(part.x, part.y)
                )
            }

            // 5. Draw the Bird Actor
            if (gameState == GameState.PLAYING || gameState == GameState.GAME_OVER) {
                val pColor = Color(selectedSkin.primaryColorHex)
                val eyeCol = Color(selectedSkin.eyeColorHex)
                val beakCol = Color(selectedSkin.beakColorHex)

                // Clamping rotation degrees based on flight velocity
                val angleRotation = (birdVelocity * 0.16f).coerceIn(-32f, 75f)

                withTransform({
                    rotate(degrees = angleRotation, pivot = Offset(birdX, birdY))
                }) {
                    // Bird outline body shadow (arcade look)
                    drawCircle(
                        color = Color.Black,
                        radius = birdRadius + 1f,
                        center = Offset(birdX, birdY)
                    )

                    // Core Body
                    drawCircle(
                        color = pColor,
                        radius = birdRadius,
                        center = Offset(birdX, birdY)
                    )

                    // Flying flapping animated wing
                    val isWingStateUp = (System.currentTimeMillis() / 120 % 2) == 0L
                    val wingOffsetDy = if (isWingStateUp && gameState == GameState.PLAYING) -3f else 3f
                    val wingX = birdX - 11f
                    val wingY = birdY - 1f + wingOffsetDy
                    
                    drawOval(
                        color = Color.Black,
                        topLeft = Offset(wingX - 1f, wingY - 6f),
                        size = Size(12f, 12f)
                    )
                    drawOval(
                        color = pColor.copy(alpha = 0.9f),
                        topLeft = Offset(wingX, wingY - 5f),
                        size = Size(10f, 10f)
                    )

                    // Comic Eye
                    val eyeCenterX = birdX + 5f
                    val eyeCenterY = birdY - 4f
                    drawCircle(color = Color.White, radius = 5.5f, center = Offset(eyeCenterX, eyeCenterY))
                    drawCircle(color = Color.Black, radius = 5.5f, center = Offset(eyeCenterX, eyeCenterY), style = Stroke(1.5f))
                    
                    // Pupil moving forward
                    drawCircle(color = eyeCol, radius = 2.5f, center = Offset(eyeCenterX + 1.2f, eyeCenterY))

                    // Double Path Beak (Upper and lower lip)
                    val beakY = birdY + 1f
                    val upperBeak = Path().apply {
                        moveTo(birdX + 11f, beakY - 3f)
                        lineTo(birdX + 22f, beakY)
                        lineTo(birdX + 11f, beakY + 3f)
                        close()
                    }
                    val lowerBeak = Path().apply {
                        moveTo(birdX + 11f, beakY + 2f)
                        lineTo(birdX + 18f, beakY + 4f)
                        lineTo(birdX + 11f, beakY + 6f)
                        close()
                    }

                    drawPath(path = upperBeak, color = beakCol)
                    drawPath(path = upperBeak, color = Color.Black, style = Stroke(1.5f))

                    drawPath(path = lowerBeak, color = beakCol)
                    drawPath(path = lowerBeak, color = Color.Black, style = Stroke(1.5f))
                }
            }

            // 6. Draw Dirt / Grass Ground base
            val groundColor = Color(activeBg.groundColorHex)
            val grassColor = Color(0xFF66BB6A)

            // Dynamic diagonal stripes scrolling animation
            val groundSpeedScroll = if (gameState == GameState.PLAYING) {
                ((System.currentTimeMillis() / 15) % 40).toFloat()
            } else 0f

            // Ground base body
            drawRect(color = groundColor, topLeft = Offset(0f, groundY), size = Size(virtualWidth, virtualHeight - groundY))
            
            // Grass level top
            drawRect(color = grassColor, topLeft = Offset(0f, groundY), size = Size(virtualWidth, 14f))
            drawRect(
                color = Color.Black,
                topLeft = Offset(-10f, groundY),
                size = Size(virtualWidth + 20f, 14f),
                style = Stroke(width = 2.5f)
            )

            // Draw nostalgic arcade visual ground stripes
            val stripeWidth = 12f
            val stripeSpacing = 28f
            var sx = -stripeWidth - groundSpeedScroll
            while (sx < virtualWidth + stripeSpacing) {
                val stripePath = Path().apply {
                    moveTo(sx, groundY + 14f)
                    lineTo(sx + stripeWidth, groundY + 14f)
                    lineTo(sx + stripeWidth - 8f, virtualHeight)
                    lineTo(sx - 8f, virtualHeight)
                    close()
                }
                drawPath(stripePath, color = Color.Black.copy(alpha = 0.08f))
                sx += stripeSpacing
            }

            // Strong solid borders
            drawRect(
                color = Color.Black,
                topLeft = Offset(0f, groundY),
                size = Size(virtualWidth, virtualHeight - groundY),
                style = Stroke(width = 2f)
            )
        }
    }
}

@Composable
fun PlayingHUD(
    score: Int,
    isNewHighScore: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // High contrast Arcade Score Badge
        Box(
            modifier = Modifier
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(16.dp))
                .border(2.dp, Color.White, shape = RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = score.toString(),
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("score_display")
                )
                
                if (isNewHighScore) {
                    val infiniteTransition = rememberInfiniteTransition(label = "flash")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(400, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulser"
                    )
                    
                    Text(
                        text = "¡NUEVO RÉCORD!",
                        color = Color(0xFFFFD700).copy(alpha = pulseAlpha),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}

@Composable
fun MenuOverlay(
    selectedSkin: BirdSkin,
    activeBg: BackgroundStyle,
    highScore: Int,
    onStartGame: () -> Unit,
    onNavigateToHighScores: () -> Unit,
    onSkinChanged: (BirdSkin) -> Unit,
    onBgChanged: (BackgroundStyle) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Giant retro title text
            Text(
                text = "FLAPPY BIRD",
                color = Color(0xFFFFEB3B),
                fontSize = 44.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .shadow(elevation = 12.dp)
                    .padding(bottom = 6.dp)
            )

            Text(
                text = "EDICIÓN DE LUJO",
                color = Color.White,
                fontSize = 12.sp,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // High Score Banner
            Box(
                modifier = Modifier
                    .shadow(8.dp, RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Récord",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "RÉCORD MÁXIMO: $highScore PTS",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action play buttons
            Button(
                onClick = onStartGame,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(2.dp, Color.White),
                modifier = Modifier
                    .width(180.dp)
                    .height(52.dp)
                    .shadow(10.dp, RoundedCornerShape(12.dp))
                    .testTag("play_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Text("JUGAR", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onNavigateToHighScores,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, Color.White),
                modifier = Modifier
                    .width(180.dp)
                    .height(44.dp)
                    .shadow(6.dp, RoundedCornerShape(12.dp))
                    .testTag("highscores_button")
            ) {
                Text("HISTORIZADO", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Skin selector row
            Text(
                text = "ELIGE TU ASPECTO:",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start).padding(start = 12.dp, bottom = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                BirdSkin.values().forEach { skin ->
                    val isSelected = skin == selectedSkin
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) Color.White.copy(alpha = 0.25f)
                                else Color.Transparent
                            )
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) Color(skin.primaryColorHex) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSkinChanged(skin) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Mini circle representation of the bird skin
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color(skin.primaryColorHex), CircleShape)
                                    .border(1.dp, Color.Black, CircleShape)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = skin.displayName.split(" ").last(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Escenario / Theme Selector
            Text(
                text = "ESCENARIO:",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start).padding(start = 12.dp, bottom = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                BackgroundStyle.values().forEach { bg ->
                    val isSelected = bg == activeBg
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSelected) Color(bg.skyColorHex).copy(alpha = 0.6f)
                                else Color.White.copy(alpha = 0.05f)
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onBgChanged(bg) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = bg.displayName,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GameOverOverlay(
    score: Int,
    highScore: Int,
    isNewHighScore: Boolean,
    onRestart: () -> Unit,
    onBackToMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(Color(0xFF263238), RoundedCornerShape(20.dp))
                .border(2.dp, Color.White, RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PARTIDA CONCLUIDA",
                color = Color(0xFFEF5350),
                fontSize = 24.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Scoreboard display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PUNTUACIÓN OBTENIDA",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = score.toString(),
                        color = Color.White,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black
                    )

                    Divider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Record de siempre:",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "$highScore pts",
                            color = Color(0xFFFFD700),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isNewHighScore) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF4CAF50), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "¡RÉCORD SUPERADO!",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Row actions
            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.5.dp, Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("try_again_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                    Text("REINTENTAR", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onBackToMenu,
                border = BorderStroke(1.dp, Color.White),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("menu_button")
            ) {
                Text("MENÚ PRINCIPAL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun HighScoresOverlay(
    topScores: List<HighScoreEntity>,
    onBack: () -> Unit,
    onClear: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E272C))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "TABLA DE RÉCORDS",
                color = Color(0xFFFFD700),
                fontSize = 24.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
            )

            // Highscores Scrolled Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                if (topScores.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "¡Aún no hay récords registrados!\n\nJuega una partida para registrar tu puntuación.",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(topScores) { index, item ->
                            HighScoreRow(place = index + 1, entity = item)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Reset scores button and Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "BORRAR TODO",
                        color = Color(0xFFEF5350),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "VOLVER",
                        color = Color(0xFF1E272C),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun HighScoreRow(
    place: Int,
    entity: HighScoreEntity
) {
    val placeColor = when (place) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> Color.LightGray
    }

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val formattedDate = remember(entity.timestamp) { dateFormatter.format(Date(entity.timestamp)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
            .border(
                width = if (place <= 3) 1.dp else 0.dp,
                color = placeColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Placement number
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(placeColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = place.toString(),
                color = Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Username and date
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = entity.playerName,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formattedDate,
                color = Color.Gray,
                fontSize = 9.sp
            )
        }

        // Score
        Text(
            text = "${entity.score} PTS",
            color = placeColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black
        )
    }
}
