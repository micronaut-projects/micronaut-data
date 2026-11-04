package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.BinaryVector
import io.micronaut.data.model.vector.search.ScoringFunction
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.repository.CrudRepository

@JdbcRepository(dialect = Dialect.ORACLE)
@Repository
interface ImageFingerprintRepository : CrudRepository<ImageFingerprint, Long> {

    // Binary vectors are compared with HAMMING distance by default
    fun searchTop3ByHashNear(hash: BinaryVector, maxDistance: Double): SearchResults<ImageFingerprint>

    // Pass ScoringFunction.JACCARD to compare them with Jaccard distance instead
    fun searchTop2ByHashNear(hash: BinaryVector, maxDistance: Double, function: ScoringFunction): SearchResults<ImageFingerprint>
}
