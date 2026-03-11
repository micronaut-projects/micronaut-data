package io.micronaut.data.jdbc.mysql.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.repository.PageableRepository
import jakarta.persistence.Column
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * MySQL HeatWave JDBC DoubleVector integration spec, adapted from OracleJdbcDoubleVectorEntitySpec.
 */
class MySqlJdbcDoubleVectorEntitySpec extends Specification implements MySqlVectorTestPropertyProvider {

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

    void "test custom vector queries with DoubleVector are not supported on MySQL"() {
        given:
        double[] dv = [1d, 2.5d, -3.75d] as double[]
        DoubleVector v1 = Vector.of(dv)

        when: "save via custom @Query using Vector parameter"
        vectorRepository.saveCustom(v1)

        then: "MySQL rejects DoubleVector"
        def ex1 = thrown(IllegalArgumentException)
        assert ex1 instanceof IllegalArgumentException
        ex1.message.contains("MYSQL does not support")

        when: "update via custom @Query with DoubleVector"
        vectorRepository.updateCustom(1L, v1)

        then:
        def ex2 = thrown(IllegalArgumentException)
        assert ex2 instanceof IllegalArgumentException
        ex2.message.contains("MYSQL does not support")
    }

    void "test default repository methods with DoubleVector are not supported on MySQL"() {
        given:
        double[] dv = [2d, -1.5d, 0.25d] as double[]
        DoubleVector v1 = Vector.of(dv)

        when: "persist entity using default repository save"
        vectorRepository.save(new VectorDoubleDoc(embedding: v1))

        then:
        def ex1 = thrown(DataAccessException)
        assert ex1.cause instanceof IllegalArgumentException
        ex1.cause.message.contains("MYSQL does not support")

        when: "update entity using default repository update"
        vectorRepository.update(new VectorDoubleDoc(id: 1L, embedding: v1))

        then:
        def ex2 = thrown(DataAccessException)
        assert ex2.cause instanceof IllegalArgumentException
        ex2.cause.message.contains("MYSQL does not support")
    }

    void "test multiple DoubleVector operations are not supported on MySQL"() {
        given:
        DoubleVector vA = Vector.of([1d, 2d, 3d] as double[])
        DoubleVector vB = Vector.of([4d, 5d, 6d] as double[])

        when:
        vectorRepository.saveCustom(vA)

        then:
        def ex1 = thrown(IllegalArgumentException)
        assert ex1 instanceof IllegalArgumentException
        ex1.message.contains("MYSQL does not support")

        when:
        vectorRepository.saveCustom(vB)

        then:
        def ex2 = thrown(IllegalArgumentException)
        assert ex2 instanceof IllegalArgumentException
        ex2.message.contains("MYSQL does not support")
    }
}

@MappedEntity("vector_double_doc")
class VectorDoubleDoc {
    @Id
    @GeneratedValue
    Long id
    @Column(length=3)
    DoubleVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    DoubleVector getEmbedding() { return embedding }
    void setEmbedding(DoubleVector embedding) { this.embedding = embedding }
}

@JdbcRepository(dialect = Dialect.MYSQL)
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
}
