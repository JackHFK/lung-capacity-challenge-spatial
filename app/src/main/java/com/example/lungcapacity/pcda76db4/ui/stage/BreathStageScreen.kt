package com.example.lungcapacity.pcda76db4.ui.stage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lungcapacity.pcda76db4.HOME_WINDOW_ID
import com.example.lungcapacity.pcda76db4.domain.model.ChallengeConfig
import com.example.lungcapacity.pcda76db4.domain.model.ChallengePhase
import com.example.lungcapacity.pcda76db4.platform.audio.MicrophoneBreathInput
import com.example.lungcapacity.pcda76db4.ui.game.GameEvent
import com.example.lungcapacity.pcda76db4.ui.game.GameUiState
import com.example.lungcapacity.pcda76db4.ui.game.GameViewModel
import com.example.lungcapacity.pcda76db4.ui.game.GameViewModelFactory
import com.example.lungcapacity.pcda76db4.ui.game.scene.BreathArenaScene
import com.example.lungcapacity.pcda76db4.ui.game.scene.BreathFrameSystem
import com.example.lungcapacity.pcda76db4.ui.game.scene.cakeRotationGesture
import com.example.lungcapacity.pcda76db4.ui.game.scene.FrameParticipant
import com.example.lungcapacity.pcda76db4.ui.game.scene.HeadEmissionPose
import com.example.lungcapacity.pcda76db4.ui.stage.components.CountdownPanel
import com.example.lungcapacity.pcda76db4.ui.stage.components.ResultPanel
import com.example.lungcapacity.pcda76db4.ui.stage.components.StageHud
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.tracking.hmd.HMDPose
import com.pico.spatial.tracking.hmd.HMDTrackingData
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

private const val PANEL_STATUS = 1
private const val PANEL_COUNTDOWN = 2
private const val PANEL_RESULT = 3

@Composable
fun BreathStageScreen() {
    val factory = remember { GameViewModelFactory() }
    val model: GameViewModel = viewModel(factory = factory)
    val state by model.uiState.collectAsStateWithLifecycle()
    val currentState by rememberUpdatedState(state)
    val navigator = LocalSpatialNavigator.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val microphone = remember(context, scope) { MicrophoneBreathInput(context, scope) }
    val intensity by microphone.intensityFlow.collectAsState()
    val rawRms by microphone.rawRmsFlow.collectAsState()
    val hmdProvider = remember { HMDTrackingProvider() }
    val hmdData by hmdProvider.dataFlow.collectAsState(
        initial = HMDTrackingData(HMDPose(Vector3.ZERO, Quat.identity()), 0L),
    )
    val currentHmd by rememberUpdatedState(hmdData)
    val hmdAnchor = remember { Entity() }
    val scene = remember(state.difficulty, state.growthRanks) {
        BreathArenaScene(
            config = ChallengeConfig(
                difficulty = state.difficulty,
                growthRanks = state.growthRanks,
            ),
            preview = false,
            onCandleExtinguished = {
                scope.launch { model.onCandleExtinguished() }
            },
        )
    }
    val anchorLoop = remember { StageAnchorLoop() }
    val currentScene by rememberUpdatedState(scene)

    val backHome: () -> Unit = {
        scope.launch {
            model.onEvent(GameEvent.ReturnHome)
            navigator.restoreWindowContainer(HOME_WINDOW_ID)
            navigator.closeStage()
        }
    }

    LaunchedEffect(microphone) {
        try {
            val started = microphone.start()
            if (!started) {
                model.onStageMounted(false)
                navigator.restoreWindowContainer(HOME_WINDOW_ID)
                navigator.closeStage()
                return@LaunchedEffect
            }
            scene.awaitReady()
            scene.restartAssembly()
            model.onStageMounted(true)
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { microphone.stop() }
        }
    }
    LaunchedEffect(scene, state.phase) {
        scene.submit(state.phase)
    }

    DisposableEffect(anchorLoop, hmdProvider) {
        hmdProvider.start()
        BreathFrameSystem.add(anchorLoop)
        onDispose {
            BreathFrameSystem.remove(anchorLoop)
            anchorLoop.close()
            hmdProvider.stop()
        }
    }
    DisposableEffect(scene) {
        BreathFrameSystem.add(scene)
        onDispose {
            BreathFrameSystem.remove(scene)
            scene.dispose()
        }
    }

    // Web-only nodes intentionally absent in Spatial: web-input-adapter, intensity-slider, breath-button.
    SpatialView(
        modifier = Modifier
            .fillMaxSize()
            .cakeRotationGesture(context, scene),
        attachments = {
            AttachmentPanel(PANEL_STATUS) {
                if (state.phase == ChallengePhase.COUNTDOWN || state.phase == ChallengePhase.RUNNING) {
                    StageHud(
                        state = state,
                        intensity = intensity,
                        rawRms = rawRms,
                        onExit = backHome,
                    )
                }
            }
            AttachmentPanel(PANEL_COUNTDOWN) {
                if (state.phase == ChallengePhase.COUNTDOWN && state.countdown > 0) {
                    CountdownPanel(state.countdown)
                }
            }
            AttachmentPanel(PANEL_RESULT) {
                if (state.phase == ChallengePhase.RESULT) {
                    ResultPanel(
                        state = state,
                        onRetry = { model.onEvent(GameEvent.RetryChallenge) },
                        onHome = backHome,
                    )
                }
            }
        },
    ) { content, attachments ->
        content.addEntity(hmdAnchor)
        scene.attach(content, scope)
        val placed = mutableSetOf<Int>()

        fun placePanel(id: Int, local: Vector3, scale: Float) {
            if (id in placed) return
            attachments.entity(id)?.let { panel ->
                val slot = Entity().apply {
                    components.get(TransformComponent::class.java)?.apply {
                        setPosition(local)
                        if (scale != 1f) scaleBy(scale)
                    }
                    addChild(panel)
                }
                hmdAnchor.addChild(slot)
                placed += id
            }
        }

        anchorLoop.attach(hmdAnchor) { dt ->
            val hmdPose = currentHmd.hmdPose
            val parent = hmdAnchor.getParent()
            hmdAnchor.components.get(TransformComponent::class.java)?.apply {
                setPosition(parent?.convertPositionFrom(hmdPose.position, null) ?: hmdPose.position)
                setQuaternion(parent?.convertRotationFrom(hmdPose.rotation, null) ?: hmdPose.rotation)
            }
            currentScene.submitBreathInput(HeadEmissionPose(hmdPose.position, hmdPose.rotation, microphone.intensity))
            placePanel(
                PANEL_STATUS,
                Vector3(0f, StagePanelLayout.StatusY, StagePanelLayout.ForwardZ),
                0.75f,
            )
            placePanel(
                PANEL_COUNTDOWN,
                Vector3(0f, StagePanelLayout.CountdownY, StagePanelLayout.ForwardZ),
                1f,
            )
            placePanel(
                PANEL_RESULT,
                Vector3(0f, StagePanelLayout.ResultY, StagePanelLayout.ResultForwardZ),
                1f,
            )
            if (currentState.phase == ChallengePhase.RUNNING) {
                anchorLoop.elapsed += dt
                if (anchorLoop.publishClock >= 0.1f) {
                    anchorLoop.publishClock = 0f
                    model.onStageFrame(currentState.elapsedSeconds + anchorLoop.elapsed)
                    anchorLoop.elapsed = 0f
                } else {
                    anchorLoop.publishClock += dt
                }
            } else {
                anchorLoop.elapsed = 0f
                anchorLoop.publishClock = 0f
            }
        }
    }
}

private class StageAnchorLoop : FrameParticipant {
    private var owner: Entity? = null
    private var tick: (Float) -> Unit = {}
    var elapsed: Float = 0f
    var publishClock: Float = 0f

    fun attach(owner: Entity, tick: (Float) -> Unit) {
        this.owner = owner
        this.tick = tick
    }

    override fun update(context: SceneUpdateContext) {
        if (owner?.scene == context.scene) {
            tick(context.deltaTime.coerceIn(0f, 0.05f))
        }
    }

    fun close() {
        owner = null
        tick = {}
    }
}
