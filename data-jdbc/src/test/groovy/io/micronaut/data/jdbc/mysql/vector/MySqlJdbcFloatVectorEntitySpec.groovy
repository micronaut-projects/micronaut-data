package io.micronaut.data.jdbc.mysql.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.vector.search.Score
import io.micronaut.data.model.vector.search.ScoringFunction
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.model.vector.search.Similarity
import io.micronaut.data.repository.PageableRepository
import jakarta.persistence.Column
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * MySQL HeatWave JDBC FloatVector integration spec, adapted from Oracle vector specs.
 */
class MySqlJdbcFloatVectorEntitySpec extends Specification implements MySqlVectorTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    VectorFloatDocRepository vectorRepository = context.getBean(VectorFloatDocRepository)

    @Override
    List<String> packages() {
        // Ensure entity/repository in this package are scanned
        return [getClass().package.name]
    }

    void "test save, find and update single entity (custom queries with FloatVector)"() {
        given:
        float[] fv = [1f, 2.5f, -3.75f] as float[]
        FloatVector v1 = Vector.of(fv)

        when:
        vectorRepository.saveCustom(v1)
        def list = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))
        def e = list.find { it.embedding.toFloatArray().toList() == fv.toList() }

        then:
        e != null
        e.id != null
        e.embedding != null
        e.embedding.type == Float.TYPE
        e.embedding.toFloatArray().toList() == fv.toList()

        when:
        float[] fv2 = [3f, 0.0f, 7.25f] as float[]
        FloatVector v2 = Vector.of(fv2)
        vectorRepository.updateCustom(e.id, v2)
        def updated = vectorRepository.findById(e.id).orElse(null)

        then:
        updated != null
        updated.embedding.type == Float.TYPE
        updated.embedding.toFloatArray().toList() == fv2.toList()
    }

    void "test save/update via default repository methods (FloatVector)"() {
        given:
        float[] fv = [2f, -1.5f, 0.25f] as float[]
        FloatVector v1 = Vector.of(fv)

        when:
        def saved = vectorRepository.save(new VectorFloatDoc(embedding: v1))

        then:
        saved?.id != null

        when:
        def fetched = vectorRepository.findById(saved.id).orElse(null)

        then:
        fetched != null
        fetched.embedding.type == Float.TYPE
        fetched.embedding.toFloatArray().toList() == fv.toList()

        when:
        float[] fv2 = [-0.5f, 3f, 4.5f] as float[]
        FloatVector v2 = Vector.of(fv2)
        fetched.embedding = v2
        def updated = vectorRepository.update(fetched)

        then:
        updated != null
        updated.embedding.type == Float.TYPE
        updated.embedding.toFloatArray().toList() == fv2.toList()
    }

    void "test derived vector near and within search fails when DISTANCE function is unavailable"() {
        given:
        vectorRepository.deleteAll()
        vectorRepository.save(new VectorFloatDoc(embedding: Vector.of([1f, 0f, 0f] as float[])))
        vectorRepository.save(new VectorFloatDoc(embedding: Vector.of([0f, 1f, 0f] as float[])))

        when:
        vectorRepository.searchByEmbeddingNear(Vector.of([1f, 0f, 0f] as float[]), 2d)

        then:
        def nearUnsupported = thrown(DataAccessException)
        assertDistanceFunctionMissing(nearUnsupported)

        when:
        vectorRepository.searchByEmbeddingWithin(Vector.of([1f, 0f, 0f] as float[]), 0d, 0.2d)

        then:
        def withinUnsupported = thrown(DataAccessException)
        assertDistanceFunctionMissing(withinUnsupported)

        when:
        vectorRepository.searchByEmbeddingBetween(Vector.of([1f, 0f, 0f] as float[]), 0d, 0.2d)

        then:
        def betweenUnsupported = thrown(DataAccessException)
        assertDistanceFunctionMissing(betweenUnsupported)
    }

    void "test derived vector Score/Similarity wrappers fail when DISTANCE function is unavailable"() {
        given:
        vectorRepository.deleteAll()
        def vectors = [
            [1f, 0f, 0f],
            [0.9f, 0.1f, 0f],
            [0.8f, 0.2f, 0f],
            [0.7f, 0.3f, 0f],
            [0.6f, 0.4f, 0f],
            [0.5f, 0.5f, 0f],
            [0.4f, 0.6f, 0f],
            [0.3f, 0.7f, 0f],
            [0.2f, 0.8f, 0f],
            [0f, 1f, 0f]
        ]
        vectors.each { v -> vectorRepository.save(new VectorFloatDoc(embedding: Vector.of(v as float[]))) }
        Vector q = Vector.of([1f, 0f, 0f] as float[])

        when:
        vectorRepository.searchByEmbeddingNear(q, 2d)

        then:
        def nearUnsupported = thrown(DataAccessException)
        assertDistanceFunctionMissing(nearUnsupported)

        when:
        vectorRepository.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.COSINE)

        then:
        def cosineUnsupported = thrown(DataAccessException)
        assertDistanceFunctionMissing(cosineUnsupported)

        when:
        vectorRepository.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.DOT)

        then:
        def dotUnsupported = thrown(DataAccessException)
        assertDistanceFunctionMissing(dotUnsupported)

        when:
        vectorRepository.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.L2_EUCLIDEAN)

        then:
        def euclideanUnsupported = thrown(DataAccessException)
        assertDistanceFunctionMissing(euclideanUnsupported)

        when:
        vectorRepository.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.L2_EUCLIDEAN_SQUARED)

        then:
        def euclideanSquaredUnsupported = thrown(IllegalArgumentException)
        euclideanSquaredUnsupported.message.contains("not supported")
        euclideanSquaredUnsupported.message.contains("MYSQL")

        when:
        vectorRepository.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.L1_MANHATTAN)

        then:
        def manhattanEx = thrown(IllegalArgumentException)
        manhattanEx.message.contains("not supported")
        manhattanEx.message.contains("MYSQL")
    }

    private static void assertDistanceFunctionMissing(DataAccessException exception) {
        assert exception.message != null
        assert exception.message.contains("DISTANCE")
        assert exception.message.contains("does not exist")
    }
}

@MappedEntity("vector_float_doc")
class VectorFloatDoc {
    @Id
    @GeneratedValue
    Long id
    @Column(length=3)
    FloatVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    FloatVector getEmbedding() { return embedding }
    void setEmbedding(FloatVector embedding) { this.embedding = embedding }
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface VectorFloatDocRepository extends PageableRepository<VectorFloatDoc, Long> {

    @Query("INSERT INTO vector_float_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_float_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") FloatVector vec)

    @Query("SELECT * FROM vector_float_doc WHERE id = :id")
    Optional<VectorFloatDoc> findById(Long id)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") FloatVector vec)

    SearchResults<VectorFloatDoc> searchByEmbeddingNear(Vector vector, Double maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingNear(Vector vector, Score maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingWithin(Vector vector, Double minDistance, Double maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingWithin(Vector vector, Similarity minDistance, Similarity maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingBetween(Vector vector, Double minDistance, Double maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingBetween(Vector vector, Score minDistance, Score maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingNear(Vector vector,
                                                        Score maxDistance,
                                                        ScoringFunction function)
}
