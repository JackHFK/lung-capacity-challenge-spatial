package com.example.lungcapacity.pcda76db4.ui.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lungcapacity.pcda76db4.BREATH_STAGE_ID
import com.example.lungcapacity.pcda76db4.HOME_WINDOW_ID
import com.example.lungcapacity.pcda76db4.domain.model.GamePage
import com.example.lungcapacity.pcda76db4.ui.game.components.ChallengeDeck
import com.example.lungcapacity.pcda76db4.ui.game.components.GrowthDetailPanel
import com.example.lungcapacity.pcda76db4.ui.game.components.GrowthTree
import com.example.lungcapacity.pcda76db4.ui.game.components.HomeHeader
import com.example.lungcapacity.pcda76db4.ui.game.scene.ArenaPreview
import com.example.lungcapacity.pcda76db4.ui.theme.LungColors
import com.pico.spatial.ui.design.LinearProgressDefaults
import com.pico.spatial.ui.design.LinearProgressIndicator
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.Subwindow
import com.pico.spatial.ui.design.windows.SubwindowPlacement
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.pico.spatial.ui.platform.containers.StageStyle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val HomeDeckInset = 12.dp
private val HomeDeckWidth = 360.dp

@Composable
fun GameScreen(
    modifier: Modifier = Modifier,
) {
    val factory = remember { GameViewModelFactory() }
    val model: GameViewModel = viewModel(factory = factory)
    val state = model.uiState.collectAsStateWithLifecycle().value
    val navigator = LocalSpatialNavigator.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(model, navigator) {
        model.stageRequests.collect {
            navigator.openStage(id = BREATH_STAGE_ID, style = StageStyle.Mixed)
            navigator.minimizeWindowContainer(HOME_WINDOW_ID)
        }
    }

    GameContent(
        state = state,
        onEvent = model::onEvent,
        modifier = modifier,
    )

    state.selectedGrowth?.let { detail ->
        Subwindow(placement = SubwindowPlacement.Right) {
            GrowthDetailPanel(
                detail = detail,
                feedback = state.feedback,
                onClose = { model.onEvent(GameEvent.CloseGrowth) },
                onUpgrade = { model.onEvent(GameEvent.UpgradeGrowth) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun GameContent(
    state: GameUiState,
    onEvent: (GameEvent) -> Unit,
    motionScale: Int = 1,
    modifier: Modifier = Modifier,
) {
    var previewLoadingProgress by remember { mutableStateOf(0f) }
    val previewReady = previewLoadingProgress >= 1f
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Box(
            Modifier.alpha(
                if (state.page != GamePage.TRIAL || previewReady) 1f else 0f,
            ),
        ) {
            HomeHeader(
                page = state.page,
                coins = state.coins,
                onPageSelected = { onEvent(GameEvent.SelectPage(it)) },
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = 24.dp)) {
            ArenaPreview(
                difficulty = state.difficulty,
                active = state.page == GamePage.TRIAL,
                modifierValue = Modifier.fillMaxSize(),
                onLoadingProgress = { previewLoadingProgress = it },
            )
            AnimatedContent(
                targetState = state.page,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (targetState == GamePage.GROWTH) {
                        (
                            fadeIn(
                                tween(
                                    durationMillis = 360 * motionScale,
                                    delayMillis = 60 * motionScale,
                                ),
                            ) +
                                scaleIn(
                                    animationSpec = tween(520 * motionScale),
                                    initialScale = 0.96f,
                                )
                            ) togetherWith (
                            fadeOut(tween(180 * motionScale)) +
                                scaleOut(tween(180 * motionScale), targetScale = 0.985f)
                            )
                    } else {
                        (
                            fadeIn(
                                tween(
                                    durationMillis = 400 * motionScale,
                                    delayMillis = 80 * motionScale,
                                ),
                            ) +
                                slideInHorizontally(
                                    animationSpec = tween(560 * motionScale),
                                    initialOffsetX = { -34 },
                                )
                            ) togetherWith (
                            fadeOut(tween(180 * motionScale)) +
                                slideOutHorizontally(
                                    animationSpec = tween(180 * motionScale),
                                    targetOffsetX = { 18 },
                                )
                            )
                    }
                },
                label = "trial-growth-transition",
            ) { page ->
                when (page) {
                GamePage.TRIAL -> {
                    Box(Modifier.fillMaxSize()) {
                        if (previewReady) {
                            // spatial-ui: intentional-in-page-overlay accepted challenge deck
                            ChallengeDeck(
                                difficulty = state.difficulty,
                                onDifficultySelected = { onEvent(GameEvent.SelectDifficulty(it)) },
                                onStart = { onEvent(GameEvent.StartChallenge) },
                                feedback = state.feedback,
                                deckWidth = HomeDeckWidth,
                                modifierValue = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(
                                        start = HomeDeckInset,
                                        top = HomeDeckInset,
                                        bottom = HomeDeckInset,
                                    ),
                            )
                        } else {
                            CakePreviewLoading(previewLoadingProgress)
                        }
                    }
                }
                GamePage.GROWTH -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = "风脉共鸣",
                                    style = PicoTheme.typography.labelSmall,
                                    color = LungColors.Clay,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "让呼气留下更远的回响",
                                    style = PicoTheme.typography.headlineMedium,
                                    color = LungColors.Soot,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            GrowthLegend()
                        }
                        GrowthTree(
                            ranks = state.growthRanks,
                            selectedId = state.selectedGrowth?.spec?.id,
                            onSelect = { onEvent(GameEvent.SelectGrowth(it)) },
                            motionScale = motionScale,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun CakePreviewLoading(progress: Float) {
    val normalizedProgress = progress.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(LungColors.Daylight) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "${(normalizedProgress * 100f).roundToInt()}%",
                style = PicoTheme.typography.headlineMedium,
                color = LungColors.Soot,
                fontWeight = FontWeight.Bold,
            )
            LinearProgressIndicator(
                progress = { normalizedProgress },
                modifier = Modifier.width(240.dp),
                height = LinearProgressDefaults.Small,
                colors = LinearProgressDefaults.linearProgressColors(
                    indicatorColor = LungColors.Clay,
                    backgroundColor = LungColors.DeckMuted,
                ),
            )
            Text(
                text = "正在准备三档蛋糕模型",
                style = PicoTheme.typography.labelMedium,
                color = LungColors.Soot,
            )
        }
    }
}

@Composable
private fun GrowthLegend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LegendItem(color = LungColors.Wax, label = "可注入")
        Spacer(Modifier.width(16.dp))
        LegendItem(color = LungColors.DeckMuted, label = "未共鸣")
    }
}

@Composable
private fun LegendItem(
    color: androidx.compose.ui.graphics.Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) {
            drawCircle(color)
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = PicoTheme.typography.labelSmall,
            color = LungColors.Charcoal,
        )
    }
}
