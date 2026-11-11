package com.example.lungcapacity.pcda76db4.ui.game.scene

import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal class ProceduralMeshFactory {
    private val meshes = mutableListOf<MeshResource>()
    private val cache = mutableMapOf<String, MeshResource>()

    val sphere: MeshResource by lazy {
        MeshResource.createSphere(0.5f).global("shared-sphere")
    }
    val unitCylinder: MeshResource by lazy {
        cylinder("unit-cylinder", radius = 0.5f, height = 1f, segments = 64)
    }
    val seed: MeshResource by lazy {
        MeshResource.createSphere(0.5f).global("fruit-seed")
    }

    fun torus(
        majorRadius: Float,
        minorRadius: Float,
    ): MeshResource {
        val key = "torus-${majorRadius.formatKey()}-${minorRadius.formatKey()}"
        return cache.getOrPut(key) {
            val majorSegments = 128
            val tubeSegments = 6
            val data = MeshData()
            for (majorIndex in 0..majorSegments) {
                val u = majorIndex.toFloat() / majorSegments
                val majorAngle = u * TWO_PI
                for (tubeIndex in 0..tubeSegments) {
                    val v = tubeIndex.toFloat() / tubeSegments
                    val tubeAngle = v * TWO_PI
                    val radial = majorRadius + minorRadius * cos(tubeAngle)
                    data.positions += V3(
                        cos(majorAngle) * radial,
                        sin(majorAngle) * radial,
                        minorRadius * sin(tubeAngle),
                    )
                    data.normals += V3(
                        cos(majorAngle) * cos(tubeAngle),
                        sin(majorAngle) * cos(tubeAngle),
                        sin(tubeAngle),
                    )
                    data.uv += Vector2(u, v)
                }
            }
            addGridIndices(data, majorSegments, tubeSegments, reverse = false)
            create(key, data)
        }
    }

    fun woodenStand(): MeshResource = cache.getOrPut("wooden-stand") {
        lathe(
            name = "wooden-stand",
            profile = listOf(
                P2(0f, -0.08f),
                P2(0.48f, -0.08f),
                P2(0.56f, -0.04f),
                P2(0.60f, 0.02f),
                P2(0.61f, 0.115f),
                P2(0.77f, 0.19f),
                P2(1.29f, 0.20f),
                P2(1.34f, 0.23f),
                P2(1.34f, 0.295f),
                P2(1.31f, 0.32f),
                P2(0f, 0.32f),
            ),
            radialSegments = 128,
        )
    }

    fun scallopedBoard(): MeshResource = cache.getOrPut("scalloped-board") {
        cylinder(
            name = "scalloped-board",
            radius = 1.125f,
            height = 0.028f,
            segments = 320,
            radiusAtAngle = { angle -> 1.125f + 0.018f * cos(angle * 40f) },
        )
    }

    fun easyCreamBody(): MeshResource = cache.getOrPut("easy-cream-body") {
        val profile = mutableListOf(
            P2(0f, 0f),
            P2(0.965f, 0f),
            P2(0.988f, 0.011f),
            P2(1f, 0.025f),
        )
        repeat(241) { index ->
            val y = 0.03f + index / 240f * 0.51f
            profile += P2(1f + 0.0012f * sin(y / 0.025f * TWO_PI), y)
        }
        profile += listOf(
            P2(0.998f, 0.552f),
            P2(0.993f, 0.568f),
            P2(0.983f, 0.582f),
            P2(0.961f, 0.59f),
            P2(0f, 0.59f),
        )
        lathe("easy-cream-body", profile, radialSegments = 128)
    }

    fun easyTopIcing(): MeshResource = cache.getOrPut("easy-top-icing") {
        lathe(
            name = "easy-top-icing",
            profile = listOf(
                P2(0f, 0f),
                P2(0.963f, 0f),
                P2(0.982f, 0.008f),
                P2(0.985f, 0.02f),
                P2(0.97f, 0.026f),
                P2(0f, 0.026f),
            ),
            radialSegments = 128,
        )
    }

    fun tierBody(radius: Float, height: Float): MeshResource {
        val key = "tier-${radius.formatKey()}-${height.formatKey()}"
        return cache.getOrPut(key) {
            lathe(
                name = key,
                profile = listOf(
                    P2(0f, 0f),
                    P2(radius - 0.02f, 0f),
                    P2(radius - 0.006f, 0.006f),
                    P2(radius, 0.018f),
                    P2(radius, height - 0.02f),
                    P2(radius - 0.006f, height - 0.006f),
                    P2(radius - 0.02f, height),
                    P2(0f, height),
                ),
                radialSegments = 64,
            )
        }
    }

    fun strawberry(detailed: Boolean = true): MeshResource =
        cache.getOrPut("strawberry-$detailed") {
            val radialSegments = 48
            val verticalSegments = 36
            val data = MeshData()
            for (vertical in 0..verticalSegments) {
                val t = vertical.toFloat() / verticalSegments
                val baseRadius = strawberryRadius(t)
                for (radial in 0..radialSegments) {
                    val u = radial.toFloat() / radialSegments
                    val angle = u * TWO_PI
                    var radius = baseRadius
                    if (detailed && vertical in 1 until verticalSegments && baseRadius > 0.001f) {
                        val band = (t * 12f).roundToInt()
                        val bandT = band / 12f
                        val count = max(6, (strawberryRadius(bandT) / 0.014f).roundToInt())
                        val offset = if (band % 2 == 0) 0f else 0.5f
                        val fractional = u * count - offset - (u * count - offset).roundToInt()
                        val distance =
                            (fractional / 0.18f).pow(2) +
                                ((t - bandT) / 0.018f).pow(2)
                        val dent = 0.004f * kotlin.math.exp(-distance * 2f)
                        radius = baseRadius - dent + baseRadius * 0.025f * sin(u * 19f + t * 4f)
                    }
                    data.positions += V3(sin(angle) * radius, t * 0.32f, cos(angle) * radius)
                    val tangentY = strawberryRadiusDerivative(t)
                    data.normals += V3(sin(angle), -tangentY, cos(angle)).normalized()
                    data.uv += Vector2(u, t)
                }
            }
            addGridIndices(data, verticalSegments, radialSegments, reverse = true)
            create("strawberry-$detailed", data)
        }

    fun rosette(): MeshResource = cache.getOrPut("rosette") {
        val points = List(145) { index ->
            val t = index / 144f
            val angle = t * TWO_PI * 2.25f
            val radius = 0.108f * (1f - t).pow(0.67f)
            V3(radius * cos(angle), 0.032f + t * 0.19f, radius * sin(angle))
        }
        tube(
            name = "rosette",
            points = points,
            radialSegments = 24,
            radiusAt = { index ->
                val t = index.toFloat() / (points.size - 1)
                0.054f * (1f - t).pow(0.4f)
            },
            flutes = 6,
            fluteAmount = 0.17f,
        )
    }

    fun leaf(length: Float, width: Float, bend: Float): MeshResource {
        val key = "leaf-${length.formatKey()}-${width.formatKey()}-${bend.formatKey()}"
        return cache.getOrPut(key) {
            val data = MeshData()
            val segments = 18
            val across = 4
            for (segment in 0..segments) {
                val t = segment.toFloat() / segments
                val halfWidth =
                    width *
                        sin(PI.toFloat() * t).coerceAtLeast(0f).pow(0.7f) *
                        (1f + 0.12f * sin(t * 15f))
                for (column in 0..across) {
                    val x = column.toFloat() / across * 2f - 1f
                    data.positions += V3(
                        x * halfWidth,
                        t * length,
                        bend * t * t + 0.012f * abs(x) * sin(t * PI.toFloat()),
                    )
                    data.normals += V3(0f, -bend * 2f * t, 1f).normalized()
                    data.uv += Vector2(column.toFloat() / across, t)
                }
            }
            addGridIndices(data, segments, across, reverse = true)
            create(key, data)
        }
    }

    fun shellRing(radius: Float, compact: Boolean): MeshResource {
        val key = "shell-${radius.formatKey()}-$compact"
        return cache.getOrPut(key) {
            val count = (radius * if (compact) 22f else 54f).roundToInt()
            val merged = MeshData()
            repeat(count) { index ->
                val center = index.toFloat() / count * TWO_PI
                val arc = TWO_PI * radius / count * 0.97f
                val points = List(13) { step ->
                    val t = step / 12f
                    val angle = center + (t - 0.5f) * arc / radius
                    val localRadius = radius + 0.012f * sin(t * PI.toFloat())
                    V3(
                        sin(angle) * localRadius,
                        sin(t * PI.toFloat()) * arc * 0.2f,
                        cos(angle) * localRadius,
                    )
                }
                merged.append(
                    tubeData(
                        points = points,
                        radialSegments = 8,
                        radiusAt = { point ->
                            val t = point.toFloat() / (points.size - 1)
                            val taper = (1f - t).pow(0.5f) *
                                (0.45f + 0.55f * sin(PI.toFloat() * minOf(1f, t * 2f)))
                            arc * (if (compact) 0.28f else 0.24f) * taper
                        },
                        flutes = 8,
                        fluteAmount = 0.19f,
                    ),
                )
            }
            create(key, merged)
        }
    }

    fun vintageSwag(radius: Float): MeshResource {
        val key = "vintage-swag-${radius.formatKey()}"
        return cache.getOrPut(key) {
            val merged = MeshData()
            repeat(8) { segment ->
                val start = segment / 8f * TWO_PI
                val arc = TWO_PI / 8f
                repeat(2) { layer ->
                    val points = List(25) { step ->
                        val t = step / 24f
                        val angle = start + t * arc
                        V3(
                            sin(angle) * (radius + 0.007f),
                            -0.08f - 0.11f * sin(t * PI.toFloat()) - layer * 0.027f,
                            cos(angle) * (radius + 0.007f),
                        )
                    }
                    merged.append(
                        tubeData(points, 12, { 0.006f }, flutes = 6, fluteAmount = 0f),
                    )
                }
                val centerAngle = start
                val center = V3(
                    sin(centerAngle) * (radius + 0.017f),
                    -0.18f,
                    cos(centerAngle) * (radius + 0.017f),
                )
                repeat(3) { fan ->
                    val lateral = fan - 1f
                    val tangent = V3(cos(centerAngle), 0f, -sin(centerAngle))
                    val points = listOf(
                        center + tangent * (lateral * 0.025f) + V3(0f, 0.1f, 0f),
                        center + tangent * (lateral * 0.013f) + V3(0f, 0.05f, 0f),
                        center,
                    )
                    merged.append(
                        tubeData(
                            points,
                            16,
                            radiusAt = { point ->
                                val t = point.toFloat() / (points.size - 1)
                                0.014f * (1f - t).pow(0.5f) *
                                    (0.45f + 0.55f * sin(PI.toFloat() * minOf(1f, t * 2f)))
                            },
                            flutes = 8,
                            fluteAmount = 0.19f,
                        ),
                    )
                }
            }
            create(key, merged)
        }
    }

    fun candleBody(): MeshResource = cache.getOrPut("candle-body") {
        lathe(
            name = "candle-body",
            profile = listOf(
                P2(0f, 0f),
                P2(0.016f, 0f),
                P2(0.019f, 0.004f),
                P2(0.019f, 0.32f),
                P2(0.017f, 0.326f),
                P2(0.011f, 0.324f),
                P2(0f, 0.324f),
            ),
            radialSegments = 48,
        )
    }

    fun cherryStem(): MeshResource = cache.getOrPut("cherry-stem") {
        val points = List(11) { index ->
            val t = index / 10f
            val first = if (t <= 0.55f) {
                val local = t / 0.55f
                V3(0.018f * local, 0.06f + 0.07f * local, 0f)
            } else {
                val local = (t - 0.55f) / 0.45f
                V3(0.018f - 0.005f * local, 0.13f + 0.06f * local, 0.012f * local)
            }
            first
        }
        tube(
            name = "cherry-stem",
            points = points,
            radialSegments = 5,
            radiusAt = { 0.002f },
            flutes = 1,
            fluteAmount = 0f,
        )
    }

    fun textQuad(width: Float, depth: Float): MeshResource {
        val key = "text-quad-${width.formatKey()}-${depth.formatKey()}"
        return cache.getOrPut(key) {
            val halfWidth = width * 0.5f
            val halfDepth = depth * 0.5f
            val data = MeshData(
                positions = mutableListOf(
                    V3(-halfWidth, 0f, -halfDepth),
                    V3(halfWidth, 0f, -halfDepth),
                    V3(halfWidth, 0f, halfDepth),
                    V3(-halfWidth, 0f, halfDepth),
                ),
                normals = MutableList(4) { V3(0f, 1f, 0f) },
                uv = mutableListOf(
                    Vector2(0f, 0f),
                    Vector2(1f, 0f),
                    Vector2(1f, 1f),
                    Vector2(0f, 1f),
                ),
                indices = mutableListOf(0, 2, 1, 0, 3, 2),
            )
            create(key, data)
        }
    }

    fun close() {
        meshes.asReversed().forEach { runCatching { it.close() } }
        meshes.clear()
        cache.clear()
    }

    private fun cylinder(
        name: String,
        radius: Float,
        height: Float,
        segments: Int,
        radiusAtAngle: (Float) -> Float = { radius },
    ): MeshResource {
        val data = MeshData()
        for (segment in 0..segments) {
            val u = segment.toFloat() / segments
            val angle = u * TWO_PI
            val currentRadius = radiusAtAngle(angle)
            val normal = V3(sin(angle), 0f, cos(angle))
            data.positions += V3(
                sin(angle) * currentRadius,
                -height * 0.5f,
                cos(angle) * currentRadius,
            )
            data.positions += V3(
                sin(angle) * currentRadius,
                height * 0.5f,
                cos(angle) * currentRadius,
            )
            data.normals += normal
            data.normals += normal
            data.uv += Vector2(u, 0f)
            data.uv += Vector2(u, 1f)
        }
        repeat(segments) { segment ->
            val start = segment * 2
            data.indices += listOf(
                start,
                start + 1,
                start + 2,
                start + 1,
                start + 3,
                start + 2,
            )
        }
        addCap(data, height * 0.5f, 1f, segments, radiusAtAngle, reverse = false)
        addCap(data, -height * 0.5f, -1f, segments, radiusAtAngle, reverse = true)
        return create(name, data)
    }

    private fun addCap(
        data: MeshData,
        y: Float,
        normalY: Float,
        segments: Int,
        radiusAtAngle: (Float) -> Float,
        reverse: Boolean,
    ) {
        val center = data.positions.size
        data.positions += V3(0f, y, 0f)
        data.normals += V3(0f, normalY, 0f)
        data.uv += Vector2(0.5f, 0.5f)
        val ringStart = data.positions.size
        for (segment in 0..segments) {
            val angle = segment.toFloat() / segments * TWO_PI
            val radius = radiusAtAngle(angle)
            val x = sin(angle)
            val z = cos(angle)
            data.positions += V3(x * radius, y, z * radius)
            data.normals += V3(0f, normalY, 0f)
            data.uv += Vector2(0.5f + x * 0.5f, 0.5f + z * 0.5f)
        }
        repeat(segments) { segment ->
            if (reverse) {
                data.indices += listOf(center, ringStart + segment + 1, ringStart + segment)
            } else {
                data.indices += listOf(center, ringStart + segment, ringStart + segment + 1)
            }
        }
    }

    private fun lathe(
        name: String,
        profile: List<P2>,
        radialSegments: Int,
    ): MeshResource {
        val data = MeshData()
        profile.forEachIndexed { profileIndex, point ->
            val previous = profile[max(0, profileIndex - 1)]
            val next = profile[minOf(profile.lastIndex, profileIndex + 1)]
            val tangentRadius = next.radius - previous.radius
            val tangentY = next.y - previous.y
            for (segment in 0..radialSegments) {
                val u = segment.toFloat() / radialSegments
                val angle = u * TWO_PI
                data.positions += V3(
                    sin(angle) * point.radius,
                    point.y,
                    cos(angle) * point.radius,
                )
                data.normals += V3(
                    sin(angle) * tangentY,
                    -tangentRadius,
                    cos(angle) * tangentY,
                ).normalized()
                data.uv += Vector2(u, profileIndex.toFloat() / profile.lastIndex)
            }
        }
        addGridIndices(data, profile.lastIndex, radialSegments, reverse = true)
        return create(name, data)
    }

    private fun tube(
        name: String,
        points: List<V3>,
        radialSegments: Int,
        radiusAt: (Int) -> Float,
        flutes: Int,
        fluteAmount: Float,
    ): MeshResource = create(
        name,
        tubeData(points, radialSegments, radiusAt, flutes, fluteAmount),
    )

    private fun tubeData(
        points: List<V3>,
        radialSegments: Int,
        radiusAt: (Int) -> Float,
        flutes: Int,
        fluteAmount: Float,
    ): MeshData {
        val data = MeshData()
        points.forEachIndexed { index, point ->
            val tangent = when (index) {
                0 -> points[1] - point
                points.lastIndex -> point - points[index - 1]
                else -> points[index + 1] - points[index - 1]
            }.normalized()
            val reference = if (abs(tangent.y) < 0.96f) V3(0f, 1f, 0f) else V3(1f, 0f, 0f)
            val side = tangent.cross(reference).normalized()
            val up = side.cross(tangent).normalized()
            for (radial in 0..radialSegments) {
                val v = radial.toFloat() / radialSegments
                val angle = v * TWO_PI
                val fluteScale = 1f + fluteAmount * cos(angle * flutes)
                val radius = radiusAt(index) * fluteScale
                val normal = (side * cos(angle) + up * sin(angle)).normalized()
                data.positions += point + normal * radius
                data.normals += normal
                data.uv += Vector2(v, index.toFloat() / points.lastIndex)
            }
        }
        addGridIndices(data, points.lastIndex, radialSegments, reverse = false)
        return data
    }

    private fun create(name: String, data: MeshData): MeshResource {
        check(data.positions.size == data.normals.size)
        check(data.positions.size == data.uv.size)
        data.positions.forEachIndexed { index, value ->
            require(value.isFinite()) { "$name position[$index] is not finite: $value" }
        }
        data.normals.forEachIndexed { index, value ->
            require(value.isFinite()) { "$name normal[$index] is not finite: $value" }
        }
        val model = MeshModel(
            positions = data.positions.map(V3::toVector3),
            triangleIndices = data.indices,
            normals = data.normals.map(V3::toVector3),
            uv0 = data.uv,
        )
        MeshModel.validateOrThrow(model)
        val minX = data.positions.minOf { it.x }
        val minY = data.positions.minOf { it.y }
        val minZ = data.positions.minOf { it.z }
        val maxX = data.positions.maxOf { it.x }
        val maxY = data.positions.maxOf { it.y }
        val maxZ = data.positions.maxOf { it.z }
        return MeshResource.createWithMeshModel(
            model = model,
            bounds = BoundingBox(
                center = Vector3(
                    (minX + maxX) * 0.5f,
                    (minY + maxY) * 0.5f,
                    (minZ + maxZ) * 0.5f,
                ),
                halfExtent = Vector3(
                    (maxX - minX) * 0.5f,
                    (maxY - minY) * 0.5f,
                    (maxZ - minZ) * 0.5f,
                ),
            ),
            name = name,
        ).global(name)
    }

    private fun MeshResource.global(name: String): MeshResource = apply {
        toGlobal()
        meshes += this
    }

    private data class P2(val radius: Float, val y: Float)

    private data class V3(
        val x: Float,
        val y: Float,
        val z: Float,
    ) {
        operator fun plus(other: V3) = V3(x + other.x, y + other.y, z + other.z)
        operator fun minus(other: V3) = V3(x - other.x, y - other.y, z - other.z)
        operator fun times(scale: Float) = V3(x * scale, y * scale, z * scale)

        fun cross(other: V3) = V3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x,
        )

        fun normalized(): V3 {
            val length = sqrt(x * x + y * y + z * z)
            return if (length <= 0.000001f) V3(0f, 1f, 0f) else this * (1f / length)
        }

        fun isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

        fun toVector3() = Vector3(x, y, z)
    }

    private data class MeshData(
        val positions: MutableList<V3> = mutableListOf(),
        val normals: MutableList<V3> = mutableListOf(),
        val uv: MutableList<Vector2> = mutableListOf(),
        val indices: MutableList<Int> = mutableListOf(),
    ) {
        fun append(other: MeshData) {
            val offset = positions.size
            positions += other.positions
            normals += other.normals
            uv += other.uv
            indices += other.indices.map { it + offset }
        }
    }

    private companion object {
        const val TWO_PI = (PI * 2.0).toFloat()

        fun addGridIndices(
            data: MeshData,
            rows: Int,
            columns: Int,
            reverse: Boolean,
        ) {
            val stride = columns + 1
            repeat(rows) { row ->
                repeat(columns) { column ->
                    val start = row * stride + column
                    data.indices += if (reverse) {
                        listOf(
                            start,
                            start + 1,
                            start + stride,
                            start + 1,
                            start + stride + 1,
                            start + stride,
                        )
                    } else {
                        listOf(
                            start,
                            start + stride,
                            start + 1,
                            start + 1,
                            start + stride,
                            start + stride + 1,
                        )
                    }
                }
            }
        }

        fun strawberryRadius(t: Float): Float =
            0.17f * sin(PI.toFloat() * t).coerceAtLeast(0f).pow(0.57f) * (0.6f + 0.55f * t)

        fun strawberryRadiusDerivative(t: Float): Float {
            val delta = 0.001f
            val low = strawberryRadius((t - delta).coerceAtLeast(0f))
            val high = strawberryRadius((t + delta).coerceAtMost(1f))
            return (high - low) / (2f * delta)
        }

        fun Float.formatKey(): String = "%.4f".format(java.util.Locale.US, this)
    }
}
