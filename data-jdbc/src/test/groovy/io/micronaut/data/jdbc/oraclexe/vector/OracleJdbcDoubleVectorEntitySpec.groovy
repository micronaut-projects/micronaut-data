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
import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.repository.PageableRepository
import jakarta.persistence.Column
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.Statement
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class OracleJdbcDoubleVectorEntitySpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    VectorDoubleDocRepository vectorRepository = context.getBean(VectorDoubleDocRepository)

    @Shared
    DataSource dataSource = context.getBean(DataSource)

    @Override
    List<String> packages() {
        // Ensure entity/repository in this package are scanned
        return [getClass().package.name]
    }

    void "test save, find and update single entity (using custom queries with io.micronaut.data.model.Vector)"() {
        given:
        double[] dv = [1d, 2.5d, -3.75d] as double[]
        DoubleVector v1 = Vector.of(dv)

        when: "save via custom @Query using Vector parameter"
        vectorRepository.saveCustom(v1)
        def list = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))
        def e = list.find { it.embedding.toDoubleArray().toList() == dv.toList() }

        then: "entity persisted and read conversion to Micronaut Vector works"
        e != null
        e.id != null
        e.embedding != null
        e.embedding.type == Double.TYPE
        e.embedding.toDoubleArray().toList() == dv.toList()

        when: "update via custom @Query to a new vector"
        double[] dv2 = [3d, 0.0d, 7.25d] as double[]
        DoubleVector v2 = Vector.of(dv2)
        vectorRepository.updateCustom(e.id, v2)
        def updated = vectorRepository.findById(e.id).orElse(null)

        then:
        updated != null
        updated.embedding.type == Double.TYPE
        updated.embedding.toDoubleArray().toList() == dv2.toList()
    }

    void "test save and update via default repository methods (no @Query)"() {
        given:
        double[] dv = [2d, -1.5d, 0.25d] as double[]
        DoubleVector v1 = Vector.of(dv)

        when: "persist entity using default repository save (auto-generated SQL, no @Query)"
        def saved = vectorRepository.save(new VectorDoubleDoc(embedding: v1))

        then:
        saved?.id != null

        when: "load back and verify embedding round-trip"
        def fetched = vectorRepository.findById(saved.id).orElse(null)

        then:
        fetched != null
        fetched.embedding.type == Double.TYPE
        fetched.embedding.toDoubleArray().toList() == dv.toList()

        when: "update entity using default repository update (auto-generated SQL, no @Query)"
        double[] dv2 = [-0.5d, 3d, 4.5d] as double[]
        DoubleVector v2 = Vector.of(dv2)
        fetched.embedding = v2
        def updated = vectorRepository.update(fetched)

        then:
        updated != null
        updated.embedding.type == Double.TYPE
        updated.embedding.toDoubleArray().toList() == dv2.toList()
    }


    void "test save, find and update multiple entities"() {
        given:
        DoubleVector vA = Vector.of([1d, 2d, 3d] as double[])
        DoubleVector vB = Vector.of([4d, 5d, 6d] as double[])

        when:
        vectorRepository.saveCustom(vA)
        vectorRepository.saveCustom(vB)
        def rows = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))

        then:
        def idA = rows.find { it.embedding.toDoubleArray().toList() == [1d, 2d, 3d] }?.id
        def idB = rows.find { it.embedding.toDoubleArray().toList() == [4d, 5d, 6d] }?.id
        idA != null
        idB != null
        idA != idB

        when: "update both"
        DoubleVector vA2 = Vector.of([7d, 8d, 9d] as double[])
        DoubleVector vB2 = Vector.of([0d, -1d, -2d] as double[])
        vectorRepository.updateCustom(idA, vA2)
        vectorRepository.updateCustom(idB, vB2)
        def rows2 = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))

        then:
        def updatedA = rows2.find { it.id == idA }?.embedding?.toDoubleArray()?.toList()
        def updatedB = rows2.find { it.id == idB }?.embedding?.toDoubleArray()?.toList()
        updatedA == [7d, 8d, 9d]
        updatedB == [0d, -1d, -2d]
    }

    void "test custom and async queries"() {
        given:
        DoubleVector vec = Vector.of([10d, 11d, 12d] as double[])

        when:
        Future<Integer> saveFut = vectorRepository.saveAsync(vec)

        then:
        saveFut.get(10, TimeUnit.SECONDS) == 1

        when:
        def all = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))
        def last = all.last()
        Future<List<VectorDoubleDoc>> findFut = vectorRepository.findAsync(last.id)

        then:
        def found = findFut.get(10, TimeUnit.SECONDS)
        found != null
        found.size() == 1
        found.get(0).embedding.toDoubleArray().toList() == [10d, 11d, 12d]

        when:
        DoubleVector vec2 = Vector.of([13d, 14d, 15d] as double[])
        Future<Integer> updFut = vectorRepository.updateAsync(last.id, vec2)

        then:
        updFut.get(10, TimeUnit.SECONDS) != null
        with(vectorRepository.findById(last.id)) {
            it.isPresent()
            it.get().embedding.toDoubleArray().toList() == [13d, 14d, 15d]
        }
    }

    void "test paging over VectorDoubleDoc"() {
        given:
        DoubleVector v1 = Vector.of([1d, 2d, 3d] as double[])
        DoubleVector v2 = Vector.of([4d, 5d, 6d] as double[])
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
        vectorRepository.save(new VectorDoubleDoc(embedding: Vector.of([1d, 0d, 0d] as double[])))
        vectorRepository.save(new VectorDoubleDoc(embedding: Vector.of([0d, 1d, 0d] as double[])))

        when:
        SearchResults<VectorDoubleDoc> nearResults = vectorRepository.searchByEmbeddingNear(Vector.of([1d, 0d, 0d] as double[]), 2d)

        then:
        nearResults != null
        nearResults.results().size() >= 1
        nearResults.results().get(0).score().value() <= 2d

        when:
        SearchResults<VectorDoubleDoc> withinResults = vectorRepository.searchByEmbeddingWithin(Vector.of([1d, 0d, 0d] as double[]), 0d, 0.2d)

        then:
        withinResults != null
        withinResults.results() != null

        when:
        SearchResults<VectorDoubleDoc> betweenResults = vectorRepository.searchByEmbeddingBetween(Vector.of([1d, 0d, 0d] as double[]), 0d, 0.2d)

        then:
        betweenResults != null
        betweenResults.results() != null
        betweenResults.results().every { it.score().value() >= 0d && it.score().value() <= 0.2d }
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
}

@MappedEntity("vector_double_doc")
class VectorDoubleDoc {
    @Id
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE, ref = "VECTOR_DOC_SEQ")
    Long id
    @Column(length=3)
    DoubleVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    DoubleVector getEmbedding() { return embedding }
    void setEmbedding(DoubleVector embedding) { this.embedding = embedding }
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface VectorDoubleDocRepository extends PageableRepository<VectorDoubleDoc, Long> {

    @Query("INSERT INTO vector_double_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_double_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") DoubleVector vec)

    @Query("SELECT * FROM vector_double_doc WHERE id = :id")
    Optional<VectorDoubleDoc> findById(Long id)

    @Query("UPDATE vector_double_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_double_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") DoubleVector vec)

    @Query("INSERT INTO vector_double_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    Future<Integer> saveAsync(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_double_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    Future<Integer> saveAsync(@Parameter("vec") DoubleVector vec)

    @Query("SELECT * FROM vector_double_doc WHERE id = :id")
    Future<List<VectorDoubleDoc>> findAsync(Long id)

    SearchResults<VectorDoubleDoc> searchByEmbeddingNear(Vector vector, Double maxDistance)

    SearchResults<VectorDoubleDoc> searchByEmbeddingWithin(Vector vector, Double minDistance, Double maxDistance)

    SearchResults<VectorDoubleDoc> searchByEmbeddingBetween(Vector vector, Double minDistance, Double maxDistance)

    @Query("UPDATE vector_double_doc SET embedding = :vec WHERE id = :id")
    Future<Integer> updateAsync(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_double_doc SET embedding = :vec WHERE id = :id")
    Future<Integer> updateAsync(Long id, @Parameter("vec") DoubleVector vec)

}
