package io.micronaut.data.jdbc.postgres.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.VectorShape
import io.micronaut.data.annotation.VectorStorage
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.SparseFloatVector
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PostgresJdbcSparseVectorEntitySpec extends Specification implements PostgresVectorTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    SparseVectorDocRepository repository = context.getBean(SparseVectorDocRepository)

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    void "test sparse vector persists through pgvector sparsevec column"() {
        given:
        repository.deleteAll()
        def sparse = new SparseFloatVector(5, [0, 3] as int[], [1.5f, -2f] as float[])

        when:
        def saved = repository.save(new SparseVectorDoc(embedding: sparse))
        def found = repository.findById(saved.id).orElse(null)

        then:
        found != null
        found.embedding.toFloatArray().toList() == [1.5f, 0f, 0f, -2f, 0f]
        found.embedding.indices().toList() == [0, 3]
        found.embedding.values().toList() == [1.5f, -2f]

        and:
        repository.findEmbeddingColumnType(saved.id) == "sparsevec"

        when:
        def updatedSparse = new SparseFloatVector(5, [2, 4] as int[], [3f, 4.5f] as float[])
        repository.updateEmbedding(saved.id, updatedSparse)
        def updated = repository.findById(saved.id).orElse(null)

        then:
        updated != null
        updated.embedding.toFloatArray().toList() == [0f, 0f, 3f, 0f, 4.5f]
        updated.embedding.indices().toList() == [2, 4]
        updated.embedding.values().toList() == [3f, 4.5f]
    }
}

@MappedEntity("postgres_sparse_vector_doc")
class SparseVectorDoc {
    @Id
    @GeneratedValue
    Long id

    @VectorStorage(length = 5, shape = VectorShape.SPARSE)
    SparseFloatVector embedding
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface SparseVectorDocRepository extends CrudRepository<SparseVectorDoc, Long> {

    @Query("SELECT pg_typeof(embedding)::text FROM postgres_sparse_vector_doc WHERE id = :id")
    String findEmbeddingColumnType(Long id)

    @Query("UPDATE postgres_sparse_vector_doc SET embedding = :embedding WHERE id = :id")
    void updateEmbedding(Long id, @Parameter("embedding") SparseFloatVector embedding)
}
