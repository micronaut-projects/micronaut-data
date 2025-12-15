package io.micronaut.data.jdbc.postgres

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.IntVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.repository.PageableRepository
import jakarta.persistence.Column
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Postgres pgvector tests using IntVector.
 * Verifies round-trip behavior and @Column(length) -> vector(N) dimension mapping.
 */
class PostgresJdbcIntVectorEntitySpec extends Specification implements PostgresTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    VectorIntDocRepository vectorRepository = context.getBean(VectorIntDocRepository)

    @Shared
    javax.sql.DataSource dataSource = context.getBean(javax.sql.DataSource)

    @Override
    List<String> packages() {
        // Ensure entity/repository in this package are scanned
        return [getClass().package.name]
    }

    void "test save, find and update single entity (using custom queries with io.micronaut.data.model.Vector)"() {
        given:
        int[] iv = [1, 2, -3] as int[]
        IntVector v1 = Vector.of(iv)

        when: "save via custom @Query using Vector parameter"
        vectorRepository.saveCustom(v1)
        def list = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))
        def e = list.find { it.embedding.toIntegerArray().toList() == iv.toList() }

        then: "entity persisted and read conversion to Micronaut Vector works"
        e != null
        e.id != null
        e.embedding != null
        e.embedding.type == Integer.TYPE
        e.embedding.toIntegerArray().toList() == iv.toList()

        when: "update via custom @Query to a new vector"
        int[] iv2 = [7, 0, 9] as int[]
        IntVector v2 = Vector.of(iv2)
        vectorRepository.updateCustom(e.id, v2)
        def updated = vectorRepository.findById(e.id).orElse(null)

        then:
        updated != null
        updated.embedding.type == Integer.TYPE
        updated.embedding.toIntegerArray().toList() == iv2.toList()
    }

    void "test save and update via default repository methods (no @Query)"() {
        given:
        int[] iv = [10, -5, 2] as int[]
        IntVector v1 = Vector.of(iv)

        when: "persist entity using default repository save (auto-generated SQL, no @Query)"
        def saved = vectorRepository.save(new VectorIntDoc(embedding: v1))

        then:
        saved?.id != null

        when: "load back and verify embedding round-trip"
        def fetched = vectorRepository.findById(saved.id).orElse(null)

        then:
        fetched != null
        fetched.embedding.type == Integer.TYPE
        fetched.embedding.toIntegerArray().toList() == iv.toList()

        when: "update entity using default repository update (auto-generated SQL, no @Query)"
        int[] iv2 = [-1, 3, 4] as int[]
        IntVector v2 = Vector.of(iv2)
        fetched.embedding = v2
        def updated = vectorRepository.update(fetched)

        then:
        updated != null
        updated.embedding.type == Integer.TYPE
        updated.embedding.toIntegerArray().toList() == iv2.toList()
    }

    void "test save, find and update multiple entities"() {
        given:
        IntVector vA = Vector.of([1, 2, 3] as int[])
        IntVector vB = Vector.of([4, 5, 6] as int[])

        when:
        vectorRepository.saveCustom(vA)
        vectorRepository.saveCustom(vB)
        def rows = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))

        then:
        def idA = rows.find { it.embedding.toIntegerArray().toList() == [1, 2, 3] }?.id
        def idB = rows.find { it.embedding.toIntegerArray().toList() == [4, 5, 6] }?.id
        idA != null
        idB != null
        idA != idB

        when: "update both"
        IntVector vA2 = Vector.of([7, 8, 9] as int[])
        IntVector vB2 = Vector.of([0, -1, -2] as int[])
        vectorRepository.updateCustom(idA, vA2)
        vectorRepository.updateCustom(idB, vB2)
        def rows2 = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))

        then:
        def updatedA = rows2.find { it.id == idA }?.embedding?.toIntegerArray()?.toList()
        def updatedB = rows2.find { it.id == idB }?.embedding?.toIntegerArray()?.toList()
        updatedA == [7, 8, 9]
        updatedB == [0, -1, -2]
    }

    void "test paging over VectorIntDoc"() {
        given:
        IntVector v1 = Vector.of([1, 2, 3] as int[])
        IntVector v2 = Vector.of([4, 5, 6] as int[])
        vectorRepository.saveCustom(v1)
        vectorRepository.saveCustom(v2)

        when:
        def p0 = vectorRepository.findAll(Pageable.from(0, 1))
        def p1 = vectorRepository.findAll(Pageable.from(1, 1))

        then:
        p0 != null
        p1 != null
        p0.getContent().size() == 1
        p1.getContent().size() == 1
        p0.getTotalSize() >= 2
    }
}

@MappedEntity("vector_int_doc")
class VectorIntDoc {
    @Id
    @GeneratedValue
    Long id
    @Column(length = 3)
    IntVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    IntVector getEmbedding() { return embedding }
    void setEmbedding(IntVector embedding) { this.embedding = embedding }
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface VectorIntDocRepository extends PageableRepository<VectorIntDoc, Long> {

    @Query("INSERT INTO vector_int_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_int_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") IntVector vec)

    @Query("SELECT * FROM vector_int_doc WHERE id = :id")
    Optional<VectorIntDoc> findById(Long id)

    @Query("UPDATE vector_int_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_int_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") IntVector vec)
}
