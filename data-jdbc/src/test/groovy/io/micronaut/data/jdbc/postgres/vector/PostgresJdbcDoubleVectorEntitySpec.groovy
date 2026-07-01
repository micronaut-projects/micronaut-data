package io.micronaut.data.jdbc.postgres.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.repository.PageableRepository
import jakarta.persistence.Column
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PostgresJdbcDoubleVectorEntitySpec extends Specification implements PostgresVectorTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    VectorDoubleDocRepository vectorRepository = context.getBean(VectorDoubleDocRepository)

    @Override
    List<String> packages() {
        // Ensure entity/repository in this package are scanned
        return [getClass().package.name]
    }

    void "custom write queries accept DoubleVector on Postgres"() {
        given:
        double[] dv = [1d, 2.5d, -3.75d] as double[]
        DoubleVector v1 = Vector.of(dv)

        when:
        vectorRepository.saveCustom(v1)
        vectorRepository.updateCustom(1L, v1)

        then:
        noExceptionThrown()
    }

    void "default repository write operations accept DoubleVector on Postgres"() {
        given:
        DoubleVector v1 = Vector.of([2d, -1.5d, 0.25d] as double[])

        when:
        def saved = vectorRepository.save(new VectorDoubleDoc(embedding: v1))
        vectorRepository.update(new VectorDoubleDoc(id: saved.id, embedding: v1))

        then:
        noExceptionThrown()
    }

    void "reading DoubleVector entities fails on Postgres"() {
        given:
        vectorRepository.saveCustom(Vector.of([1d, 2d, 3d] as double[]))

        when:
        vectorRepository.findAll()

        then:
        def findAllEx = thrown(DataAccessException)
        assertPostgresDoubleVectorUnsupported(findAllEx)

        when:
        vectorRepository.findById(1L)

        then:
        def findByIdEx = thrown(IllegalArgumentException)
        assertPostgresDoubleVectorUnsupported(findByIdEx)
    }

    void "paging DoubleVector entities fails on Postgres"() {
        given:
        vectorRepository.saveCustom(Vector.of([1d, 2d, 3d] as double[]))

        when:
        vectorRepository.findAll(io.micronaut.data.model.Pageable.from(0, 1))

        then:
        def pagingEx = thrown(DataAccessException)
        assertPostgresDoubleVectorUnsupported(pagingEx)
    }

    void "derived vector near/within/between with DoubleVector execute on empty dataset"() {
        given:
        vectorRepository.deleteAll()
        Vector queryVector = Vector.of([1d, 0d, 0d] as double[])

        when:
        def nearResults = vectorRepository.searchByEmbeddingNear(queryVector, 100d)

        then:
        nearResults != null
        nearResults.results().isEmpty()

        when:
        def withinResults = vectorRepository.searchByEmbeddingWithin(queryVector, 0d, 100d)

        then:
        withinResults != null
        withinResults.results().isEmpty()

        when:
        def betweenResults = vectorRepository.searchByEmbeddingBetween(queryVector, 0d, 100d)

        then:
        betweenResults != null
        betweenResults.results().isEmpty()
    }

    private static void assertPostgresDoubleVectorUnsupported(Throwable throwable) {
        assert throwable.message != null
        assert throwable.message.contains("POSTGRES does not support")
        assert throwable.message.contains("DoubleVector")
    }
}

@MappedEntity("vector_double_doc")
class VectorDoubleDoc {
    @Id
    @GeneratedValue
    Long id
    @Column(length = 3)
    DoubleVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    DoubleVector getEmbedding() { return embedding }
    void setEmbedding(DoubleVector embedding) { this.embedding = embedding }
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface VectorDoubleDocRepository extends PageableRepository<VectorDoubleDoc, Long> {

    @Query("INSERT INTO vector_double_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_double_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") DoubleVector vec)

    @Query("SELECT * FROM vector_double_doc WHERE id = :id")
    Optional<VectorDoubleDoc> findById(Long id)

    @Query("UPDATE vector_double_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_double_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") DoubleVector vec)

    @Query("INSERT INTO vector_double_doc(embedding) VALUES (:vec)")
    java.util.concurrent.Future<Integer> saveAsync(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_double_doc(embedding) VALUES (:vec)")
    java.util.concurrent.Future<Integer> saveAsync(@Parameter("vec") DoubleVector vec)

    @Query("SELECT * FROM vector_double_doc WHERE id = :id")
    java.util.concurrent.Future<java.util.List<VectorDoubleDoc>> findAsync(Long id)

    @Query("UPDATE vector_double_doc SET embedding = :vec WHERE id = :id")
    java.util.concurrent.Future<Integer> updateAsync(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_double_doc SET embedding = :vec WHERE id = :id")
    java.util.concurrent.Future<Integer> updateAsync(Long id, @Parameter("vec") DoubleVector vec)

    SearchResults<VectorDoubleDoc> searchByEmbeddingNear(Vector vector, Double maxDistance)

    SearchResults<VectorDoubleDoc> searchByEmbeddingWithin(Vector vector, Double minDistance, Double maxDistance)

    SearchResults<VectorDoubleDoc> searchByEmbeddingBetween(Vector vector, Double minDistance, Double maxDistance)

}
