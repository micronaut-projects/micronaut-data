package io.micronaut.data.jdbc.postgres.vector.binary

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.VectorIndex
import io.micronaut.data.annotation.VectorIndexType
import io.micronaut.data.annotation.VectorStorage
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.postgres.vector.PostgresVectorTestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.BinaryVector
import io.micronaut.data.model.vector.search.ScoringFunction
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * pgvector stores binary vectors in bit(n) columns and compares them with Hamming (<~>) or Jaccard (<%>) distance.
 */
class PostgresJdbcBinaryVectorEntitySpec extends Specification implements PostgresVectorTestPropertyProvider {

    static final BinaryVector A = new BinaryVector([(byte) 0xFF, (byte) 0x00] as byte[])
    // one bit away from A
    static final BinaryVector B = new BinaryVector([(byte) 0xFF, (byte) 0x01] as byte[])
    // every bit differs from A
    static final BinaryVector C = new BinaryVector([(byte) 0x00, (byte) 0xFF] as byte[])

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    PgBinaryDocRepository repository = context.getBean(PgBinaryDocRepository)

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    void setup() {
        repository.deleteAll()
        [A, B, C].each { repository.save(new PgBinaryDoc(embedding: it)) }
    }

    void "saves and reads binary vectors in a bit(16) column"() {
        when:
        List<BinaryVector> stored = repository.findAll()*.embedding

        then:
        stored as Set == [A, B, C] as Set
        stored.every { it.dimensions() == 16 }
        repository.findEmbeddingColumnType() == "bit"
    }

    void "binds a binary vector in a custom query"() {
        when:
        repository.saveCustom(new BinaryVector([(byte) 0xF0, (byte) 0x0F] as byte[]))

        then:
        repository.findAll()*.embedding.contains(new BinaryVector([(byte) 0xF0, (byte) 0x0F] as byte[]))
    }

    void "derived search compares binary vectors with Hamming distance by default"() {
        when:
        SearchResults<PgBinaryDoc> results = repository.searchTop2ByEmbeddingNear(A, 20d)

        then:
        results.results()*.entity()*.embedding == [A, B]
        results.results()*.score()*.value() == [0d, 1d]
    }

    void "derived search filters by Hamming distance"() {
        expect:
        repository.searchTop2ByEmbeddingNear(A, 0.5d).results()*.entity()*.embedding == [A]
    }

    void "derived search uses Jaccard distance when asked"() {
        when:
        SearchResults<PgBinaryDoc> results = repository.searchTop2ByEmbeddingNear(A, 1d, ScoringFunction.JACCARD)

        then: "B sets 9 bits and shares 8 of them with A"
        results.results()*.entity()*.embedding == [A, B]
        results.results()[0].score().value() == 0d
        Math.abs(results.results()[1].score().value() - 1d / 9d) < 1e-6d
        Math.abs(results.results()[1].similarity().value() - 8d / 9d) < 1e-6d
    }

    void "a nullable binary vector property round-trips SQL NULL"() {
        given:
        PgNullableBinaryDocRepository nullableRepository = context.getBean(PgNullableBinaryDocRepository)

        when:
        PgNullableBinaryDoc empty = nullableRepository.save(new PgNullableBinaryDoc(name: "empty"))
        PgNullableBinaryDoc filled = nullableRepository.save(new PgNullableBinaryDoc(name: "filled", embedding: A))

        then:
        nullableRepository.findById(empty.id).orElseThrow().embedding == null
        nullableRepository.findById(filled.id).orElseThrow().embedding == A
    }
}

@MappedEntity("pg_binary_doc")
class PgBinaryDoc {
    @Id
    @GeneratedValue
    Long id

    @VectorStorage(length = 16)
    @VectorIndex(vectorIndexType = VectorIndexType.HNSW)
    BinaryVector embedding
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PgBinaryDocRepository extends CrudRepository<PgBinaryDoc, Long> {

    @Query("INSERT INTO pg_binary_doc(embedding) VALUES (:vec)")
    void saveCustom(BinaryVector vec)

    @Query(value = "SELECT data_type FROM information_schema.columns WHERE table_name = 'pg_binary_doc' AND column_name = 'embedding'", nativeQuery = true)
    String findEmbeddingColumnType()

    SearchResults<PgBinaryDoc> searchTop2ByEmbeddingNear(BinaryVector vector, Double maxDistance)

    SearchResults<PgBinaryDoc> searchTop2ByEmbeddingNear(BinaryVector vector, Double maxDistance, ScoringFunction function)
}

@MappedEntity("pg_binary_nullable_doc")
class PgNullableBinaryDoc {
    @Id
    @GeneratedValue
    Long id

    String name

    @Nullable
    @VectorStorage(length = 16)
    BinaryVector embedding
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface PgNullableBinaryDocRepository extends CrudRepository<PgNullableBinaryDoc, Long> {
}
