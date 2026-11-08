package io.micronaut.data.r2dbc.oraclexe.vector.binary

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.VectorStorage
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.BinaryVector
import io.micronaut.data.model.vector.search.ScoringFunction
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.r2dbc.oraclexe.OracleXETestPropertyProvider
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class OracleR2dbcBinaryVectorSpec extends Specification implements OracleXETestPropertyProvider {

    static final BinaryVector A = new BinaryVector([(byte) 0xFF, (byte) 0x00] as byte[])
    static final BinaryVector B = new BinaryVector([(byte) 0xFF, (byte) 0x01] as byte[])
    static final BinaryVector C = new BinaryVector([(byte) 0x00, (byte) 0xFF] as byte[])

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties + ["spec.name": "OracleR2dbcBinaryVectorSpec"])

    @Shared
    R2dbcBinaryDocRepository repository = context.getBean(R2dbcBinaryDocRepository)

    void setup() {
        repository.deleteAll()
        [A, B, C].each { repository.save(new R2dbcBinaryDoc(embedding: it)) }
    }

    void "saves and reads binary vectors"() {
        expect:
        repository.findAll()*.embedding as Set == [A, B, C] as Set
    }

    void "binds a binary vector in a custom query"() {
        given:
        BinaryVector vector = new BinaryVector([(byte) 0xF0, (byte) 0x0F] as byte[])

        when:
        repository.saveCustom(vector)

        then:
        repository.findAll()*.embedding.contains(vector)
    }

    void "derived search compares binary vectors with HAMMING by default"() {
        when:
        SearchResults<R2dbcBinaryDoc> results = repository.searchTop2ByEmbeddingNear(A, 20d)

        then:
        results.results()*.entity()*.embedding == [A, B]
        results.results()*.score()*.value() == [0d, 1d]
    }

    void "derived search uses JACCARD when asked"() {
        when:
        SearchResults<R2dbcBinaryDoc> results = repository.searchTop2ByEmbeddingNear(A, 1d, ScoringFunction.JACCARD)

        then:
        results.results()*.entity()*.embedding == [A, B]
        Math.abs(results.results()[1].score().value() - 1d / 9d) < 1e-6d
    }
}

@MappedEntity("r2dbc_binary_doc")
class R2dbcBinaryDoc {
    @Id
    @GeneratedValue
    Long id

    @VectorStorage(length = 16)
    BinaryVector embedding
}

@Requires(property = "spec.name", value = "OracleR2dbcBinaryVectorSpec")
@R2dbcRepository(dialect = Dialect.ORACLE)
interface R2dbcBinaryDocRepository extends CrudRepository<R2dbcBinaryDoc, Long> {

    @Query("INSERT INTO r2dbc_binary_doc(id, embedding) VALUES (r2dbc_binary_doc_seq.nextval, :vec)")
    void saveCustom(BinaryVector vec)

    SearchResults<R2dbcBinaryDoc> searchTop2ByEmbeddingNear(BinaryVector vector, Double maxDistance)

    SearchResults<R2dbcBinaryDoc> searchTop2ByEmbeddingNear(BinaryVector vector, Double maxDistance, ScoringFunction function)
}
