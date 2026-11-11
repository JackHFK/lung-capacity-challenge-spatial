package com.example.lungcapacity.pcda76db4.ui.game.scene

import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.resource.MeshIndexSizeType
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.MeshVertexAttribute
import com.pico.spatial.core.ecs.resource.MeshVertexAttributeType
import com.pico.spatial.core.ecs.resource.PrimitiveType
import com.pico.spatial.core.ecs.resource.RawMesh
import com.pico.spatial.core.ecs.resource.RawMeshDescriptorBuilder
import com.pico.spatial.core.ecs.resource.RawMeshStreamAttribute
import com.pico.spatial.core.ecs.resource.RawMeshStreamLayout
import com.pico.spatial.core.math.Vector3
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

internal data class PreparedInscriptionGeometry(
    val happy: PreparedTextMesh,
    val birthday: PreparedTextMesh,
) {
    companion object {
        fun prepare(): PreparedInscriptionGeometry =
            PreparedInscriptionGeometry(
                happy = PreparedTextMesh.decode(GeneratedInscriptionData.happy),
                birthday = PreparedTextMesh.decode(GeneratedInscriptionData.birthday),
            )
    }
}

internal data class PreparedTextMesh(
    val vertices: FloatArray,
    val indices: ShortArray,
    val bounds: BoundingBox,
) {
    val vertexCount: Int
        get() = vertices.size / FLOATS_PER_VERTEX

    companion object {
        private const val MAGIC = "CTG3"
        private const val FLOATS_PER_VERTEX = 6

        fun decode(chunks: Array<String>): PreparedTextMesh {
            val compressed = Base64.getDecoder().decode(chunks.joinToString(""))
            val bytes = InflaterInputStream(
                ByteArrayInputStream(compressed),
                Inflater(true),
            ).use { it.readBytes() }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4).also(buffer::get).toString(Charsets.US_ASCII)
            require(magic == MAGIC) { "Unexpected inscription geometry format: $magic" }

            val vertexCount = buffer.int
            val indexCount = buffer.int
            val vertices = FloatArray(vertexCount * FLOATS_PER_VERTEX)
            repeat(vertices.size) { vertices[it] = buffer.float }
            val indices = ShortArray(indexCount)
            repeat(indices.size) { indices[it] = buffer.short }
            require(!buffer.hasRemaining()) { "Trailing inscription geometry bytes" }

            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var minZ = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            var maxZ = Float.NEGATIVE_INFINITY
            repeat(vertexCount) { index ->
                val offset = index * FLOATS_PER_VERTEX
                val x = vertices[offset]
                val y = vertices[offset + 1]
                val z = vertices[offset + 2]
                require(x.isFinite() && y.isFinite() && z.isFinite())
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                minZ = minOf(minZ, z)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
                maxZ = maxOf(maxZ, z)
            }
            return PreparedTextMesh(
                vertices = vertices,
                indices = indices,
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
            )
        }
    }
}

internal class InscriptionMeshes private constructor(
    val happy: MeshResource,
    val birthday: MeshResource,
) {
    fun close() {
        birthday.close()
        happy.close()
    }

    companion object {
        fun create(prepared: PreparedInscriptionGeometry): InscriptionMeshes =
            InscriptionMeshes(
                happy = createMesh("inscription-happy", prepared.happy),
                birthday = createMesh("inscription-birthday", prepared.birthday),
            )

        private fun createMesh(
            name: String,
            prepared: PreparedTextMesh,
        ): MeshResource {
            val descriptor = RawMeshDescriptorBuilder().use { builder ->
                builder
                    .streamLayout(
                        RawMeshStreamLayout(
                            bufferSlot = 0,
                            stride = VERTEX_STRIDE_BYTES,
                        ),
                    )
                    .streamAttribute(
                        RawMeshStreamAttribute(
                            MeshVertexAttribute.POSITION,
                            MeshVertexAttributeType.FLOAT3,
                        ),
                    )
                    .streamAttribute(
                        RawMeshStreamAttribute(
                            MeshVertexAttribute.NORMAL,
                            MeshVertexAttributeType.FLOAT3,
                            recordByteOffset = 12,
                        ),
                    )
                    .vertexCount(prepared.vertexCount)
                    .indexCount(prepared.indices.size)
                    .indexType(MeshIndexSizeType.UNSIGNED_SHORT)
                    .boundingBox(prepared.bounds)
                    .addSubMesh(
                        0,
                        prepared.indices.size,
                        0,
                        name,
                        PrimitiveType.TRIANGLES,
                    )
                    .build()
            }
            return descriptor.use { value ->
                RawMesh.create(value).use { raw ->
                    check(
                        raw.populateVertexBuffer(0) { buffer ->
                            buffer.asFloatBuffer().put(prepared.vertices)
                            true
                        },
                    )
                    check(
                        raw.populateIndicesBuffer { buffer ->
                            buffer.asShortBuffer().put(prepared.indices)
                            true
                        },
                    )
                    MeshResource.createWithRawMesh(raw, name).apply { toGlobal() }
                }
            }
        }

        private const val VERTEX_STRIDE_BYTES = 24
    }
}
