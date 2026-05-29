package io.micronaut.data.jdbc.oraclexe.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.search.Score
import io.micronaut.data.model.vector.search.ScoringFunction
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.model.vector.search.Similarity
import io.micronaut.data.runtime.operations.internal.sql.SimilarityNormalizer
import io.micronaut.data.repository.PageableRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.Statement
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class OracleJdbcFloatVectorEntitySpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    VectorFloatDocRepository vectorRepository = context.getBean(VectorFloatDocRepository)

    @Shared
    DataSource dataSource = context.getBean(DataSource)

    @Override
    List<String> packages() {
        // Ensure entity/repository in this package are scanned
        return [getClass().package.name]
    }

    void "test save, find and update single entity (using custom queries with io.micronaut.data.model.Vector)"() {
        given:
        float[] dv = [1f, 2.5f, -3.75f] as float[]
        FloatVector v1 = Vector.of(dv)

        when: "save via custom @Query using Vector parameter"
        vectorRepository.saveCustom(v1)
        def list = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))
        def e = list.find { it.embedding.toFloatArray().toList() == dv.toList() }

        then: "entity persisted and read conversion to Micronaut Vector works"
        e != null
        e.id != null
        e.embedding != null
        e.embedding.type == Float.TYPE
        e.embedding.toFloatArray().toList() == dv.toList()

        when: "update via custom @Query to a new vector"
        float [] dv2 = [3f, 0.0f, 7.25f] as float[]
        FloatVector v2 = Vector.of(dv2)
        vectorRepository.updateCustom(e.id, v2)
        def updated = vectorRepository.findById(e.id).orElse(null)

        then:
        updated != null
        updated.embedding.type == Float.TYPE
        updated.embedding.toFloatArray().toList() == dv2.toList()
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
        Future<Integer> saveFut = vectorRepository.saveAsync(vec)

        then:
        saveFut.get(10, TimeUnit.SECONDS) == 1

        when:
        def all = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))
        def last = all.last()
        Future<List<VectorFloatDoc>> findFut = vectorRepository.findAsync(last.id)

        then:
        def found = findFut.get(10, TimeUnit.SECONDS)
        found != null
        found.size() == 1
        found.get(0).embedding.toFloatArray().toList() == [10f, 11f, 12f]

        when:
        FloatVector vec2 = Vector.of([13f, 14f, 15f] as float[])
        Future<Integer> updFut = vectorRepository.updateAsync(last.id, vec2)

        then:
        updFut.get(10, TimeUnit.SECONDS) != null
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

    void "test derived vector search results are empty not null"() {
        given:
        vectorRepository.deleteAll()

        when:
        SearchResults<VectorFloatDoc> emptyResults = vectorRepository.searchByEmbeddingNear(Vector.of([1f, 0f, 0f] as float[]), 2d)

        then:
        emptyResults != null
        emptyResults.results().isEmpty()
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
        Set<Long> allIds = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))*.id as Set<Long>
        FloatVector q = Vector.of([1f, 0f, 0f] as float[])

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
        def euclideanSquaredOk = vectorRepository.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.L2_EUCLIDEAN_SQUARED)
        def dotOk = vectorRepository.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.DOT)
        def manhattanOk = vectorRepository.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.L1_MANHATTAN)

        then:
        cosineOk.results().size() == nearByDouble.results().size()
        euclideanOk.results().size() >= 1
        euclideanSquaredOk.results().size() >= 1
        dotOk.results().size() >= 1
        manhattanOk.results().size() >= 1
        assertScoringResults(cosineOk, ScoringFunction.COSINE)
        assertScoringResults(euclideanOk, ScoringFunction.L2_EUCLIDEAN)
        assertScoringResults(euclideanSquaredOk, ScoringFunction.L2_EUCLIDEAN_SQUARED)
        assertScoringResults(dotOk, ScoringFunction.DOT)
        assertScoringResults(manhattanOk, ScoringFunction.L1_MANHATTAN)
    }

    void "test Oracle L2 euclidean squared search over 15 vectors returns expected scores and normalized similarity"() {
        given:
        vectorRepository.deleteAll()
        def vectors = [
            [1.0f, 0.0f, 0.0f],
            [0.95f, 0.05f, 0.0f],
            [0.90f, 0.10f, 0.0f],
            [0.85f, 0.15f, 0.0f],
            [0.80f, 0.20f, 0.0f],
            [0.75f, 0.25f, 0.0f],
            [0.70f, 0.30f, 0.0f],
            [0.65f, 0.35f, 0.0f],
            [0.60f, 0.40f, 0.0f],
            [0.55f, 0.45f, 0.0f],
            [0.50f, 0.50f, 0.0f],
            [0.40f, 0.60f, 0.0f],
            [0.30f, 0.70f, 0.0f],
            [0.20f, 0.80f, 0.0f],
            [0.0f, 1.0f, 0.0f]
        ]
        vectors.each { v -> vectorRepository.save(new VectorFloatDoc(embedding: Vector.of(v as float[]))) }
        FloatVector query = Vector.of([1.0f, 0.0f, 0.0f] as float[])
        def queryValues = query.toFloatArray()
        def expectedOrder = vectors.toList().sort { left, right ->
            squaredEuclideanDistance(queryValues, left as float[]) <=> squaredEuclideanDistance(queryValues, right as float[])
        }
        def expectedByVector = expectedOrder.collectEntries { vector ->
            [(vector.toList()): squaredEuclideanDistance(queryValues, vector as float[])]
        }

        when:
        SearchResults<VectorFloatDoc> results = vectorRepository.searchByEmbeddingNear(query, new Score(2d), ScoringFunction.L2_EUCLIDEAN_SQUARED)

        then:
        results != null
        results.results() != null
        results.results().size() >= 1
        def expectedEmbeddings = expectedOrder.collect { it.toList() } as Set
        def resultEmbeddings = results.results()*.entity()*.embedding*.toFloatArray()*.toList() as Set
        expectedEmbeddings.containsAll(resultEmbeddings)
        results.results().every { it.similarity() != null }
        results.results().every { result ->
            def embedding = result.entity().embedding.toFloatArray().toList()
            Math.abs(result.score().value() - expectedByVector.get(embedding)) < 1.0e-6d
        }
        assertScoringResults(results, ScoringFunction.L2_EUCLIDEAN_SQUARED)

        and:
        def exactMatch = results.results().find { it.entity().embedding.toFloatArray().toList() == queryValues.toList() }
        exactMatch != null
        Math.abs(exactMatch.score().value()) < 1.0e-9d
        Math.abs(exactMatch.similarity().value() - 1.0d) < 1.0e-9d
    }

    void "test derived top+order vector queries for near and within"() {
        given:
        vectorRepository.deleteAll()
        def vectors = [
            [1f, 0f, 0f],
            [0.9f, 0.1f, 0f],
            [0.8f, 0.2f, 0f],
            [0.7f, 0.3f, 0f],
            [0.6f, 0.4f, 0f]
        ]
        vectors.each { v -> vectorRepository.save(new VectorFloatDoc(embedding: Vector.of(v as float[]))) }
        def allIds = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))*.id as Set
        FloatVector q = Vector.of([1f, 0f, 0f] as float[])

        when:
        List<VectorFloatDoc> nearTop2IdDesc = vectorRepository.findTop2ByEmbeddingNearOrderByIdDesc(q, 1_000_000d)
        SearchResults<VectorFloatDoc> nearTop2IdDescResults = vectorRepository.searchTop2ByEmbeddingNearOrderByIdDesc(q, 1_000_000d)
        List<VectorFloatDoc> withinTop2IdAsc = vectorRepository.findTop2ByEmbeddingWithinOrderByIdAsc(q, 0d, 1_000_000d)
        SearchResults<VectorFloatDoc> withinTop2IdAscResults = vectorRepository.searchTop2ByEmbeddingWithinOrderByIdAsc(q, 0d, 1_000_000d)

        then:
        nearTop2IdDesc.size() == 2
        nearTop2IdDesc[0].id > nearTop2IdDesc[1].id
        allIds.containsAll(nearTop2IdDesc*.id as Set)
        nearTop2IdDesc.every { it instanceof VectorFloatDoc }
        nearTop2IdDesc[0].metaClass.respondsTo(nearTop2IdDesc[0], "getScore").isEmpty()

        nearTop2IdDescResults.results().size() >= 1
        allIds.containsAll(nearTop2IdDescResults.results()*.entity()*.id as Set)
        nearTop2IdDescResults.results().every { it.score() != null }

        withinTop2IdAsc.size() == 2
        withinTop2IdAsc[0].id < withinTop2IdAsc[1].id
        allIds.containsAll(withinTop2IdAsc*.id as Set)
        withinTop2IdAsc.every { it instanceof VectorFloatDoc }
        withinTop2IdAsc[0].metaClass.respondsTo(withinTop2IdAsc[0], "getScore").isEmpty()

        withinTop2IdAscResults.results().size() >= 1
        allIds.containsAll(withinTop2IdAscResults.results()*.entity()*.id as Set)
        withinTop2IdAscResults.results().every { it.score() != null }
    }



    private void executeSilently(String sql) {
        Connection c = null
        Statement st = null
        try {
            c = dataSource.getConnection()
            st = c.createStatement()
            st.execute(sql)
        } catch (Throwable ignored) {
            // ignore if already exists or unsupported in current XE version
        } finally {
            try { st?.close() } catch (ignored) {}
            try { c?.close() } catch (ignored) {}
        }
    }

    private static double squaredEuclideanDistance(float[] left, float[] right) {
        double sum = 0d
        for (int i = 0; i < left.length; i++) {
            double diff = left[i] - right[i]
            sum += diff * diff
        }
        return sum
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
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE, ref = "VECTOR_DOC_SEQ")
    Long id
    FloatVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    FloatVector getEmbedding() { return embedding }
    void setEmbedding(FloatVector embedding) { this.embedding = embedding }
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface VectorFloatDocRepository extends PageableRepository<VectorFloatDoc, Long> {

    @Query("INSERT INTO vector_float_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_float_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") FloatVector vec)

    @Query("SELECT * FROM vector_float_doc WHERE id = :id")
    Optional<VectorFloatDoc> findById(Long id)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") FloatVector vec)

    @Query("INSERT INTO vector_float_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    Future<Integer> saveAsync(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_float_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    Future<Integer> saveAsync(@Parameter("vec") FloatVector vec)

    @Query("SELECT * FROM vector_float_doc WHERE id = :id")
    Future<List<VectorFloatDoc>> findAsync(Long id)

    SearchResults<VectorFloatDoc> searchByEmbeddingNear(Vector vector, Double maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingNear(FloatVector vector, Double maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingNear(Vector vector, Score maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingWithin(Vector vector, Double minDistance, Double maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingWithin(Vector vector, Similarity minDistance, Similarity maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingBetween(Vector vector, Double minDistance, Double maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingBetween(Vector vector, Score minDistance, Score maxDistance)

    SearchResults<VectorFloatDoc> searchByEmbeddingNear(Vector vector,
                                                          Score maxDistance,
                                                          ScoringFunction function)

    List<VectorFloatDoc> findTop2ByEmbeddingNearOrderByIdDesc(Vector vector, Double maxDistance)

    SearchResults<VectorFloatDoc> searchTop2ByEmbeddingNearOrderByIdDesc(Vector vector, Double maxDistance)

    List<VectorFloatDoc> findTop2ByEmbeddingWithinOrderByIdAsc(Vector vector, Double minDistance, Double maxDistance)

    SearchResults<VectorFloatDoc> searchTop2ByEmbeddingWithinOrderByIdAsc(Vector vector, Double minDistance, Double maxDistance)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    Future<Integer> updateAsync(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    Future<Integer> updateAsync(Long id, @Parameter("vec") FloatVector vec)

}
