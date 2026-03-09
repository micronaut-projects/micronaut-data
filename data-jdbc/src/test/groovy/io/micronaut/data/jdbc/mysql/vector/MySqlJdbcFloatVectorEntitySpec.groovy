package io.micronaut.data.jdbc.mysql.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.mysql.MySQLTestPropertyProvider
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.vector.search.Score
import io.micronaut.data.model.vector.search.ScoringFunction
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.model.vector.search.Similarity
import io.micronaut.data.model.vector.search.SimilarityNormalizer
import io.micronaut.data.repository.PageableRepository
import jakarta.persistence.Column
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * MySQL HeatWave JDBC FloatVector integration spec, adapted from Oracle vector specs.
 */
class MySqlJdbcFloatVectorEntitySpec extends Specification implements MySQLTestPropertyProvider {

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

    void "test derived vector near and within search results (HeatWave DISTANCE)"() {
        given:
        vectorRepository.deleteAll()
        vectorRepository.save(new VectorFloatDoc(embedding: Vector.of([1f, 0f, 0f] as float[])))
        vectorRepository.save(new VectorFloatDoc(embedding: Vector.of([0f, 1f, 0f] as float[])))

        when:
        SearchResults<VectorFloatDoc> nearResults = null
        SearchResults<VectorFloatDoc> withinResults = null
        SearchResults<VectorFloatDoc> betweenResults = null
        DataAccessException unsupportedEx = null
        try {
            nearResults = vectorRepository.searchByEmbeddingNear(Vector.of([1f, 0f, 0f] as float[]), 2d)
            withinResults = vectorRepository.searchByEmbeddingWithin(Vector.of([1f, 0f, 0f] as float[]), 0d, 0.2d)
            betweenResults = vectorRepository.searchByEmbeddingBetween(Vector.of([1f, 0f, 0f] as float[]), 0d, 0.2d)
        } catch (DataAccessException e) {
            unsupportedEx = e
        }

        then:
        if (unsupportedEx == null) {
            assert nearResults != null
            assert nearResults.results() != null
            assert withinResults != null
            assert withinResults.results() != null
            assert betweenResults != null
            assert betweenResults.results() != null
            assert betweenResults.results().every { it.score().value() >= 0d && it.score().value() <= 0.2d }
        } else {
            assert unsupportedEx.message.contains("DISTANCE")
            assert unsupportedEx.message.contains("does not exist")
        }
    }

    void "test derived vector Score/Similarity wrappers and scoring function behavior with 10 vectors"() {
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
        SearchResults<VectorFloatDoc> nearByDouble = null
        SearchResults<VectorFloatDoc> nearByScore = null
        SearchResults<VectorFloatDoc> withinBySimilarity = null
        SearchResults<VectorFloatDoc> betweenByScore = null
        SearchResults<VectorFloatDoc> euclideanOk = null
        DataAccessException unsupportedDistance = null
        try {
            nearByDouble = vectorRepository.searchByEmbeddingNear(q, 2d)
            nearByScore = vectorRepository.searchByEmbeddingNear(q, new Score(2d))
            withinBySimilarity = vectorRepository.searchByEmbeddingWithin(q, new Similarity(0d), new Similarity(2d))
            betweenByScore = vectorRepository.searchByEmbeddingBetween(q, new Score(0d), new Score(2d))
            euclideanOk = vectorRepository.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.EUCLIDEAN)
        } catch (DataAccessException e) {
            unsupportedDistance = e
        }

        then:
        if (unsupportedDistance == null) {
            assert nearByDouble.results().size() >= 8
            assert nearByScore.results().size() == nearByDouble.results().size()
            assert withinBySimilarity.results().size() == nearByDouble.results().size()
            assert betweenByScore.results().size() == nearByDouble.results().size()
            assert nearByScore.results().collect { it.entity().id } == nearByDouble.results().collect { it.entity().id }
            assert euclideanOk.results().size() == nearByDouble.results().size()
            assertScoringResults(euclideanOk, ScoringFunction.EUCLIDEAN)
        } else {
            assert unsupportedDistance.message.contains("DISTANCE")
            assert unsupportedDistance.message.contains("does not exist")
        }

        when:
        SearchResults<VectorFloatDoc> cosineOk = null
        DataAccessException cosineDistanceUnsupported = null
        try {
            cosineOk = vectorRepository.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.COSINE)
        } catch (DataAccessException e) {
            cosineDistanceUnsupported = e
        }

        then:
        if (cosineDistanceUnsupported == null) {
            assert cosineOk != null
            assert cosineOk.results() != null
            assertScoringResults(cosineOk, ScoringFunction.COSINE)
        } else {
            assert cosineDistanceUnsupported.message.contains("DISTANCE")
            assert cosineDistanceUnsupported.message.contains("does not exist")
        }

        when:
        SearchResults<VectorFloatDoc> dotOk = null
        DataAccessException dotDistanceUnsupported = null
        try {
            dotOk = vectorRepository.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.DOT_PRODUCT)
        } catch (DataAccessException e) {
            dotDistanceUnsupported = e
        }

        then:
        if (dotDistanceUnsupported == null) {
            assert dotOk != null
            assert dotOk.results() != null
            assertScoringResults(dotOk, ScoringFunction.DOT_PRODUCT)
        } else {
            assert dotDistanceUnsupported.message.contains("DISTANCE")
            assert dotDistanceUnsupported.message.contains("does not exist")
        }

        when:
        SearchResults<VectorFloatDoc> innerAliasOk = null
        DataAccessException innerDistanceUnsupported = null
        try {
            innerAliasOk = vectorRepository.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.INNER_PRODUCT)
        } catch (DataAccessException e) {
            innerDistanceUnsupported = e
        }

        then:
        if (innerDistanceUnsupported == null) {
            assert innerAliasOk != null
            assert innerAliasOk.results() != null
            assertScoringResults(innerAliasOk, ScoringFunction.INNER_PRODUCT)
        } else {
            assert innerDistanceUnsupported.message.contains("DISTANCE")
            assert innerDistanceUnsupported.message.contains("does not exist")
        }

        when:
        vectorRepository.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.TAXICAB)

        then:
        def taxicabEx = thrown(IllegalArgumentException)
        taxicabEx.message.contains("not supported")
        taxicabEx.message.contains("MYSQL")
    }

    private static void assertScoringResults(SearchResults<VectorFloatDoc> results, ScoringFunction function) {
        assert results != null
        assert results.results() != null
        assert results.results().size() >= 1
        def normalizer = SimilarityNormalizer.forScoringFunction(function)
        results.results().each { r ->
            double score = r.score().value()
            assert r.similarity() != null
            double similarity = r.similarity().value()
            assert Math.abs(similarity - normalizer.getSimilarity(score)) < 1.0e-9d
        }
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
