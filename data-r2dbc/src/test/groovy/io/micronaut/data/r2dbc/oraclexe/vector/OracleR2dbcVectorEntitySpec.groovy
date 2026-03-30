package io.micronaut.data.r2dbc.oraclexe.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.VectorShape
import io.micronaut.data.annotation.VectorStorage
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.vector.DoubleVector;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.ByteVector;
import io.micronaut.data.model.vector.search.Score
import io.micronaut.data.model.vector.search.ScoringFunction
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.model.vector.search.Similarity
import io.micronaut.data.model.vector.search.SimilarityNormalizer
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.r2dbc.oraclexe.OracleXETestPropertyProvider
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * R2DBC specs for Oracle VECTOR support covering FloatVector, IntVector and ByteVector.
 * Mirrors the JDBC vector entity specs.
 */
class OracleR2dbcVectorEntitySpec extends Specification implements OracleXETestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties + ["spec.name": "OracleR2dbcVectorEntitySpec"])

    @Shared
    ConnectionFactory connectionFactory = context.getBean(ConnectionFactory)

    @Shared
    VectorFloatDocRepository vectorFloatDocRepository = context.getBean(VectorFloatDocRepository)

    @Shared
    VectorByteDocRepository vectorByteDocRepository = context.getBean(VectorByteDocRepository)

    @Shared
    VectorDoubleDocRepository vectorDoubleDocRepository = context.getBean(VectorDoubleDocRepository)

    @Shared
    SparseVectorByteDocRepository sparseVectorByteDocRepository = context.getBean(SparseVectorByteDocRepository)

    // FLOAT64/default
    void "R2DBC DoubleVector - default CRUD and custom @Query"() {
        given:
        def repo = vectorDoubleDocRepository
        double[] dv = [1d, 2.5d, -3.75d] as double[]
        DoubleVector v1 = Vector.of(dv)

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
        DoubleVector v2 = Vector.of(dv2)
        fetched.embedding = v2
        def updated = repo.update(fetched)

        then:
        updated != null
        updated.embedding.type == Double.TYPE
        updated.embedding.toDoubleArray().toList() == dv2.toList()

        when: "custom @Query insert and update"
        double[] dvx = [2d, 4d, 6d] as double[]
        DoubleVector vx = Vector.of(dvx)
        repo.saveCustom(vx)
        def all = repo.findAll()
        def e = all.find { it.embedding?.toDoubleArray()?.toList() == dvx.toList() }

        then:
        e != null
        e.id != null

        when:
        double[] dvy = [-1d, 0.5d, 10d] as double[]
        DoubleVector vy = Vector.of(dvy)
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

    // INT8 -> ByteVector (maps to byte[])
    void "R2DBC ByteVector - default CRUD and custom @Query"() {
        given:
        def repo = vectorByteDocRepository
        ByteVector v1 = Vector.of([1, 2, -3] as byte[])

        when:
        def saved = repo.save(new VectorByteDoc(embedding: v1))

        then:
        saved?.id != null

        when:
        def fetched = repo.findById(saved.id).orElse(null)

        then:
        fetched != null
        fetched.embedding.type == Byte.TYPE
        fetched.embedding.toByteArray().toList() == [1, 2, -3]

        when:
        ByteVector v2 = Vector.of([3, 0, 7] as byte[])
        fetched.embedding = v2
        def updated = repo.update(fetched)

        then:
        updated != null
        updated.embedding.type == Byte.TYPE
        updated.embedding.toByteArray().toList() == [3, 0, 7]

        when: "custom @Query insert and update"
        ByteVector vx = Vector.of([10, 11, 12] as byte[])
        repo.saveCustom(vx)
        def all = repo.findAll()
        def e = all.find { it.embedding?.toByteArray()?.toList() == [10, 11, 12] }

        then:
        e != null

        when:
        ByteVector vy = Vector.of([13, 14, 15] as byte[])
        repo.updateCustom(e.id, vy)
        def after = repo.findById(e.id).orElse(null)

        then:
        after != null
        after.embedding.toByteArray().toList() == [13, 14, 15]
    }

    void "R2DBC Oracle FloatVector - derived search with wrappers and scoring function"() {
        given:
        def repo = vectorFloatDocRepository
        repo.deleteAll()
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
        vectors.each { v -> repo.save(new VectorFloatDoc(embedding: Vector.of(v as float[]))) }
        FloatVector q = Vector.of([1f, 0f, 0f] as float[])

        when:
        SearchResults<VectorFloatDoc> nearByDouble = repo.searchByEmbeddingNear((Vector) q, 2d)
        SearchResults<VectorFloatDoc> nearByFloatVector = repo.searchByEmbeddingNear(q, 2d)
        SearchResults<VectorFloatDoc> nearByScore = repo.searchByEmbeddingNear(q, new Score(2d))
        SearchResults<VectorFloatDoc> withinBySimilarity = repo.searchByEmbeddingWithin(q, new Similarity(0d), new Similarity(2d))
        SearchResults<VectorFloatDoc> betweenByScore = repo.searchByEmbeddingBetween(q, new Score(0d), new Score(2d))

        then:
        nearByDouble != null
        nearByDouble.results() != null
        nearByDouble.results().size() == vectors.size()
        nearByFloatVector.results().size() == nearByDouble.results().size()
        nearByScore.results().size() == nearByDouble.results().size()
        withinBySimilarity.results().size() == nearByDouble.results().size()
        betweenByScore.results().size() == nearByDouble.results().size()

        when:
        def cosineOk = repo.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.COSINE)
        def euclideanOk = repo.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.L2_EUCLIDEAN)
        def euclideanSquaredOk = repo.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.L2_EUCLIDEAN_SQUARED)
        def dotOk = repo.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.DOT)
        def manhattanOk = repo.searchByEmbeddingNear(q, new Score(2d), ScoringFunction.L1_MANHATTAN)

        then:
        cosineOk.results().size() == nearByDouble.results().size()
        euclideanOk.results().size() > 0
        euclideanSquaredOk.results().size() > 0
        dotOk.results().size() > 0
        manhattanOk.results().size() > 0
        assertScoringResults(cosineOk, ScoringFunction.COSINE)
        assertScoringResults(euclideanOk, ScoringFunction.L2_EUCLIDEAN)
        assertScoringResults(euclideanSquaredOk, ScoringFunction.L2_EUCLIDEAN_SQUARED)
        assertScoringResults(dotOk, ScoringFunction.DOT)
        assertScoringResults(manhattanOk, ScoringFunction.L1_MANHATTAN)
    }

    void "R2DBC Oracle L2 euclidean squared search over 15 vectors returns expected ordering and normalized similarity"() {
        given:
        def repo = vectorFloatDocRepository
        repo.deleteAll()
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
        vectors.each { v -> repo.save(new VectorFloatDoc(embedding: Vector.of(v as float[]))) }
        FloatVector query = Vector.of([1.0f, 0.0f, 0.0f] as float[])
        def queryValues = query.toFloatArray()
        def expectedOrder = vectors.toList().sort { left, right ->
            squaredEuclideanDistance(queryValues, left as float[]) <=> squaredEuclideanDistance(queryValues, right as float[])
        }
        def expectedByVector = expectedOrder.collectEntries { vector ->
            [(vector.toList()): squaredEuclideanDistance(queryValues, vector as float[])]
        }

        when:
        SearchResults<VectorFloatDoc> results = repo.searchByEmbeddingNear(query, new Score(2d), ScoringFunction.L2_EUCLIDEAN_SQUARED)

        then:
        results != null
        results.results() != null
        results.results().size() == expectedOrder.size()
        results.results()*.entity()*.embedding*.toFloatArray()*.toList() as Set == expectedOrder.collect { it.toList() } as Set
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

    private static double squaredEuclideanDistance(float[] left, float[] right) {
        double sum = 0d
        for (int i = 0; i < left.length; i++) {
            double diff = left[i] - right[i]
            sum += diff * diff
        }
        return sum
    }

    void "R2DBC Oracle FloatVector - derived top+order vector queries"() {
        given:
        def repo = vectorFloatDocRepository
        repo.deleteAll()
        def vectors = [
            [1f, 0f, 0f],
            [0.9f, 0.1f, 0f],
            [0.8f, 0.2f, 0f],
            [0.7f, 0.3f, 0f],
            [0.6f, 0.4f, 0f]
        ]
        vectors.each { v -> repo.save(new VectorFloatDoc(embedding: Vector.of(v as float[]))) }
        FloatVector q = Vector.of([1f, 0f, 0f] as float[])

        when:
        List<VectorFloatDoc> nearTop2IdDesc = repo.findTop2ByEmbeddingNearOrderByIdDesc(q, 1_000_000d)
        SearchResults<VectorFloatDoc> nearTop2IdDescResults = repo.searchTop2ByEmbeddingNearOrderByIdDesc(q, 1_000_000d)
        List<VectorFloatDoc> withinTop2IdAsc = repo.findTop2ByEmbeddingWithinOrderByIdAsc(q, 0d, 1_000_000d)
        SearchResults<VectorFloatDoc> withinTop2IdAscResults = repo.searchTop2ByEmbeddingWithinOrderByIdAsc(q, 0d, 1_000_000d)

        then:
        nearTop2IdDesc.size() == 2
        nearTop2IdDesc[0].id > nearTop2IdDesc[1].id
        nearTop2IdDescResults.results().size() == 2
        withinTop2IdAsc.size() == 2
        withinTop2IdAsc[0].id < withinTop2IdAsc[1].id
        withinTop2IdAscResults.results().size() == 2
    }

    void "R2DBC Oracle Sparse ByteVector - typed CRUD and derived near search"() {
        given:
        def repo = sparseVectorByteDocRepository
        repo.deleteAll()
        ByteVector v1 = Vector.of([0, 10, 0, 20, 0] as byte[])
        ByteVector v2 = Vector.of([0, 8, 0, 18, 0] as byte[])

        when:
        def saved1 = repo.save(new SparseVectorByteDoc(embedding: v1))
        def saved2 = repo.save(new SparseVectorByteDoc(embedding: v2))
        def all = repo.findAll()

        then:
        saved1.id != null
        saved2.id != null
        all.size() >= 2
        def expected = ([0, 10, 0, 20, 0] as byte[]).toList()
        def embeddings = all.collect { it.embedding.toByteArray().toList() }
        embeddings.contains(expected)

        when:
        def matched = repo.searchByEmbeddingNear((Vector) v1, 100d)
        def matchedByByteVector = repo.searchByEmbeddingNear(v1, 100d)

        then:
        matched != null
        matched.results().size() == 2
        matchedByByteVector.results().size() == matched.results().size()
    }

    private void executeSilently(String sql) {
        try {
            Mono.from(connectionFactory.create())
                .flatMapMany { Connection c ->
                    Flux.from(c.createStatement(sql).execute())
                        .flatMap { r -> r.getRowsUpdated() }
                        .onErrorResume { t -> Mono.empty() }
                        .concatWith(Mono.from(c.close()))
                }
                .collectList()
                .block()
        } catch (Throwable ignored) {
            // ignore if statement is unsupported or already exists
        }
    }
}


@MappedEntity("vector_double_doc")
class VectorDoubleDoc {
    @Id
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE, ref = "VECTOR_DOC_SEQ")
    Long id
    DoubleVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    DoubleVector getEmbedding() { return embedding }
    void setEmbedding(DoubleVector embedding) { this.embedding = embedding }
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

@MappedEntity("vector_byte_doc")
class VectorByteDoc {
    @Id
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE, ref = "VECTOR_DOC_SEQ")
    Long id
    ByteVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    ByteVector getEmbedding() { return embedding }
    void setEmbedding(ByteVector embedding) { this.embedding = embedding }
}

// Repositories (R2DBC, Oracle)

@Requires(property = "spec.name", value = "OracleR2dbcVectorEntitySpec")
@R2dbcRepository(dialect = Dialect.ORACLE)
interface VectorFloatDocRepository extends CrudRepository<VectorFloatDoc, Long> {

    @Query("INSERT INTO vector_float_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_float_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") FloatVector vec)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_float_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") FloatVector vec)

    @Query("SELECT * FROM vector_float_doc")
    List<VectorFloatDoc> findAll()

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
}

@Requires(property = "spec.name", value = "OracleR2dbcVectorEntitySpec")
@R2dbcRepository(dialect = Dialect.ORACLE)
interface VectorByteDocRepository extends CrudRepository<VectorByteDoc, Long> {

    @Query("INSERT INTO vector_byte_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_byte_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") ByteVector vec)

    @Query("UPDATE vector_byte_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_byte_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") ByteVector vec)

    @Query("SELECT * FROM vector_byte_doc")
    List<VectorByteDoc> findAll()
}

@Requires(property = "spec.name", value = "OracleR2dbcVectorEntitySpec")
@R2dbcRepository(dialect = Dialect.ORACLE)
interface VectorDoubleDocRepository extends CrudRepository<VectorDoubleDoc, Long> {

    @Query("INSERT INTO vector_double_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_double_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") DoubleVector vec)

    @Query("UPDATE vector_double_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_double_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") DoubleVector vec)

    @Query("SELECT * FROM vector_double_doc")
    List<VectorDoubleDoc> findAll()
}

@MappedEntity("vector_sparse_byte_doc")
class SparseVectorByteDoc {
    @Id
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE, ref = "VECTOR_DOC_SEQ")
    Long id

    @VectorStorage(length = 5, shape = VectorShape.SPARSE)
    ByteVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    ByteVector getEmbedding() { return embedding }
    void setEmbedding(ByteVector embedding) { this.embedding = embedding }
}

@Requires(property = "spec.name", value = "OracleR2dbcVectorEntitySpec")
@R2dbcRepository(dialect = Dialect.ORACLE)
interface SparseVectorByteDocRepository extends CrudRepository<SparseVectorByteDoc, Long> {
    @Query("SELECT * FROM vector_sparse_byte_doc")
    List<SparseVectorByteDoc> findAll()

    SearchResults<SparseVectorByteDoc> searchByEmbeddingNear(Vector vector, Double maxDistance)

    SearchResults<SparseVectorByteDoc> searchByEmbeddingNear(ByteVector vector, Double maxDistance)
}
