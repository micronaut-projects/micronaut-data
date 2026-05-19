package io.micronaut.data.r2dbc.mysql.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.CrudRepository
import jakarta.persistence.Column
import spock.lang.Specification

/**
 * MySQL HeatWave R2DBC DoubleVector negative spec (float-only support).
 * Assert that DoubleVector usage results in IllegalArgumentException.
 */
class MySqlR2dbcDoubleVectorEntitySpec extends Specification implements MySqlVectorTestPropertyProvider {

    void "mysql r2dbc vector repository fails fast at startup"() {
        when:
        Throwable failure = null
        ApplicationContext context = null
        try {
            context = ApplicationContext.run(properties + ["spec.name": "MySqlR2dbcDoubleVectorEntitySpec"])
            VectorDoubleDocRepository repository = context.getBean(VectorDoubleDocRepository)
            repository.save(new VectorDoubleDoc(embedding: Vector.of([1d, 2.5d, -3.75d] as double[])))
        } catch (Throwable t) {
            failure = t
        } finally {
            context?.close()
        }

        then:
        failure != null
        failure.message.contains("Vectors aren't supported for the database MYSQL")
    }
}

@MappedEntity("vector_double_doc")
class VectorDoubleDoc {
    @Id
    @GeneratedValue
    Long id

    // Use length as vector dimensions for MySQL HeatWave
    @Column(length = 3)
    DoubleVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    DoubleVector getEmbedding() { return embedding }
    void setEmbedding(DoubleVector embedding) { this.embedding = embedding }
}

@Requires(property = "spec.name", value = "MySqlR2dbcDoubleVectorEntitySpec")
@R2dbcRepository(dialect = Dialect.MYSQL)
interface VectorDoubleDocRepository extends CrudRepository<VectorDoubleDoc, Long> {

    @Query("INSERT INTO vector_double_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_double_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") DoubleVector vec)

    @Query("UPDATE vector_double_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_double_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") DoubleVector vec)

    @Query("SELECT * FROM vector_double_doc")
    java.util.List<VectorDoubleDoc> findAll()
}
