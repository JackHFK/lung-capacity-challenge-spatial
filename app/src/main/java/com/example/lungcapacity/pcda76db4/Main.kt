package com.example.lungcapacity.pcda76db4

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lungcapacity.pcda76db4.ui.game.GameScreen
import com.example.lungcapacity.pcda76db4.ui.stage.BreathStageScreen
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.Stage

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            PicoTheme {
                GameScreen(
                    // responsive-rule: home-minimum
                    modifier = Modifier.windowConstraints(
                        minWidth = 920.dp,
                        minHeight = 680.dp,
                        maxWidth = 1120.dp,
                        maxHeight = 820.dp,
                    ),
                )
            }
        }
        Stage(id = BREATH_STAGE_ID) {
            PicoTheme {
                BreathStageScreen()
            }
        }
    }

const val HOME_WINDOW_ID = "Home"
const val BREATH_STAGE_ID = "breath-challenge"
