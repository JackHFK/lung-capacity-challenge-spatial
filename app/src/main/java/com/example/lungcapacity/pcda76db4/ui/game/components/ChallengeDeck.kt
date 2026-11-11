package com.example.lungcapacity.pcda76db4.ui.game.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.lungcapacity.pcda76db4.domain.model.Difficulty
import com.example.lungcapacity.pcda76db4.ui.theme.LungColors
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.HorizontalDivider
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentControlDefaults
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.containerCornerRadius
import com.pico.spatial.ui.design.itemContentPadding
import com.pico.spatial.ui.design.textStyle

@Composable
fun ChallengeDeck(
    difficulty: Difficulty,
    onDifficultySelected: (Difficulty) -> Unit,
    onStart: () -> Unit,
    feedback: String = "",
    deckWidth: Dp = 360.dp,
    modifierValue: Modifier = Modifier,
) {
    val shape = CutCornerShape(topEnd = 28.dp, bottomStart = 28.dp)
    Column(
        modifier = modifierValue
            .width(deckWidth)
            .fillMaxHeight()
            .clip(shape)
            .drawBehind {
                drawRect(LungColors.Soot)
                drawRect(
                    color = LungColors.Clay,
                    size = androidx.compose.ui.geometry.Size(
                        6.dp.toPx(),
                        116.dp.toPx(),
                    ),
                )
            }
            .padding(start = 26.dp, top = 24.dp, end = 26.dp, bottom = 22.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    "本轮挑战",
                    style = PicoTheme.typography.labelSmall,
                    color = LungColors.Wax,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    difficulty.title,
                    style = PicoTheme.typography.headlineLarge,
                    color = LungColors.Daylight,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("TRIAL", style = PicoTheme.typography.labelSmall, color = LungColors.Wax)
                Text(
                    "01",
                    style = PicoTheme.typography.headlineLarge,
                    color = LungColors.Wax.copy(alpha = 0.24f),
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            difficulty.description,
            style = PicoTheme.typography.bodySmall,
            color = LungColors.DeckBody,
        )

        Spacer(Modifier.height(17.dp))
        FieldLabel("风阵规模")
        Spacer(Modifier.height(6.dp))
        DifficultySegments(difficulty, onDifficultySelected)

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = LungColors.Daylight.copy(alpha = 0.16f))
        if (feedback.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                feedback,
                style = PicoTheme.typography.labelSmall,
                color = LungColors.DeckMuted,
            )
        }

        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = LungColors.DeckDivider,
            )
            Button(
                onClick = onStart,
                size = ButtonDefaults.Regular,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LungColors.Clay,
                    contentColor = PicoTheme.colorScheme.labelPrimaryLight,
                ),
            ) {
                Text("点燃试炼")
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = PicoTheme.typography.labelSmall,
        color = LungColors.DeckMuted,
    )
}

@Composable
private fun DifficultySegments(
    selected: Difficulty,
    onSelected: (Difficulty) -> Unit,
) {
    val size = SegmentControlDefaults.Regular
    val colors = challengeSegmentColors()
    SegmentControl(
        modifier = Modifier.width(172.dp).height(size.height),
        backgroundColor = LungColors.DeckControlSurface,
        cornerRadius = size.containerCornerRadius(),
    ) {
        Difficulty.entries.forEach { item ->
            SegmentItem(
                selected = selected == item,
                onClick = { onSelected(item) },
                textStyle = size.textStyle(),
                colors = colors,
                contentPadding = size.itemContentPadding(),
                title = { Text(item.label) },
            )
        }
    }
}

@Composable
private fun challengeSegmentColors() = SegmentControlDefaults.colors(
    itemBackgroundColor = androidx.compose.ui.graphics.Color.Transparent,
    itemContentColor = LungColors.Soot.copy(alpha = 0.72f),
    selectedItemBackgroundColor = LungColors.Daylight,
    selectedItemContentColor = LungColors.Soot,
)
