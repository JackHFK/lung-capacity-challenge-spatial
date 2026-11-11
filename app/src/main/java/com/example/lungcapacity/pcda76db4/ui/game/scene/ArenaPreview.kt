package com.example.lungcapacity.pcda76db4.ui.game.scene

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import com.example.lungcapacity.pcda76db4.domain.model.ChallengeConfig
import com.example.lungcapacity.pcda76db4.domain.model.Difficulty
import com.example.lungcapacity.pcda76db4.ui.theme.LungColors
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView

private val PreviewDifficulties = listOf(
    Difficulty.EASY,
    Difficulty.NORMAL,
    Difficulty.HARD,
)

@Composable
fun ArenaPreview(
    difficulty: Difficulty,
    active: Boolean,
    modifierValue: Modifier = Modifier,
    onLoadingProgress: (Float) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val fitProbe = remember {
        SpatialViewVisibleRegionProbe(
            profileId = "home-preview",
            sdkVersion = "6.2.6",
        )
    }
    var sceneProgress by remember {
        mutableStateOf(PreviewDifficulties.associateWith { CakeLoadingProgress() })
    }
    val scenes = remember {
        PreviewDifficulties.associateWith { previewDifficulty ->
            BreathArenaScene(
                config = ChallengeConfig(previewDifficulty),
                preview = true,
                onLoadingProgress = { progress ->
                    sceneProgress = sceneProgress + (previewDifficulty to progress)
                },
            ).apply {
                setVisible(false)
            }
        }
    }
    var readyDifficulties by remember(scenes) { mutableStateOf(emptySet<Difficulty>()) }

    DisposableEffect(scenes) {
        onLoadingProgress(0f)
        scenes.values.forEach { BreathFrameSystem.add(it) }
        onDispose {
            scenes.values.forEach { scene ->
                BreathFrameSystem.remove(scene)
                scene.dispose()
            }
            onLoadingProgress(0f)
        }
    }
    scenes.forEach { (previewDifficulty, scene) ->
        LaunchedEffect(scene) {
            scene.awaitReady()
            readyDifficulties = readyDifficulties + previewDifficulty
        }
    }
    val allScenesReady = readyDifficulties.size == scenes.size
    val sharedResourceProgress = PreviewDifficulties.maxOf {
        sceneProgress.getValue(it).sharedResources
    }
    val sceneBuildProgress = PreviewDifficulties
        .sumOf { sceneProgress.getValue(it).sceneBuild.toDouble() }
        .div(PreviewDifficulties.size)
        .toFloat()
    val loadingProgress =
        sharedResourceProgress * SHARED_RESOURCE_PROGRESS_SHARE +
            sceneBuildProgress * SCENE_BUILD_PROGRESS_SHARE
    LaunchedEffect(loadingProgress, onLoadingProgress) {
        onLoadingProgress(loadingProgress)
    }
    LaunchedEffect(active, difficulty, allScenesReady) {
        if (allScenesReady) {
            scenes.values.forEach { it.setVisible(false) }
            if (active) {
                scenes.getValue(difficulty).apply {
                    restartAssembly()
                    setVisible(true)
                }
            }
        }
    }

    Box(
        modifier = if (active) {
            modifierValue
                .clip(RoundedCornerShape(8.dp))
                .drawBehind { drawRect(LungColors.Daylight) }
        } else {
            Modifier.size(0.dp)
        },
    ) {
        SpatialView(
            modifier = Modifier
                .fillMaxSize()
                .captureSpatialViewVisibleRegion(fitProbe),
            initial = { content, _ ->
                fitProbe.bind(content)
                scenes.values.forEach { it.attach(content, scope) }
            },
        )
        Text(
            text = "${difficulty.title} · 组装预览",
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            style = PicoTheme.typography.labelSmall,
            color = LungColors.Soot,
        )
    }
}

private const val SHARED_RESOURCE_PROGRESS_SHARE = 0.60f
private const val SCENE_BUILD_PROGRESS_SHARE = 0.40f
