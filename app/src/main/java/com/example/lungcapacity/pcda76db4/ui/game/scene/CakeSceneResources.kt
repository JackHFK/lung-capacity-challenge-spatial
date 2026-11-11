package com.example.lungcapacity.pcda76db4.ui.game.scene

import android.os.Handler
import android.os.Looper
import com.pico.spatial.core.ecs.resource.MeshInstancesResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicIntegerArray

internal class CakeSceneResources private constructor(
    shared: SharedCakeResources,
) {
    val meshes = shared.meshes
    val inscription = shared.inscription
    val flameMeshes = shared.flameMeshes
    val materials = CakeMaterials(shared.textures)
    val physicsMaterial = PhysicsMaterialResource()

    private val instances = mutableListOf<MeshInstancesResource>()
    private val shapes = mutableListOf<ShapeResource>()

    fun instances(
        name: String,
        values: List<MeshInstancesResource.Instance>,
    ): MeshInstancesResource =
        MeshInstancesResource.create(name, values).apply {
            toGlobal()
            instances += this
        }

    fun sphereShape(radius: Float): ShapeResource =
        ShapeResource.createSphere(radius).apply {
            toGlobal()
            shapes += this
        }

    fun close() {
        instances.asReversed().forEach { runCatching { it.close() } }
        instances.clear()
        shapes.asReversed().forEach { runCatching { it.close() } }
        shapes.clear()
        runCatching { physicsMaterial.close() }
        materials.close()
    }

    companion object {
        suspend fun create(
            awaitLooperTurn: suspend () -> Unit,
            onProgress: (Float) -> Unit = {},
        ): CakeSceneResources {
            val shared = SharedCakeResources.acquire(awaitLooperTurn, onProgress)
            awaitLooperTurn()
            onProgress(1f)
            return CakeSceneResources(shared)
        }
    }
}

internal data class CakeTextureResources(
    val cream: TextureSet,
    val fruit: TextureSet,
    val wood: TextureSet,
    val candle: com.pico.spatial.core.ecs.resource.TextureResource,
) {
    fun close() {
        cream.close()
        fruit.close()
        wood.close()
        candle.close()
    }
}

private class SharedCakeResources private constructor(
    val textures: CakeTextureResources,
    val inscription: InscriptionMeshes,
    val flameMeshes: FlameMeshResources,
) {
    val meshes = ProceduralMeshFactory()

    companion object {
        private val mutex = Mutex()
        private var instance: SharedCakeResources? = null

        suspend fun acquire(
            awaitLooperTurn: suspend () -> Unit,
            onProgress: (Float) -> Unit,
        ): SharedCakeResources {
            instance?.let {
                onProgress(1f)
                return it
            }
            val shared = mutex.withLock {
                instance ?: create(awaitLooperTurn, onProgress).also { instance = it }
            }
            onProgress(1f)
            return shared
        }

        private suspend fun create(
            awaitLooperTurn: suspend () -> Unit,
            onProgress: (Float) -> Unit,
        ): SharedCakeResources {
            val mainHandler = Handler(Looper.getMainLooper())
            val taskProgress = AtomicIntegerArray(PREPARATION_WEIGHTS.size)
            fun reportTaskProgress(task: Int, progress: Float) {
                taskProgress.set(task, (progress.coerceIn(0f, 1f) * 1_000f).toInt())
                val weightedProgress = PREPARATION_WEIGHTS.indices.sumOf { index ->
                    PREPARATION_WEIGHTS[index].toDouble() *
                        taskProgress.get(index).toDouble() /
                        1_000.0
                }.toFloat()
                mainHandler.post {
                    onProgress(weightedProgress * PREPARATION_PROGRESS_SHARE)
                }
            }
            val (preparedTextures, preparedInscription) = withContext(Dispatchers.Default) {
                coroutineScope {
                    val cream = async {
                        ProceduralTextures.surfaceBitmaps(SurfaceKind.CREAM) {
                            reportTaskProgress(PREPARATION_CREAM, it)
                        }
                    }
                    val fruit = async {
                        ProceduralTextures.surfaceBitmaps(SurfaceKind.FRUIT) {
                            reportTaskProgress(PREPARATION_FRUIT, it)
                        }
                    }
                    val wood = async {
                        ProceduralTextures.surfaceBitmaps(SurfaceKind.WOOD) {
                            reportTaskProgress(PREPARATION_WOOD, it)
                        }
                    }
                    val candle = async {
                        ProceduralTextures.candleBitmap {
                            reportTaskProgress(PREPARATION_CANDLE, it)
                        }
                    }
                    val inscription = async {
                        PreparedInscriptionGeometry.prepare().also {
                            reportTaskProgress(PREPARATION_INSCRIPTION, 1f)
                        }
                    }
                    PreparedCakeTextures(
                        cream = cream.await(),
                        fruit = fruit.await(),
                        wood = wood.await(),
                        candle = candle.await(),
                    ) to inscription.await()
                }
            }
            onProgress(PREPARATION_PROGRESS_SHARE)
            awaitLooperTurn()
            var cream: TextureSet? = null
            var fruit: TextureSet? = null
            var wood: TextureSet? = null
            var candle: com.pico.spatial.core.ecs.resource.TextureResource? = null
            var inscription: InscriptionMeshes? = null
            var flameMeshes: FlameMeshResources? = null
            return try {
                cream = preparedTextures.cream.toTextureSet()
                onProgress(0.74f)
                awaitLooperTurn()
                fruit = preparedTextures.fruit.toTextureSet()
                onProgress(0.80f)
                awaitLooperTurn()
                wood = preparedTextures.wood.toTextureSet()
                onProgress(0.86f)
                awaitLooperTurn()
                candle = ProceduralTextures.texture(preparedTextures.candle)
                onProgress(0.90f)
                awaitLooperTurn()
                inscription = InscriptionMeshes.create(preparedInscription)
                onProgress(0.95f)
                awaitLooperTurn()
                flameMeshes = FlameMeshResources.create()
                onProgress(1f)
                SharedCakeResources(
                    textures = CakeTextureResources(
                        cream = cream,
                        fruit = fruit,
                        wood = wood,
                        candle = candle,
                    ),
                    inscription = inscription,
                    flameMeshes = flameMeshes,
                )
            } catch (error: Throwable) {
                runCatching { flameMeshes?.close() }
                runCatching { inscription?.close() }
                runCatching { candle?.close() }
                runCatching { wood?.close() }
                runCatching { fruit?.close() }
                runCatching { cream?.close() }
                throw error
            } finally {
                preparedTextures.close()
            }
        }

        private fun SurfaceBitmaps.toTextureSet(): TextureSet =
            TextureSet(
                baseColor = ProceduralTextures.texture(baseColor),
                roughness = ProceduralTextures.texture(roughness),
                normal = ProceduralTextures.texture(normal),
            )

        private const val PREPARATION_CREAM = 0
        private const val PREPARATION_FRUIT = 1
        private const val PREPARATION_WOOD = 2
        private const val PREPARATION_CANDLE = 3
        private const val PREPARATION_INSCRIPTION = 4
        private const val PREPARATION_PROGRESS_SHARE = 0.68f
        private val PREPARATION_WEIGHTS = floatArrayOf(0.31f, 0.31f, 0.31f, 0.04f, 0.03f)
    }
}
