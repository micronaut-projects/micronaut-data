package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.VectorIndex
import io.micronaut.data.annotation.VectorIndexType
import io.micronaut.data.annotation.VectorStorage
import io.micronaut.data.model.vector.BinaryVector

@MappedEntity("image_fingerprint")
data class ImageFingerprint(
    @field:Id
    @field:GeneratedValue
    val id: Long? = null,

    val name: String,

    @field:VectorStorage(length = 16) // VECTOR(16, BINARY): 16 bits
    @field:VectorIndex(vectorIndexType = VectorIndexType.IVF, distanceType = VectorIndexType.DistanceType.HAMMING)
    val hash: BinaryVector
)
