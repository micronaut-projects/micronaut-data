package io.micronaut.data.jdbc.oraclexe.binaryvector

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.VectorIndex
import io.micronaut.data.annotation.VectorIndexType
import io.micronaut.data.annotation.VectorStorage
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.BinaryVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.vector.search.ScoringFunction
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class OracleJdbcBinaryVectorEntitySpec extends Specification implements OracleTestPropertyProvider {

    static final BinaryVector A = new BinaryVector([(byte) 0xFF, (byte) 0x00] as byte[])
    // one bit away from A
    static final BinaryVector B = new BinaryVector([(byte) 0xFF, (byte) 0x01] as byte[])
    // every bit differs from A
    static final BinaryVector C = new BinaryVector([(byte) 0x00, (byte) 0xFF] as byte[])

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    BinaryDocRepository repository = context.getBean(BinaryDocRepository)

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    void setup() {
        repository.deleteAll()
        [A, B, C].each { repository.save(new BinaryDoc(embedding: it)) }
    }

    void "saves and reads binary vectors"() {
        when:
        List<BinaryVector> stored = repository.findAll()*.embedding

        then:
        stored as Set == [A, B, C] as Set
        stored.every { it.dimensions() == 16 }
    }

    void "binds a binary vector in a custom query"() {
        when:
        repository.saveCustom(BinaryVector.of(true, true, true, true, false, false, false, false,
                false, false, false, false, true, true, true, true))

        then:
        repository.findAll()*.embedding.contains(new BinaryVector([(byte) 0xF0, (byte) 0x0F] as byte[]))
    }

    void "derived search compares binary vectors with HAMMING by default"() {
        when:
        SearchResults<BinaryDoc> results = repository.searchTop2ByEmbeddingNear(A, 20d)

        then:
        results.results()*.entity()*.embedding == [A, B]
        results.results()*.score()*.value() == [0d, 1d]
        results.results()*.similarity()*.value() == [0d, 1d]
    }

    void "derived search filters by HAMMING distance"() {
        expect:
        repository.searchTop2ByEmbeddingNear(A, 0.5d).results()*.entity()*.embedding == [A]
    }

    void "derived search uses JACCARD when asked"() {
        when:
        SearchResults<BinaryDoc> results = repository.searchTop2ByEmbeddingNear(A, 1d, ScoringFunction.JACCARD)

        then: "B sets 9 bits and shares 8 of them with A"
        results.results()*.entity()*.embedding == [A, B]
        results.results()[0].score().value() == 0d
        Math.abs(results.results()[1].score().value() - 1d / 9d) < 1e-6d
        Math.abs(results.results()[1].similarity().value() - 8d / 9d) < 1e-6d
    }

    void "reads a binary column mapped to a Vector property as a BinaryVector"() {
        given:
        GenericBinaryDocRepository genericRepository = context.getBean(GenericBinaryDocRepository)

        when:
        GenericBinaryDoc saved = genericRepository.save(new GenericBinaryDoc(embedding: A))
        Vector read = genericRepository.findById(saved.id).orElseThrow().embedding

        then:
        read instanceof BinaryVector
        read == A
    }
}

@MappedEntity("vector_binary_doc")
class BinaryDoc {
    @Id
    @GeneratedValue
    Long id

    @VectorStorage(length = 16)
    @VectorIndex(vectorIndexType = VectorIndexType.IVF, distanceType = VectorIndexType.DistanceType.HAMMING)
    BinaryVector embedding
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface BinaryDocRepository extends CrudRepository<BinaryDoc, Long> {

    @Query("INSERT INTO vector_binary_doc(id, embedding) VALUES (vector_binary_doc_seq.nextval, :vec)")
    void saveCustom(BinaryVector vec)

    SearchResults<BinaryDoc> searchTop2ByEmbeddingNear(BinaryVector vector, Double maxDistance)

    SearchResults<BinaryDoc> searchTop2ByEmbeddingNear(BinaryVector vector, Double maxDistance, ScoringFunction function)
}

@MappedEntity("vector_binary_generic_doc")
class GenericBinaryDoc {
    @Id
    @GeneratedValue
    Long id

    @MappedProperty(definition = "VECTOR(16, BINARY)")
    Vector embedding
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface GenericBinaryDocRepository extends CrudRepository<GenericBinaryDoc, Long> {
}
