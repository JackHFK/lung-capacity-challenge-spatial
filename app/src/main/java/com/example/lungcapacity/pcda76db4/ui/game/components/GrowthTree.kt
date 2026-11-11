package com.example.lungcapacity.pcda76db4.ui.game.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.lungcapacity.pcda76db4.domain.model.GrowthCatalog
import com.example.lungcapacity.pcda76db4.domain.model.GrowthNodeId
import com.example.lungcapacity.pcda76db4.domain.model.GrowthSpec
import com.example.lungcapacity.pcda76db4.ui.theme.LungColors
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect

private data class NodePosition(
    val id: GrowthNodeId,
    val x: Float,
    val y: Float,
    val special: Boolean,
)

private val nodePositions = listOf(
    NodePosition(GrowthNodeId.FIRST_SPLIT, 0.18f, 0.16f, true),
    NodePosition(GrowthNodeId.CHILD_DRIFT, 0.36f, 0.10f, true),
    NodePosition(GrowthNodeId.SECOND_BLOOM, 0.64f, 0.10f, true),
    NodePosition(GrowthNodeId.EXTINGUISH_WAVE, 0.82f, 0.16f, true),
    NodePosition(GrowthNodeId.FORCE, 0.12f, 0.55f, false),
    NodePosition(GrowthNodeId.DENSITY, 0.31f, 0.45f, false),
    NodePosition(GrowthNodeId.SPREAD, 0.50f, 0.36f, false),
    NodePosition(GrowthNodeId.LIFETIME, 0.69f, 0.45f, false),
    NodePosition(GrowthNodeId.CHARGE, 0.88f, 0.55f, false),
)

@Composable
fun GrowthTree(
    ranks: Map<GrowthNodeId, Int>,
    selectedId: GrowthNodeId?,
    onSelect: (GrowthNodeId) -> Unit,
    motionScale: Int = 1,
    modifier: Modifier = Modifier,
) {
    val coreTransition = rememberInfiniteTransition(label = "breath-core")
    val corePulse by coreTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_300),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath-core-pulse",
    )
    BoxWithConstraints(modifier) {
        val treeWidth = maxWidth
        val treeHeight = maxHeight
        Canvas(Modifier.matchParentSize()) {
            drawRect(LungColors.Daylight)
            val root = Offset(size.width * 0.5f, size.height * 0.88f)
            nodePositions.forEach { position ->
                val end = Offset(size.width * position.x, size.height * position.y)
                val path = Path().apply {
                    moveTo(root.x, root.y)
                    cubicTo(
                        root.x,
                        root.y - size.height * 0.20f,
                        end.x,
                        end.y + size.height * 0.16f,
                        end.x,
                        end.y,
                    )
                }
                val active = (ranks[position.id] ?: 0) > 0
                drawPath(
                    path = path,
                    color = if (active) LungColors.Clay else LungColors.Brass.copy(alpha = 0.46f),
                    style = Stroke(width = if (active) 5f else 3f),
                )
            }
            drawCircle(
                LungColors.Clay.copy(alpha = 0.16f),
                radius = 72f * corePulse,
                center = root,
            )
            drawCircle(LungColors.Soot, radius = 54f, center = root)
            drawOval(
                color = LungColors.Wax,
                topLeft = Offset(root.x - 12f, root.y - 25f),
                size = androidx.compose.ui.geometry.Size(24f, 50f),
            )
        }

        nodePositions.forEachIndexed { index, position ->
            val spec = GrowthCatalog.spec(position.id)
            GrowthNode(
                spec = spec,
                rank = ranks[position.id] ?: 0,
                selected = selectedId == position.id,
                special = position.special,
                entryIndex = index,
                motionScale = motionScale,
                onClick = { onSelect(position.id) },
                modifier = Modifier.offset(
                    x = positionX(treeWidth, position.x, if (position.special) 84.dp else 76.dp),
                    y = positionY(treeHeight, position.y, if (position.special) 104.dp else 96.dp),
                ),
            )
        }

        Text(
            text = "息核",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-30).dp),
            style = PicoTheme.typography.labelMedium,
            color = LungColors.Wax,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun positionX(width: Dp, ratio: Float, nodeSize: Dp): Dp = width * ratio - nodeSize / 2
private fun positionY(height: Dp, ratio: Float, nodeSize: Dp): Dp = height * ratio - nodeSize / 2

@Composable
private fun GrowthNode(
    spec: GrowthSpec,
    rank: Int,
    selected: Boolean,
    special: Boolean,
    entryIndex: Int,
    motionScale: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = if (special) androidx.compose.foundation.shape.CutCornerShape(18.dp)
    else androidx.compose.foundation.shape.CircleShape
    val nodeSize = if (special) 84.dp else 76.dp
    var visible by remember(spec.id) { mutableStateOf(false) }
    LaunchedEffect(spec.id) {
        visible = true
    }
    val delayMillis = (80 + entryIndex * 42) * motionScale
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(
            tween(
                durationMillis = 180 * motionScale,
                delayMillis = delayMillis,
            ),
        ) +
            slideInVertically(
                animationSpec = tween(
                    durationMillis = 520 * motionScale,
                    delayMillis = delayMillis,
                ),
                initialOffsetY = { 18 },
            ) +
            scaleIn(
                animationSpec = tween(
                    durationMillis = 520 * motionScale,
                    delayMillis = delayMillis,
                ),
                initialScale = 0.88f,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(nodeSize)
                    .clip(shape)
                    .spatialHoverEffect()
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.matchParentSize()) {
                    drawPath(
                        path = Path().apply {
                            if (special) {
                                moveTo(size.width * 0.5f, 0f)
                                lineTo(size.width, size.height * 0.3f)
                                lineTo(size.width * 0.84f, size.height)
                                lineTo(size.width * 0.16f, size.height)
                                lineTo(0f, size.height * 0.3f)
                                close()
                            } else {
                                addOval(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
                            }
                        },
                        color = when {
                            special && rank == 0 -> LungColors.Charcoal.copy(alpha = 0.44f)
                            special -> LungColors.Berry
                            rank > 0 -> LungColors.Mint
                            else -> LungColors.Wax
                        },
                    )
                    if (selected) {
                        drawCircle(
                            color = LungColors.Soot,
                            radius = size.minDimension * 0.46f,
                            style = Stroke(width = 5f),
                        )
                    }
                }
                Text(
                    spec.rune,
                    style = PicoTheme.typography.titleLarge,
                    color = if (special) LungColors.Daylight else LungColors.Soot,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                spec.label,
                style = PicoTheme.typography.labelSmall,
                color = LungColors.Soot,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (special) {
                    if (rank > 0) "已共鸣" else "未共鸣"
                } else {
                    "$rank / ${spec.maxRank}"
                },
                style = PicoTheme.typography.bodySmall,
                color = LungColors.Charcoal.copy(alpha = 0.68f),
            )
        }
    }
}
