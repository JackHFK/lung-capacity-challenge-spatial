package com.example.lungcapacity.pcda76db4.ui.game.scene

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture

internal fun Modifier.cakeRotationGesture(
    context: Context,
    scene: BreathArenaScene,
): Modifier =
    pointerInput(context, scene) {
        detectSpatialDragGesture(
            context,
            TargetEntity.any(scene::isCakeRotationTarget),
        ) { event ->
            scene.rotateCake(event.dragAmount.x)
        }
    }
