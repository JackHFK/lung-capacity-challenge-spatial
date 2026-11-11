package com.example.lungcapacity.pcda76db4.ui.game.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lungcapacity.pcda76db4.domain.model.GamePage
import com.example.lungcapacity.pcda76db4.ui.theme.LungColors
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentControlDefaults
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.TitleAlignment
import com.pico.spatial.ui.design.TitleBar
import com.pico.spatial.ui.design.containerCornerRadius
import com.pico.spatial.ui.design.itemContentPadding
import com.pico.spatial.ui.design.textStyle

@Composable
fun HomeHeader(
    page: GamePage,
    coins: Int,
    onPageSelected: (GamePage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(76.dp)) {
        TitleBar(
            modifier = Modifier.fillMaxWidth(),
            titleAlignment = TitleAlignment.Center,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandFlame()
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "呼吸仪式场",
                            style = PicoTheme.typography.labelSmall,
                            color = LungColors.Clay,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "肺活量大挑战",
                            style = PicoTheme.typography.headlineLarge,
                            color = PicoTheme.colorScheme.labelPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            },
            trailingActions = {
                CoinReadout(coins)
            },
        )
        PageSelector(
            page = page,
            onPageSelected = onPageSelected,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun PageSelector(
    page: GamePage,
    onPageSelected: (GamePage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val size = SegmentControlDefaults.Regular
    SegmentControl(
        modifier = modifier.width(150.dp).height(size.height),
        cornerRadius = size.containerCornerRadius(),
    ) {
        SegmentItem(
            selected = page == GamePage.TRIAL,
            onClick = { onPageSelected(GamePage.TRIAL) },
            textStyle = size.textStyle(),
            contentPadding = size.itemContentPadding(),
            title = { Text("试炼") },
        )
        SegmentItem(
            selected = page == GamePage.GROWTH,
            onClick = { onPageSelected(GamePage.GROWTH) },
            textStyle = size.textStyle(),
            contentPadding = size.itemContentPadding(),
            title = { Text("风脉") },
        )
    }
}

@Composable
private fun CoinReadout(coins: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(18.dp)) {
            drawCircle(LungColors.Brass)
            drawCircle(LungColors.Wax, radius = size.minDimension * 0.28f)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = coins.toString(),
            style = PicoTheme.typography.titleMedium,
            color = PicoTheme.colorScheme.labelPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BrandFlame() {
    Canvas(Modifier.size(width = 44.dp, height = 50.dp)) {
        val shell = Path().apply {
            moveTo(size.width * 0.5f, 0f)
            lineTo(size.width * 0.88f, size.height * 0.22f)
            lineTo(size.width, size.height * 0.72f)
            lineTo(size.width * 0.72f, size.height)
            lineTo(size.width * 0.28f, size.height)
            lineTo(0f, size.height * 0.72f)
            lineTo(size.width * 0.12f, size.height * 0.22f)
            close()
        }
        drawPath(shell, LungColors.Soot)
        rotate(8f, Offset(size.width * 0.5f, size.height * 0.56f)) {
            drawOval(
                color = LungColors.Wax,
                topLeft = Offset(size.width * 0.35f, size.height * 0.24f),
                size = Size(size.width * 0.30f, size.height * 0.50f),
            )
            drawOval(
                color = LungColors.Clay,
                topLeft = Offset(size.width * 0.45f, size.height * 0.44f),
                size = Size(size.width * 0.16f, size.height * 0.26f),
            )
        }
    }
}
