package com.example.lungcapacity.pcda76db4.ui.game.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lungcapacity.pcda76db4.R
import com.example.lungcapacity.pcda76db4.ui.game.GrowthDetailUi
import com.example.lungcapacity.pcda76db4.ui.theme.LungColors
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.TitleAlignment
import com.pico.spatial.ui.design.TitleBar

@Composable
fun GrowthDetailPanel(
    detail: GrowthDetailUi,
    feedback: String,
    onClose: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TitleBar(
            modifier = Modifier.fillMaxWidth(),
            titleAlignment = TitleAlignment.Center,
            title = {
                Text(
                    detail.spec.label,
                    style = PicoTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            leadingActions = {
                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cross),
                        contentDescription = "关闭成长详情",
                    )
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.matchParentSize()) {
                        drawCircle(LungColors.Wax)
                        drawCircle(
                            LungColors.Brass,
                            radius = size.minDimension * 0.47f,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f),
                        )
                    }
                    Text(
                        detail.spec.rune,
                        style = PicoTheme.typography.headlineLarge,
                        color = LungColors.Soot,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.size(16.dp))
                Column {
                    Text(
                        detail.spec.category,
                        style = PicoTheme.typography.labelLarge,
                        color = LungColors.Clay,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "等级 ${detail.rank} / ${detail.spec.maxRank}",
                        style = PicoTheme.typography.bodyMedium,
                        color = PicoTheme.colorScheme.labelSecondary,
                    )
                }
            }

            Text(
                detail.spec.description,
                style = PicoTheme.typography.bodyMedium,
                color = PicoTheme.colorScheme.labelPrimary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EffectBlock(
                    label = "此刻",
                    value = detail.currentEffect,
                    modifier = Modifier.weight(1f),
                )
                EffectBlock(
                    label = "共鸣后",
                    value = detail.nextEffect,
                    modifier = Modifier.weight(1f),
                    highlighted = true,
                )
            }

            Text(
                "共鸣条件",
                style = PicoTheme.typography.labelMedium,
                color = PicoTheme.colorScheme.labelSecondary,
            )
            Text(
                detail.prerequisiteText,
                style = PicoTheme.typography.bodyMedium,
                color = PicoTheme.colorScheme.labelPrimary,
            )
            if (feedback.isNotBlank()) {
                Text(
                    feedback,
                    style = PicoTheme.typography.bodySmall,
                    color = if (feedback.startsWith("升级完成")) LungColors.Mint else LungColors.Berry,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "注入火种",
                    style = PicoTheme.typography.labelSmall,
                    color = PicoTheme.colorScheme.labelSecondary,
                )
                Text(
                    detail.cost?.toString() ?: "—",
                    style = PicoTheme.typography.titleMedium,
                    color = LungColors.Brass,
                    fontWeight = FontWeight.Bold,
                )
            }
            Button(
                onClick = onUpgrade,
                enabled = detail.upgradeEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LungColors.Clay,
                    contentColor = PicoTheme.colorScheme.labelPrimaryLight,
                ),
            ) {
                Text(detail.upgradeLabel)
            }
        }
    }
}

@Composable
private fun EffectBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Column(
        modifier = modifier
            .height(110.dp)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = PicoTheme.typography.labelSmall,
            color = if (highlighted) LungColors.Mint else PicoTheme.colorScheme.labelSecondary,
        )
        Text(
            value,
            style = PicoTheme.typography.bodyMedium,
            color = PicoTheme.colorScheme.labelPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}
