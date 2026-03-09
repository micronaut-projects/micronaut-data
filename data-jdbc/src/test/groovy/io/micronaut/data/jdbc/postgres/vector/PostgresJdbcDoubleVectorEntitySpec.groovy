package io.micronaut.data.jdbc.postgres.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.repository.PageableRepository
import jakarta.persistence.Column
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PostgresJdbcDoubleVectorEntitySpec extends Specification implements PostgresTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    VectorDoubleDocRepository vectorRepository = context.getBean(VectorDoubleDocRepository)

    @Shared
    javax.sql.DataSource dataSource = context.getBean(javax.sql.DataSource)

    @Override
    List<String> packages() {
        // Ensure entity/repository in this package are scanned
        return [getClass().package.name]
    }

    void "custom queries with DoubleVector are not supported on Postgres"() {
        given:
        double[] dv = [1d, 2.5d, -3.75d] as double[]
        DoubleVector v1 = Vector.of(dv)

        when: "save via custom @Query using Vector parameter"
        vectorRepository.saveCustom(v1)

        then:
        def ex1 = thrown(IllegalArgumentException)
        ex1.message.contains("POSTGRES does not support")

        when: "update via custom @Query with DoubleVector"
        vectorRepository.updateCustom(1L, v1)

        then:
        def ex2 = thrown(IllegalArgumentException)
        ex2.message.contains("POSTGRES does not support")
    }

    void "default repository methods with DoubleVector are not supported on Postgres"() {
        given:
        double[] dv = [2d, -1.5d, 0.25d] as double[]
        DoubleVector v1 = Vector.of(dv)

        when: "persist entity using default repository save"
        vectorRepository.save(new VectorDoubleDoc(embedding: v1))

        then:
        def ex1 = thrown(DataAccessException)
        assert ex1.cause instanceof IllegalArgumentException
        ex1.cause.message.contains("POSTGRES does not support")

        when: "update entity using default repository update"
        vectorRepository.update(new VectorDoubleDoc(id: 1L, embedding: v1))

        then:
        def ex2 = thrown(DataAccessException)
        assert ex2.cause instanceof IllegalArgumentException
        ex2.cause.message.contains("POSTGRES does not support")
    }


    void "multiple DoubleVector operations are not supported on Postgres"() {
        given:
        DoubleVector vA = Vector.of([1d, 2d, 3d] as double[])
        DoubleVector vB = Vector.of([4d, 5d, 6d] as double[])

        when:
        vectorRepository.saveCustom(vA)

        then:
        def ex1 = thrown(IllegalArgumentException)
        ex1.message.contains("POSTGRES does not support")

        when:
        vectorRepository.saveCustom(vB)

        then:
        def ex2 = thrown(IllegalArgumentException)
        ex2.message.contains("POSTGRES does not support")
    }

    void "paging cannot be exercised with DoubleVector on Postgres due to unsupported type"() {
        given:
        DoubleVector v1 = Vector.of([1d, 2d, 3d] as double[])

        when:
        vectorRepository.saveCustom(v1)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("POSTGRES does not support")
    }

    void "derived vector near/within/between with DoubleVector are not supported on Postgres"() {
        given:
        Vector queryVector = Vector.of([1d, 0d, 0d] as double[])

        when:
        vectorRepository.searchByEmbeddingNear(queryVector, 2d)

        then:
        def nearEx = thrown(IllegalArgumentException)
        nearEx.message.contains("POSTGRES does not support")

        when:
        vectorRepository.searchByEmbeddingWithin(queryVector, 0d, 0.2d)

        then:
        def withinEx = thrown(IllegalArgumentException)
        withinEx.message.contains("POSTGRES does not support")

        when:
        vectorRepository.searchByEmbeddingBetween(queryVector, 0d, 0.2d)

        then:
        def betweenEx = thrown(IllegalArgumentException)
        betweenEx.message.contains("POSTGRES does not support")
    }

    private void executeSilently(String sql) {
        java.sql.Connection c = null
        java.sql.Statement st = null
        try {
            c = dataSource.getConnection()
            st = c.createStatement()
            st.execute(sql)
        } catch (Throwable e) {
            println e
            // ignore if already exists or unsupported in current XE version
        } finally {
            try { st?.close() } catch (ignored) {}
            try { c?.close() } catch (ignored) {}
        }
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
