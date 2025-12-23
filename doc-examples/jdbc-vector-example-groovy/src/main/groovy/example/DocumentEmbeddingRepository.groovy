package example

import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.repository.CrudRepository

@JdbcRepository(dialect = Dialect.ORACLE)
@Repository
interface DocumentEmbeddingRepository extends CrudRepository<DocumentEmbedding, Long> {

    // Example showing vector as a parameter
    @Query("INSERT INTO document_embedding(id, embedding) VALUES (:id, :vec)")
    void insertEmbedding(Long id, Vector vec)
}
