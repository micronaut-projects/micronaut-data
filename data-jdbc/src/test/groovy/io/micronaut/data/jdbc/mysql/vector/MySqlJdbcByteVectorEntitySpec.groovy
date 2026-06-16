package io.micronaut.data.jdbc.mysql.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.ByteVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.repository.PageableRepository
import jakarta.persistence.Column
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * MySQL HeatWave JDBC ByteVector integration spec, adapted from Oracle vector specs.
 */
class MySqlJdbcByteVectorEntitySpec extends Specification implements MySqlVectorTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    VectorByteDocRepository vectorRepository = context.getBean(VectorByteDocRepository)

    @Override
    List<String> packages() {
        // Ensure entity/repository in this package are scanned
        return [getClass().package.name]
    }

    void "test save, find and update single entity (custom queries with ByteVector)"() {
        given:
        byte[] bv = [1 as byte, 2 as byte, -3 as byte] as byte[]
        ByteVector v1 = Vector.of(bv)

        when: "saving ByteVector via custom query fails"
        vectorRepository.saveCustom(v1)

        then:
        def ex1 = thrown(IllegalArgumentException)
        ex1 instanceof IllegalArgumentException
        ex1.message.contains("MYSQL does not support")

        when: "updating ByteVector via custom query also fails"
        vectorRepository.updateCustom(1L, v1)

        then:
        def ex2 = thrown(IllegalArgumentException)
        ex2 instanceof IllegalArgumentException
        ex2.message.contains("MYSQL does not support")
    }

    void "test save/update via default repository methods (ByteVector)"() {
        given:
        byte[] bv = [2 as byte, -1 as byte, 0 as byte] as byte[]
        ByteVector v1 = Vector.of(bv)

        when: "saving entity with ByteVector fails"
        vectorRepository.save(new VectorByteDoc(embedding: v1))

        then:
        def ex1 = thrown(DataAccessException)
        assert ex1.cause instanceof IllegalArgumentException
        ex1.cause.message.contains("MYSQL does not support")

        when: "updating entity with ByteVector fails"
        vectorRepository.update(new VectorByteDoc(id: 1L, embedding: v1))

        then:
        def ex2 = thrown(DataAccessException)
        assert ex2.cause instanceof IllegalArgumentException
        ex2.cause.message.contains("MYSQL does not support")
    }
}

@MappedEntity("vector_byte_doc")
class VectorByteDoc {
    @Id
    @GeneratedValue
    Long id
    @Column(length=3)
    ByteVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    ByteVector getEmbedding() { return embedding }
    void setEmbedding(ByteVector embedding) { this.embedding = embedding }
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface VectorByteDocRepository extends PageableRepository<VectorByteDoc, Long> {

    @Query("INSERT INTO vector_byte_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_byte_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") ByteVector vec)

    @Query("SELECT * FROM vector_byte_doc WHERE id = :id")
    Optional<VectorByteDoc> findById(Long id)

    @Query("UPDATE vector_byte_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_byte_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") ByteVector vec)
}
