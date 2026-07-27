package io.micronaut.data.jdbc.oraclexe.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.VectorShape
import io.micronaut.data.annotation.VectorStorage
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import io.micronaut.data.model.Sort
import io.micronaut.data.model.vector.DoubleVector
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.SparseDoubleVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.PageableRepository
import io.micronaut.transaction.SynchronousTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.Statement
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class OracleJdbcVectorEntitySpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    VectorDocRepository vectorRepository = context.getBean(VectorDocRepository)

    @Shared
    SparseVectorDocRepository sparseVectorRepository = context.getBean(SparseVectorDocRepository)

    @Shared
    GenericVectorSearchRepository genericVectorSearchRepository = context.getBean(GenericVectorSearchRepository)

    @Shared
    DataSource dataSource = context.getBean(DataSource)

    @Override
    List<String> packages() {
        // Ensure entity/repository in this package are scanned
        return [getClass().package.name]
    }

    def cleanup() {
        // Clean table between tests
        executeSilently "DELETE FROM vector_doc"
        executeSilently "DELETE FROM vector_sparse_doc"
        executeSilently "DELETE FROM generic_vector_search_doc"
        // no-op transaction boundary to flush
        context.getBean(SynchronousTransactionManager).executeWrite { status -> null }
    }

    void "dense vector value is persisted through sparse entity mapping"() {
        given:
        Vector dense = Vector.of([0d, 10d, 0d, 20d, 0d] as double[])

        when:
        def saved = sparseVectorRepository.save(new SparseVectorDoc(embedding: dense))
        def fetched = sparseVectorRepository.findById(saved.id).orElse(null)

        then:
        fetched != null
        fetched.embedding.toDoubleArray().toList() == [0d, 10d, 0d, 20d, 0d]
    }

    void "sparse vector value written through dense entity mapping is materialized with zeros"() {
        given:
        def sparse = new SparseDoubleVector(5, [1, 3] as int[], [10d, 20d] as double[])

        when:
        vectorRepository.saveCustom(sparse)
        def all = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))
        def matched = all.find { it.embedding.toDoubleArray().toList() == [0d, 10d, 0d, 20d, 0d] }

        then:
        matched != null
    }

    void "test save, find and update single entity (using custom queries with io.micronaut.data.model.Vector)"() {
        given:
        double[] dv = [1d, 2.5d, -3.75d] as double[]
        Vector v1 = Vector.of(dv)

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
        Vector v2 = Vector.of(dv2)
        vectorRepository.updateCustom(e.id, v2)
        def updated = vectorRepository.findById(e.id).orElse(null)

        then:
        updated != null
        updated.embedding.type == Double.TYPE
        updated.embedding.toDoubleArray().toList() == dv2.toList()
    }


    void "test save, find and update multiple entities"() {
        given:
        Vector vA = Vector.of([1d, 2d, 3d] as double[])
        Vector vB = Vector.of([4d, 5d, 6d] as double[])

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
        Vector vA2 = Vector.of([7d, 8d, 9d] as double[])
        Vector vB2 = Vector.of([0d, -1d, -2d] as double[])
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
        Vector vec = Vector.of([10d, 11d, 12d] as double[])

        when:
        Future<Integer> saveFut = vectorRepository.saveAsync(vec)

        then:
        saveFut.get(10, TimeUnit.SECONDS) == 1

        when:
        def all = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))
        def last = all.last()
        Future<List<VectorDoc>> findFut = vectorRepository.findAsync(last.id)

        then:
        def found = findFut.get(10, TimeUnit.SECONDS)
        found != null
        found.size() == 1
        found.get(0).embedding.toDoubleArray().toList() == [10d, 11d, 12d]

        when:
        Vector vec2 = Vector.of([13d, 14d, 15d] as double[])
        Future<Integer> updFut = vectorRepository.updateAsync(last.id, vec2)

        then:
        updFut.get(10, TimeUnit.SECONDS) != null
        with(vectorRepository.findById(last.id)) {
            it.isPresent()
            it.get().embedding.toDoubleArray().toList() == [13d, 14d, 15d]
        }
    }

    void "test similarity search converts float query to generic vector format"() {
        given:
        genericVectorSearchRepository.save(
            new GenericVectorSearchDoc(embedding: Vector.of([1d, 0d, 0d] as double[]))
        )

        when:
        def results = genericVectorSearchRepository.searchByEmbeddingNear(
            Vector.of([1f, 0f, 0f] as float[]),
            2d
        ).results()

        then:
        results.size() == 1
        results.first().entity().embedding.toDoubleArray().toList() == [1d, 0d, 0d]
    }

    void "test top2 derived queries with explicit ORDER BY"() {
        given:
        vectorRepository.saveCustom(Vector.of([1d, 0d, 0d] as double[]))
        vectorRepository.saveCustom(Vector.of([2d, 0d, 0d] as double[]))
        vectorRepository.saveCustom(Vector.of([3d, 0d, 0d] as double[]))

        when:
        def top2Desc = vectorRepository.findTop2OrderByIdDesc()
        def top2Asc = vectorRepository.findTop2OrderByIdAsc()

        then:
        top2Desc.size() == 2
        top2Desc[0].id > top2Desc[1].id
        top2Asc.size() == 2
        top2Asc[0].id < top2Asc[1].id
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

@MappedEntity("vector_doc")
class VectorDoc {
    @Id
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE, ref = "VECTOR_DOC_SEQ")
    Long id
    Vector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    Vector getEmbedding() { return embedding }
    void setEmbedding(Vector embedding) { this.embedding = embedding }
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface VectorDocRepository extends PageableRepository<VectorDoc, Long> {

    @Query("INSERT INTO vector_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") DoubleVector vec)

    @Query("SELECT * FROM vector_doc WHERE id = :id")
    Optional<VectorDoubleDoc> findById(Long id)

    @Query("UPDATE vector_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    Future<Integer> saveAsync(@Parameter("vec") Vector vec)

    @Query("SELECT * FROM vector_doc WHERE id = :id")
    Future<List<VectorDoc>> findAsync(Long id)

    @Query("UPDATE vector_doc SET embedding = :vec WHERE id = :id")
    Future<Integer> updateAsync(Long id, @Parameter("vec") Vector vec)

    List<VectorDoc> findTop2OrderByIdDesc()

    List<VectorDoc> findTop2OrderByIdAsc()

}

@MappedEntity("generic_vector_search_doc")
class GenericVectorSearchDoc {
    @Id
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE, ref = "VECTOR_DOC_SEQ")
    Long id

    Vector embedding
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface GenericVectorSearchRepository extends PageableRepository<GenericVectorSearchDoc, Long> {

    SearchResults<GenericVectorSearchDoc> searchByEmbeddingNear(FloatVector vector, Double maxDistance)
}

@MappedEntity("vector_sparse_doc")
class SparseVectorDoc {
    @Id
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE, ref = "VECTOR_DOC_SEQ")
    Long id

    @VectorStorage(length = 5, shape = VectorShape.SPARSE)
    Vector embedding
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface SparseVectorDocRepository extends PageableRepository<SparseVectorDoc, Long> {
}
