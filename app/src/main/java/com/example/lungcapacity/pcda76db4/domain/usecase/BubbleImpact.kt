package com.example.lungcapacity.pcda76db4.domain.usecase

import kotlin.math.sqrt

internal data class Point3(val x: Float, val y: Float, val z: Float)

/** Sweeps the entire frame segment against the flame's bubble-expanded ellipsoid. */
internal object BubbleImpact {
    /** Sweep a bubble-expanded board volume, including hits between rendered frames. */
    fun boxFraction(
        start: Point3,
        end: Point3,
        center: Point3,
        halfExtent: Point3,
        bubbleRadius: Point3,
    ): Float? {
        var entry = 0f
        var exit = 1f
        val origins = floatArrayOf(start.x - center.x, start.y - center.y, start.z - center.z)
        val directions = floatArrayOf(end.x - start.x, end.y - start.y, end.z - start.z)
        val extents = floatArrayOf(
            halfExtent.x + bubbleRadius.x,
            halfExtent.y + bubbleRadius.y,
            halfExtent.z + bubbleRadius.z,
        )
        for (axis in 0..2) {
            val direction = directions[axis]
            val origin = origins[axis]
            if (kotlin.math.abs(direction) < 1e-8f) {
                if (kotlin.math.abs(origin) > extents[axis]) return null
            } else {
                val a = (-extents[axis] - origin) / direction
                val b = (extents[axis] - origin) / direction
                entry = maxOf(entry, minOf(a, b))
                exit = minOf(exit, maxOf(a, b))
                if (entry > exit) return null
            }
        }
        return entry
    }

    fun diskFraction(start: Point3, end: Point3, center: Point3, radius: Float): Float? {
        if (start.z <= center.z || end.z > center.z) return null
        val fraction = (center.z - start.z) / (end.z - start.z)
        val x = start.x + (end.x - start.x) * fraction - center.x
        val y = start.y + (end.y - start.y) * fraction - center.y
        return fraction.takeIf { x * x + y * y <= radius * radius }
    }

    fun fraction(
        start: Point3,
        end: Point3,
        center: Point3,
        flameHalfExtent: Point3,
        bubbleRadius: Point3,
    ): Float? {
        val rx = (flameHalfExtent.x + bubbleRadius.x).coerceAtLeast(0.0001f)
        val ry = (flameHalfExtent.y + bubbleRadius.y).coerceAtLeast(0.0001f)
        val rz = (flameHalfExtent.z + bubbleRadius.z).coerceAtLeast(0.0001f)
        val sx = (start.x - center.x) / rx
        val sy = (start.y - center.y) / ry
        val sz = (start.z - center.z) / rz
        val dx = (end.x - start.x) / rx
        val dy = (end.y - start.y) / ry
        val dz = (end.z - start.z) / rz
        val c = sx * sx + sy * sy + sz * sz - 1f
        if (c <= 0f) return 0f
        val a = dx * dx + dy * dy + dz * dz
        if (a <= 1e-10f) return null
        val b = 2f * (sx * dx + sy * dy + sz * dz)
        val discriminant = b * b - 4f * a * c
        if (discriminant < 0f) return null
        val t = (-b - sqrt(discriminant)) / (2f * a)
        return t.takeIf { it in 0f..1f }
    }
}

/** One hit deposits its energy once; sustained gentle hits do not fight frame-rate decay. */
internal class FlameResponse(val threshold: Float) {
    var exposure = 0f
        private set
    var pulse = 0f
        private set
    var extinguished = false
        private set
    private var recoveryDelay = 0f

    fun hit(energy: Float): Boolean {
        if (extinguished || !energy.isFinite() || energy <= 0f) return false
        exposure += energy
        pulse = 1f
        recoveryDelay = 0.55f
        if (exposure >= threshold) {
            extinguished = true
            return true
        }
        return false
    }

    fun update(dt: Float) {
        val step = dt.coerceAtLeast(0f)
        pulse = (pulse - step / 0.22f).coerceAtLeast(0f)
        if (extinguished) return
        val recoveryTime = (step - recoveryDelay).coerceAtLeast(0f)
        recoveryDelay = (recoveryDelay - step).coerceAtLeast(0f)
        exposure = (exposure - recoveryTime * 0.045f).coerceAtLeast(0f)
    }

    fun reset() {
        exposure = 0f
        pulse = 0f
        extinguished = false
        recoveryDelay = 0f
    }
}
