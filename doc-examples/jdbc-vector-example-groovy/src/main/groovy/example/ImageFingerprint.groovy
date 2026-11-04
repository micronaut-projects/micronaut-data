package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.VectorIndex
import io.micronaut.data.annotation.VectorIndexType
import io.micronaut.data.annotation.VectorStorage
import io.micronaut.data.model.vector.BinaryVector

@MappedEntity("image_fingerprint")
class ImageFingerprint {

    @Id
    @GeneratedValue
    Long id

    String name

    @VectorStorage(length = 16) // VECTOR(16, BINARY): 16 bits
    @VectorIndex(vectorIndexType = VectorIndexType.IVF, distanceType = VectorIndexType.DistanceType.HAMMING)
    BinaryVector hash
}
