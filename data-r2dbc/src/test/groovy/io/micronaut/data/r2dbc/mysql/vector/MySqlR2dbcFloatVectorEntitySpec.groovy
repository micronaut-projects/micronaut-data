package io.micronaut.data.r2dbc.mysql.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.CrudRepository
import jakarta.persistence.Column
import spock.lang.Specification

/**
 * MySQL HeatWave R2DBC FloatVector negative spec (float-only support).
 * Assert that FloatVector usage results in IllegalArgumentException.
 */
class MySqlR2dbcFloatVectorEntitySpec extends Specification implements MySqlVectorTestPropertyProvider {

    void "mysql r2dbc vector repository fails fast at startup"() {
        when:
        Throwable failure = null
        ApplicationContext context = null
        try {
            context = ApplicationContext.run(properties + ["spec.name": "MySqlR2dbcFloatVectorEntitySpec"])
            VectorFloatDocRepository repository = context.getBean(VectorFloatDocRepository)
            repository.save(new VectorFloatDoc(embedding: Vector.of([1f, 2.5f, -3.75f] as float[])))
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

@MappedEntity("vector_float_doc")
class VectorFloatDoc {
    @Id
    @GeneratedValue
    Long id

    // Use length as vector dimensions for MySQL HeatWave
    @Column(length = 3)
    FloatVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    FloatVector getEmbedding() { return embedding }
    void setEmbedding(FloatVector embedding) { this.embedding = embedding }
}

@Requires(property = "spec.name", value = "MySqlR2dbcFloatVectorEntitySpec")
@R2dbcRepository(dialect = Dialect.MYSQL)
interface VectorFloatDocRepository extends CrudRepository<VectorFloatDoc, Long> {

    @Query("INSERT INTO vector_float_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_float_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") FloatVector vec)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") FloatVector vec)

    @Query("SELECT * FROM vector_float_doc")
    java.util.List<VectorFloatDoc> findAll()
}
