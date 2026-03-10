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
interface DocumentEmbeddingRepository extends CrudRepository<DocumentEmbedding, Long> {

    // Example showing vector as a parameter
    DocumentEmbedding save(Vector embedding)

    List<DocumentEmbedding> findTop2ByEmbeddingNear(Vector vec, Double maxDistance)

    SearchResults<DocumentEmbedding> searchByEmbeddingNear(Vector vector,
                                                           Score maxDistance,
                                                           ScoringFunction function)
}
