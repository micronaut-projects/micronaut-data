package io.micronaut.data.r2dbc.mysql.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.ByteVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.CrudRepository
import jakarta.persistence.Column
import spock.lang.Specification

/**
 * MySQL HeatWave R2DBC ByteVector negative spec (float-only support).
 * Assert that ByteVector usage results in IllegalArgumentException.
 */
class MySqlR2dbcByteVectorEntitySpec extends Specification implements MySqlVectorTestPropertyProvider {

    void "mysql r2dbc vector repository fails fast at startup"() {
        when:
        Throwable failure = null
        ApplicationContext context = null
        try {
            context = ApplicationContext.run(properties + ["spec.name": "MySqlR2dbcByteVectorEntitySpec"])
            VectorByteDocRepository repository = context.getBean(VectorByteDocRepository)
            repository.save(new VectorByteDoc(embedding: Vector.of([1 as byte, 2 as byte, -3 as byte] as byte[])))
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

@MappedEntity("vector_byte_doc")
class VectorByteDoc {
    @Id
    @GeneratedValue
    Long id

    // Use length as vector dimensions for MySQL HeatWave
    @Column(length = 3)
    ByteVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    ByteVector getEmbedding() { return embedding }
    void setEmbedding(ByteVector embedding) { this.embedding = embedding }
}

@Requires(property = "spec.name", value = "MySqlR2dbcByteVectorEntitySpec")
@R2dbcRepository(dialect = Dialect.MYSQL)
interface VectorByteDocRepository extends CrudRepository<VectorByteDoc, Long> {

    @Query("INSERT INTO vector_byte_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_byte_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") ByteVector vec)

    @Query("UPDATE vector_byte_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_byte_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") ByteVector vec)

    @Query("SELECT * FROM vector_byte_doc")
    java.util.List<VectorByteDoc> findAll()
}
