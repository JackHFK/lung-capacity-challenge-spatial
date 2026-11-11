package com.example.lungcapacity.pcda76db4.ui.stage.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lungcapacity.pcda76db4.domain.model.ChallengePhase
import com.example.lungcapacity.pcda76db4.ui.game.GameUiState
import com.example.lungcapacity.pcda76db4.ui.theme.LungColors
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.LinearProgressDefaults
import com.pico.spatial.ui.design.LinearProgressIndicator

@Composable
fun StageHud(
    state: GameUiState,
    intensity: Float,
    rawRms: Float,
    onExit: () -> Unit,
) {
    val strength by animateFloatAsState(
        targetValue = if (intensity.isFinite()) intensity.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(80),
        label = "breath-strength",
    )
    Row(
        modifier = Modifier
            .width(520.dp)
            .height(64.dp)
            .clip(CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp))
            .drawBehind { drawRect(LungColors.Soot) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("肺活量挑战", color = LungColors.Wax, fontWeight = FontWeight.Bold)
        Text(formatTime(state.elapsedSeconds), color = LungColors.Daylight)
        Text("剩余 ${state.remainingCandles}", color = LungColors.Daylight)
        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier.width(104.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(modifier = Modifier.width(104.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("力度", style = PicoTheme.typography.labelSmall, color = LungColors.Mint)
                Text(
                    "${(strength * 100f).toInt()}%",
                    style = PicoTheme.typography.labelSmall,
                    color = LungColors.Daylight,
                )
            }
            LinearProgressIndicator(
                progress = { strength },
                modifier = Modifier.width(104.dp),
                height = LinearProgressDefaults.Small,
                colors = LinearProgressDefaults.linearProgressColors(
                    indicatorColor = LungColors.Mint,
                    backgroundColor = LungColors.Daylight.copy(alpha = 0.14f),
                ),
            )
            Text(
                "RMS ${formatRms(rawRms)}",
                style = PicoTheme.typography.labelSmall,
                color = LungColors.Daylight.copy(alpha = 0.64f),
            )
        }
        Button(
            onClick = onExit,
            size = ButtonDefaults.Small,
            colors = ButtonDefaults.buttonColors(
                containerColor = LungColors.Clay,
                contentColor = PicoTheme.colorScheme.labelPrimaryLight,
            ),
        ) { Text("返回") }
    }
}

@Composable
fun CountdownPanel(value: Int) {
    Box(
        modifier = Modifier
            .size(170.dp)
            .clip(CutCornerShape(28.dp))
            .drawBehind { drawRect(LungColors.Soot.copy(alpha = 0.92f)) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value.coerceAtLeast(1).toString(),
                style = PicoTheme.typography.headlineLarge,
                color = LungColors.Wax,
                fontWeight = FontWeight.Bold,
            )
            Text("稳住呼吸", color = LungColors.Daylight)
        }
    }
}

@Composable
fun ResultPanel(
    state: GameUiState,
    onRetry: () -> Unit,
    onHome: () -> Unit,
) {
    val visibleState = remember {
        MutableTransitionState(false).apply {
            targetState = true
        }
    }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(
            tween(
                durationMillis = RESULT_ENTRY_DURATION_MILLIS,
                easing = resultEntryEasing,
            ),
        ) +
            scaleIn(
                animationSpec = tween(
                    durationMillis = RESULT_ENTRY_DURATION_MILLIS,
                    easing = resultEntryEasing,
                ),
                initialScale = 0.5f,
            ),
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .height(280.dp)
                .clip(CutCornerShape(topEnd = 28.dp, bottomStart = 28.dp))
                .drawBehind { drawRect(LungColors.Soot.copy(alpha = 0.96f)) }
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "风阵熄灭",
                style = PicoTheme.typography.headlineLarge,
                color = LungColors.Wax,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                ResultMetric("完成时间", formatTime(state.elapsedSeconds))
                ResultMetric("获得火种", "+${state.reward}")
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LungColors.Clay,
                        contentColor = PicoTheme.colorScheme.labelPrimaryLight,
                    ),
                ) { Text("再燃一次") }
                Button(onClick = onHome) { Text("返回仪式场") }
            }
        }
    }
}

@Composable
private fun ResultMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = PicoTheme.typography.labelSmall,
            color = LungColors.Daylight.copy(alpha = 0.64f),
        )
        Text(
            value,
            style = PicoTheme.typography.titleLarge,
            color = LungColors.Daylight,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatTime(seconds: Float): String {
    val totalCentiseconds = (seconds.coerceAtLeast(0f) * 100).toInt()
    return "%02d:%02d.%02d".format(
        totalCentiseconds / 6000,
        (totalCentiseconds / 100) % 60,
        totalCentiseconds % 100,
    )
}

private fun formatRms(rms: Float): String =
    "%.6f".format(if (rms.isFinite()) rms.coerceIn(0f, 1f) else 0f)

private const val RESULT_ENTRY_DURATION_MILLIS = 300
private val resultEntryEasing = CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f)
