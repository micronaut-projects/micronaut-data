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
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * MySQL HeatWave R2DBC ByteVector negative spec (float-only support).
 * Assert that ByteVector usage results in IllegalArgumentException.
 */
class MySqlR2dbcByteVectorEntitySpec extends Specification implements MySqlVectorTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties + ["spec.name": "MySqlR2dbcByteVectorEntitySpec"])

    @Shared
    VectorByteDocRepository vectorRepository = context.getBean(VectorByteDocRepository)

    void "custom queries with ByteVector are not supported on MySQL (R2DBC)"() {
        given:
        byte[] bv = [1 as byte, 2 as byte, -3 as byte] as byte[]
        ByteVector v1 = Vector.of(bv)

        when: "save via custom @Query using Vector parameter"
        vectorRepository.saveCustom(v1)

        then:
        def ex1 = thrown(IllegalArgumentException)
        ex1.message.contains("Vectors aren't supported for the database MYSQL")

        when: "update via custom @Query with ByteVector"
        vectorRepository.updateCustom(1L, v1)

        then:
        def ex2 = thrown(IllegalArgumentException)
        ex2.message.contains("Vectors aren't supported for the database MYSQL")
    }

    void "default repository methods with ByteVector are not supported on MySQL (R2DBC)"() {
        given:
        byte[] bv = [2 as byte, -1 as byte, 0 as byte] as byte[]
        ByteVector v1 = Vector.of(bv)

        when: "persist entity using default repository save"
        vectorRepository.save(new VectorByteDoc(embedding: v1))

        then:
        def ex1 = thrown(IllegalArgumentException)
        ex1.message.contains("Vectors aren't supported for the database MYSQL")

        when: "update entity using default repository update"
        vectorRepository.update(new VectorByteDoc(id: 1L, embedding: v1))

        then:
        def ex2 = thrown(IllegalArgumentException)
        ex2.message.contains("Vectors aren't supported for the database MYSQL")
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
