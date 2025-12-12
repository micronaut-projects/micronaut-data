package io.micronaut.data.r2dbc.postgres.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.Vector
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.r2dbc.postgres.PostgresTestPropertyProvider
import io.micronaut.data.repository.CrudRepository
import jakarta.persistence.Column
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * R2DBC specs for Postgres pgvector support covering DoubleVector and FloatVector.
 * Mirrors the JDBC pgvector entity specs.
 */
class PostgresR2dbcVectorEntitySpec extends Specification implements PostgresTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties + ["spec.name": "PostgresR2dbcVectorEntitySpec"])

    @Shared
    VectorDoubleDocRepository vectorDoubleDocRepository = context.getBean(VectorDoubleDocRepository)

    @Shared
    VectorFloatDocRepository vectorFloatDocRepository = context.getBean(VectorFloatDocRepository)

    // FLOAT64/default
    void "R2DBC DoubleVector - default CRUD and custom @Query"() {
        given:
        def repo = vectorDoubleDocRepository
        double[] dv = [1d, 2.5d, -3.75d] as double[]
        Vector.DoubleVector v1 = Vector.of(dv)

        when:
        def saved = repo.save(new VectorDoubleDoc(embedding: v1))

        then:
        saved?.id != null

        when:
        def fetched = repo.findById(saved.id).orElse(null)

        then:
        fetched != null
        fetched.embedding.type == Double.TYPE
        fetched.embedding.toDoubleArray().toList() == dv.toList()

        when:
        double[] dv2 = [3d, 0.0d, 7.25d] as double[]
        Vector.DoubleVector v2 = Vector.of(dv2)
        fetched.embedding = v2
        def updated = repo.update(fetched)

        then:
        updated != null
        updated.embedding.type == Double.TYPE
        updated.embedding.toDoubleArray().toList() == dv2.toList()

        when: "custom @Query insert and update"
        double[] dvx = [2d, 4d, 6d] as double[]
        Vector.DoubleVector vx = Vector.of(dvx)
        repo.saveCustom(vx)
        def all = repo.findAll()
        def e = all.find { it.embedding?.toDoubleArray()?.toList() == dvx.toList() }

        then:
        e != null
        e.id != null

        when:
        double[] dvy = [-1d, 0.5d, 10d] as double[]
        Vector.DoubleVector vy = Vector.of(dvy)
        repo.updateCustom(e.id, vy)
        def after = repo.findById(e.id).orElse(null)

        then:
        after != null
        after.embedding.type == Double.TYPE
        after.embedding.toDoubleArray().toList() == dvy.toList()
    }

    // FLOAT32
    void "R2DBC FloatVector - default CRUD and custom @Query"() {
        given:
        def repo = vectorFloatDocRepository
        Vector.FloatVector v1 = Vector.of([1f, 2.5f, -3.75f] as float[])

        when:
        def saved = repo.save(new VectorFloatDoc(embedding: v1))

        then:
        saved?.id != null

        when:
        def fetched = repo.findById(saved.id).orElse(null)

        then:
        fetched != null
        fetched.embedding.type == Float.TYPE
        fetched.embedding.toFloatArray().toList() == [1f, 2.5f, -3.75f]

        when:
        Vector.FloatVector v2 = Vector.of([3f, 0f, 7.25f] as float[])
        fetched.embedding = v2
        def updated = repo.update(fetched)

        then:
        updated != null
        updated.embedding.type == Float.TYPE
        updated.embedding.toFloatArray().toList() == [3f, 0f, 7.25f]

        when: "custom @Query insert and update"
        Vector.FloatVector vx = Vector.of([10f, 11f, 12f] as float[])
        repo.saveCustom(vx)
        def all = repo.findAll()
        def e = all.find { it.embedding?.toFloatArray()?.toList() == [10f, 11f, 12f] }

        then:
        e != null

        when:
        Vector.FloatVector vy = Vector.of([13f, 14f, 15f] as float[])
        repo.updateCustom(e.id, vy)
        def after = repo.findById(e.id).orElse(null)

        then:
        after != null
        after.embedding.toFloatArray().toList() == [13f, 14f, 15f]
    }
}

@MappedEntity("vector_double_doc")
class VectorDoubleDoc {
    @Id
    @GeneratedValue
    Long id

    // Use length as vector dimensions for pgvector
    @Column(length = 3)
    Vector.DoubleVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    Vector.DoubleVector getEmbedding() { return embedding }
    void setEmbedding(Vector.DoubleVector embedding) { this.embedding = embedding }
}

@MappedEntity("vector_float_doc")
class VectorFloatDoc {
    @Id
    @GeneratedValue
    Long id

    // Use length as vector dimensions for pgvector
    @Column(length = 3)
    Vector.FloatVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    Vector.FloatVector getEmbedding() { return embedding }
    void setEmbedding(Vector.FloatVector embedding) { this.embedding = embedding }
}

// Repositories (R2DBC, Postgres)

@Requires(property = "spec.name", value = "PostgresR2dbcVectorEntitySpec")
@R2dbcRepository(dialect = Dialect.POSTGRES)
interface VectorDoubleDocRepository extends CrudRepository<VectorDoubleDoc, Long> {

    @Query("INSERT INTO vector_double_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_double_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") Vector.DoubleVector vec)

    @Query("UPDATE vector_double_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_double_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector.DoubleVector vec)

    @Query("SELECT * FROM vector_double_doc")
    List<VectorDoubleDoc> findAll()
}

@Requires(property = "spec.name", value = "PostgresR2dbcVectorEntitySpec")
@R2dbcRepository(dialect = Dialect.POSTGRES)
interface VectorFloatDocRepository extends CrudRepository<VectorFloatDoc, Long> {

    @Query("INSERT INTO vector_float_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_float_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") Vector.FloatVector vec)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector.FloatVector vec)

    @Query("SELECT * FROM vector_float_doc")
    List<VectorFloatDoc> findAll()
}
