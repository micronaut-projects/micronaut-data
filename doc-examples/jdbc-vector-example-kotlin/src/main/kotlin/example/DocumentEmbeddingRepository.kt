package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.vector.search.Score
import io.micronaut.data.model.vector.search.ScoringFunction
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.repository.CrudRepository

@JdbcRepository(dialect = Dialect.ORACLE)
@Repository
interface DocumentEmbeddingRepository : CrudRepository<DocumentEmbedding, Long> {

    // Example showing vector as a parameter
    fun save(embedding: Vector): DocumentEmbedding

    fun findTop2ByEmbeddingNear(vec: Vector, maxDistance: Double): List<DocumentEmbedding>

    fun searchByEmbeddingNear(vector: Vector,
                              maxDistance: Score,
                              function: ScoringFunction): SearchResults<DocumentEmbedding>
}
