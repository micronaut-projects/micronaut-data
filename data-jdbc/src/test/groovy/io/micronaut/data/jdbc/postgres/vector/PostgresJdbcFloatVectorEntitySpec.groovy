package io.micronaut.data.jdbc.postgres.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.vector.search.Score
import io.micronaut.data.model.vector.search.ScoringFunction
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.model.vector.search.Similarity
import io.micronaut.data.model.vector.search.SimilarityNormalizer
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.PageableRepository
import jakarta.persistence.Column
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PostgresJdbcFloatVectorEntitySpec extends Specification implements PostgresVectorTestPropertyProvider {

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

    void "test save, find and update single entity (using custom queries with io.micronaut.data.model.Vector)"() {
        given:
        float[] fv = [1f, 2.5f, -3.75f] as float[]
        FloatVector v1 = Vector.of(fv)

        when: "save via custom @Query using Vector parameter"
        vectorRepository.saveCustom(v1)
        def list = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))
        def e = list.find { it.embedding.toFloatArray().toList() == fv.toList() }

        then: "entity persisted and read conversion to Micronaut Vector works"
        e != null
        e.id != null
        e.embedding != null
        e.embedding.type == Float.TYPE
        e.embedding.toFloatArray().toList() == fv.toList()

        when: "update via custom @Query to a new vector"
        float[] fv2 = [3f, 0.0f, 7.25f] as float[]
        FloatVector v2 = Vector.of(fv2)
        vectorRepository.updateCustom(e.id, v2)
        def updated = vectorRepository.findById(e.id).orElse(null)

        then:
        updated != null
        updated.embedding.type == Float.TYPE
        updated.embedding.toFloatArray().toList() == fv2.toList()
    }

    void "test save and update via default repository methods (no @Query)"() {
        given:
        float[] fv = [2f, -1.5f, 0.25f] as float[]
        FloatVector v1 = Vector.of(fv)

        when: "persist entity using default repository save (auto-generated SQL, no @Query)"
        def saved = vectorRepository.save(new VectorFloatDoc(embedding: v1))

        then:
        saved?.id != null

        when: "load back and verify embedding round-trip"
        def fetched = vectorRepository.findById(saved.id).orElse(null)

        then:
        fetched != null
        fetched.embedding.type == Float.TYPE
        fetched.embedding.toFloatArray().toList() == fv.toList()

        when: "update entity using default repository update (auto-generated SQL, no @Query)"
        float[] fv2 = [-0.5f, 3f, 4.5f] as float[]
        FloatVector v2 = Vector.of(fv2)
        fetched.embedding = v2
        def updated = vectorRepository.update(fetched)

        then:
        updated != null
        updated.embedding.type == Float.TYPE
        updated.embedding.toFloatArray().toList() == fv2.toList()
    }

    void "test save, find and update multiple entities"() {
        given:
        FloatVector vA = Vector.of([1f, 2f, 3f] as float[])
        FloatVector vB = Vector.of([4f, 5f, 6f] as float[])

        when:
        vectorRepository.saveCustom(vA)
        vectorRepository.saveCustom(vB)
        def rows = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))

        then:
        def idA = rows.find { it.embedding.toFloatArray().toList() == [1f, 2f, 3f] }?.id
        def idB = rows.find { it.embedding.toFloatArray().toList() == [4f, 5f, 6f] }?.id
        idA != null
        idB != null
        idA != idB

        when: "update both"
        FloatVector vA2 = Vector.of([7f, 8f, 9f] as float[])
        FloatVector vB2 = Vector.of([0f, -1f, -2f] as float[])
        vectorRepository.updateCustom(idA, vA2)
        vectorRepository.updateCustom(idB, vB2)
        def rows2 = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))

        then:
        def updatedA = rows2.find { it.id == idA }?.embedding?.toFloatArray()?.toList()
        def updatedB = rows2.find { it.id == idB }?.embedding?.toFloatArray()?.toList()
        updatedA == [7f, 8f, 9f]
        updatedB == [0f, -1f, -2f]
    }

    void "test custom and async queries"() {
        given:
        FloatVector vec = Vector.of([10f, 11f, 12f] as float[])

        when:
        java.util.concurrent.Future<Integer> saveFut = vectorRepository.saveAsync(vec)

        then:
        saveFut.get(10, java.util.concurrent.TimeUnit.SECONDS) == 1

        when:
        def all = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))
        def last = all.last()
        java.util.concurrent.Future<java.util.List<VectorFloatDoc>> findFut = vectorRepository.findAsync(last.id)

        then:
        def found = findFut.get(10, java.util.concurrent.TimeUnit.SECONDS)
        found != null
        found.size() == 1
        found.get(0).embedding.toFloatArray().toList() == [10f, 11f, 12f]

        when:
        FloatVector vec2 = Vector.of([13f, 14f, 15f] as float[])
        java.util.concurrent.Future<Integer> updFut = vectorRepository.updateAsync(last.id, vec2)

        then:
        updFut.get(10, java.util.concurrent.TimeUnit.SECONDS) != null
        with(vectorRepository.findById(last.id)) {
            it.isPresent()
            it.get().embedding.toFloatArray().toList() == [13f, 14f, 15f]
        }
    }

    void "test paging over VectorFloatDoc"() {
        given:
        FloatVector v1 = Vector.of([1f, 2f, 3f] as float[])
        FloatVector v2 = Vector.of([4f, 5f, 6f] as float[])
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

    void "test derived vector near and within search results"() {
        given:
        vectorRepository.deleteAll()
        vectorRepository.save(new VectorFloatDoc(embedding: Vector.of([1f, 0f, 0f] as float[])))
        vectorRepository.save(new VectorFloatDoc(embedding: Vector.of([0f, 1f, 0f] as float[])))

        when:
        SearchResults<VectorFloatDoc> nearResults = vectorRepository.searchByEmbeddingNear(Vector.of([1f, 0f, 0f] as float[]), 2d)

        then:
        nearResults != null
        nearResults.results().size() >= 1
        nearResults.results().get(0).score().value() <= 2d

        when:
        SearchResults<VectorFloatDoc> withinResults = vectorRepository.searchByEmbeddingWithin(Vector.of([1f, 0f, 0f] as float[]), 0d, 0.2d)

        then:
        withinResults != null
        withinResults.results() != null

        when:
        SearchResults<VectorFloatDoc> betweenResults = vectorRepository.searchByEmbeddingBetween(Vector.of([1f, 0f, 0f] as float[]), 0d, 0.2d)

        then:
        betweenResults != null
        betweenResults.results() != null
        betweenResults.results().every { it.score().value() >= 0d && it.score().value() <= 0.2d }
    }

    void "test derived vector search with Score and Similarity wrappers over 10 vectors"() {
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
        def nearByDouble = vectorRepository.searchByEmbeddingNear(q, 2d)
        def nearByScore = vectorRepository.searchByEmbeddingNear(q, new Score(2d))
        def withinBySimilarity = vectorRepository.searchByEmbeddingWithin(q, new Similarity(0d), new Similarity(2d))
        def betweenByScore = vectorRepository.searchByEmbeddingBetween(q, new Score(0d), new Score(2d))

        then:
        nearByDouble.results().size() >= 8
        nearByScore.results().size() == nearByDouble.results().size()
        withinBySimilarity.results().size() == nearByDouble.results().size()
        betweenByScore.results().size() == nearByDouble.results().size()
        nearByScore.results().collect { it.entity().id } == nearByDouble.results().collect { it.entity().id }

        when:
        def cosineOk = vectorRepository.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.COSINE)
        def euclideanOk = vectorRepository.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.L2_EUCLIDEAN)
        def dotOk = vectorRepository.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.DOT)

        then:
        cosineOk.results().size() == nearByDouble.results().size()
        euclideanOk.results().size() >= 1
        dotOk.results().size() >= 1
        assertScoringResults(cosineOk, ScoringFunction.COSINE)
        assertScoringResults(euclideanOk, ScoringFunction.L2_EUCLIDEAN)
        assertScoringResults(dotOk, ScoringFunction.DOT)

        when:
        vectorRepository.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.L1_MANHATTAN)

        then:
        def manhattanEx = thrown(IllegalArgumentException)
        manhattanEx.message.contains("not supported")
        manhattanEx.message.contains("POSTGRES")
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
    @Column(length = 3)
    FloatVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    FloatVector getEmbedding() { return embedding }
    void setEmbedding(FloatVector embedding) { this.embedding = embedding }
}

@JdbcRepository(dialect = Dialect.POSTGRES)
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

    @Query("INSERT INTO vector_float_doc(embedding) VALUES (:vec)")
    java.util.concurrent.Future<Integer> saveAsync(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_float_doc(embedding) VALUES (:vec)")
    java.util.concurrent.Future<Integer> saveAsync(@Parameter("vec") FloatVector vec)

    @Query("SELECT * FROM vector_float_doc WHERE id = :id")
    java.util.concurrent.Future<java.util.List<VectorFloatDoc>> findAsync(Long id)

    SearchResults<VectorFloatDoc> searchByEmbeddingNear(Vector vector, Double maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingNear(Vector vector, Score maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingWithin(Vector vector, Double minDistance, Double maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingWithin(Vector vector, Similarity minDistance, Similarity maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingBetween(Vector vector, Double minDistance, Double maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingBetween(Vector vector, Score minDistance, Score maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingNear(Vector vector,
                                                        Score maxDistance,
                                                        ScoringFunction function)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    java.util.concurrent.Future<Integer> updateAsync(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    java.util.concurrent.Future<Integer> updateAsync(Long id, @Parameter("vec") FloatVector vec)
}
