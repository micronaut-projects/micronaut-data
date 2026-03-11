package io.micronaut.data.r2dbc.postgres.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.CrudRepository
import jakarta.persistence.Column
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * R2DBC specs for Postgres pgvector support.
 * Only FloatVector is supported; DoubleVector must throw an exception.
 */
class PostgresR2dbcVectorEntitySpec extends Specification implements PostgresVectorTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties + ["spec.name": "PostgresR2dbcVectorEntitySpec"])

    @Shared
    VectorDoubleDocRepository vectorDoubleDocRepository = context.getBean(VectorDoubleDocRepository)

    @Shared
    VectorFloatDocRepository vectorFloatDocRepository = context.getBean(VectorFloatDocRepository)


    // FLOAT64/default
    void "DoubleVector is not supported on Postgres R2DBC"() {
        given:
        def repo = vectorDoubleDocRepository
        double[] dv = [1d, 2.5d, -3.75d] as double[]
        DoubleVector v1 = Vector.of(dv)

        when: "persist entity using default repository save"
        repo.save(new VectorDoubleDoc(embedding: v1))
        then:
        thrown(IllegalArgumentException)

        when: "update entity using default repository update"
        repo.update(new VectorDoubleDoc(id: 1L, embedding: v1))
        then:
        thrown(IllegalArgumentException)

        when: "custom @Query insert"
        repo.saveCustom(v1)
        then:
        thrown(IllegalArgumentException)

        when: "custom @Query update"
        repo.updateCustom(1L, v1)
        then:
        thrown(IllegalArgumentException)
    }

    // FLOAT32
    void "R2DBC FloatVector - default CRUD and custom @Query"() {
        given:
        def repo = vectorFloatDocRepository
        FloatVector v1 = Vector.of([1f, 2.5f, -3.75f] as float[])

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
        FloatVector v2 = Vector.of([3f, 0f, 7.25f] as float[])
        fetched.embedding = v2
        def updated = repo.update(fetched)

        then:
        updated != null
        updated.embedding.type == Float.TYPE
        updated.embedding.toFloatArray().toList() == [3f, 0f, 7.25f]

        when: "custom @Query insert and update"
        FloatVector vx = Vector.of([10f, 11f, 12f] as float[])
        repo.saveCustom(vx)
        def all = repo.findAll()
        def e = all.find { it.embedding?.toFloatArray()?.toList() == [10f, 11f, 12f] }

        then:
        e != null

        when:
        FloatVector vy = Vector.of([13f, 14f, 15f] as float[])
        repo.updateCustom(e.id, vy)
        def after = repo.findById(e.id).orElse(null)

        then:
        after != null
        after.embedding.toFloatArray().toList() == [13f, 14f, 15f]
    }

    void "R2DBC FloatVector - derived near and within SearchResults"() {
        given:
        def repo = vectorFloatDocRepository
        repo.deleteAll()
        repo.save(new VectorFloatDoc(embedding: Vector.of([1f, 0f, 0f] as float[])))
        repo.save(new VectorFloatDoc(embedding: Vector.of([0f, 1f, 0f] as float[])))

        when:
        SearchResults<VectorFloatDoc> nearResults = repo.searchByEmbeddingNear(Vector.of([1f, 0f, 0f] as float[]), 2d)

        then:
        nearResults != null
        nearResults.results() != null
        nearResults.results().size() == 2
        nearResults.results().every { it.score().value() <= 2d }

        when:
        SearchResults<VectorFloatDoc> withinResults = repo.searchByEmbeddingWithin(Vector.of([1f, 0f, 0f] as float[]), 0d, 2d)

        then:
        withinResults != null
        withinResults.results() != null
        withinResults.results().size() == 2

        when:
        SearchResults<VectorFloatDoc> betweenResults = repo.searchByEmbeddingBetween(Vector.of([1f, 0f, 0f] as float[]), 0d, 2d)

        then:
        betweenResults != null
        betweenResults.results() != null
        betweenResults.results().size() == 2
        betweenResults.results().every { it.score().value() >= 0d && it.score().value() <= 2d }
    }

    void "R2DBC Postgres sparse-like vectors are explicitly unsupported"() {
        given:
        def repo = vectorFloatDocRepository
        float[] sparseLike = new float[16]
        sparseLike[0] = 1f
        FloatVector sparse = Vector.of(sparseLike)

        when:
        repo.save(new VectorFloatDoc(embedding: sparse))

        then:
        def ex1 = thrown(IllegalArgumentException)
        ex1.message.contains("Sparse vectors are not supported for Postgres R2DBC")

        when:
        repo.saveCustom(sparse)

        then:
        def ex2 = thrown(IllegalArgumentException)
        ex2.message.contains("Sparse vectors are not supported for Postgres R2DBC")
    }

}
@MappedEntity("vector_double_doc")
class VectorDoubleDoc {
    @Id
    @GeneratedValue
    Long id

    // Use length as vector dimensions for pgvector
    @Column(length = 3)
    DoubleVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    DoubleVector getEmbedding() { return embedding }
    void setEmbedding(DoubleVector embedding) { this.embedding = embedding }
}

@MappedEntity("vector_float_doc")
class VectorFloatDoc {
    @Id
    @GeneratedValue
    Long id

    // Use length as vector dimensions for pgvector
    @Column(length = 3)
    FloatVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    FloatVector getEmbedding() { return embedding }
    void setEmbedding(FloatVector embedding) { this.embedding = embedding }
}

// Repositories (R2DBC, Postgres)

@Requires(property = "spec.name", value = "PostgresR2dbcVectorEntitySpec")
@R2dbcRepository(dialect = Dialect.POSTGRES)
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
    List<VectorDoubleDoc> findAll()
}

@Requires(property = "spec.name", value = "PostgresR2dbcVectorEntitySpec")
@R2dbcRepository(dialect = Dialect.POSTGRES)
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
    List<VectorFloatDoc> findAll()

    SearchResults<VectorFloatDoc> searchByEmbeddingNear(Vector vector, Double maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingWithin(Vector vector, Double minDistance, Double maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingBetween(Vector vector, Double minDistance, Double maxDistance)
}
