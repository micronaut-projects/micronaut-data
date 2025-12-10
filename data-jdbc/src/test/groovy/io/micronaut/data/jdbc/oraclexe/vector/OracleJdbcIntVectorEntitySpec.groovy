package io.micronaut.data.jdbc.oraclexe.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import io.micronaut.data.model.Sort
import io.micronaut.data.model.Vector
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

import static java.util.concurrent.TimeUnit.*

class OracleJdbcIntVectorEntitySpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    VectorIntDocRepository vectorRepository = context.getBean(VectorIntDocRepository)

    @Shared
    DataSource dataSource = context.getBean(DataSource)

    @Override
    List<String> packages() {
        // Ensure entity/repository in this package are scanned
        return [getClass().package.name]
    }

    def setupSpec() {
        // Create sequence and table if not exists (ignore errors if already present)
        executeSilently "CREATE SEQUENCE VECTOR_DOC_SEQ"
        // Oracle 23ai VECTOR: use 3 dims for tests (INT8)
        executeSilently "CREATE TABLE vector_int_doc (id NUMBER PRIMARY KEY, embedding VECTOR(3, INT8))"
    }

    def cleanup() {
        // Clean table between tests
        executeSilently "DELETE FROM vector_int_doc"
        // no-op transaction boundary to flush
        context.getBean(SynchronousTransactionManager).executeWrite { status -> null }
    }

    void "test save, find and update single entity (using custom queries with io.micronaut.data.model.Vector)"() {
        given:
        int[] dv = [1, 2, -3] as int[]
        Vector.IntVector v1 = Vector.of(dv)

        when: "save via custom @Query using Vector parameter"
        vectorRepository.saveCustom(v1)
        def list = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))
        def e = list.find { it.embedding.toIntegerArray().toList() == dv.toList() }

        then: "entity persisted and read conversion to Micronaut Vector works"
        e != null
        e.id != null
        e.embedding != null
        e.embedding.type == Integer.TYPE
        e.embedding.toIntegerArray().toList() == dv.toList()

        when: "update via custom @Query to a new vector"
        int [] dv2 = [3, 0, 7] as int[]
        Vector.IntVector v2 = Vector.of(dv2)
        vectorRepository.updateCustom(e.id, v2)
        def updated = vectorRepository.findById(e.id).orElse(null)

        then:
        updated != null
        updated.embedding.type == Integer.TYPE
        updated.embedding.toIntegerArray().toList() == dv2.toList()
    }


    void "test save, find and update multiple entities"() {
        given:
        Vector.IntVector vA = Vector.of([1, 2, 3] as int[])
        Vector.IntVector vB = Vector.of([4, 5, 6] as int[])

        when:
        vectorRepository.saveCustom(vA)
        vectorRepository.saveCustom(vB)
        def rows = vectorRepository.findAll(Sort.of(io.micronaut.data.model.Sort.Order.asc("id")))

        then:
        def idA = rows.find { it.embedding.toIntegerArray().toList() == [1, 2, 3] }?.id
        def idB = rows.find { it.embedding.toIntegerArray().toList() == [4, 5, 6] }?.id
        idA != null
        idB != null
        idA != idB

        when: "update both"
        Vector.IntVector vA2 = Vector.of([7, 8, 9] as int[])
        Vector.IntVector vB2 = Vector.of([0, -1, -2] as int[])
        vectorRepository.updateCustom(idA, vA2)
        vectorRepository.updateCustom(idB, vB2)
        def rows2 = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))

        then:
        def updatedA = rows2.find { it.id == idA }?.embedding?.toIntegerArray()?.toList()
        def updatedB = rows2.find { it.id == idB }?.embedding?.toIntegerArray()?.toList()
        updatedA == [7, 8, 9]
        updatedB == [0, -1, -2]
    }

    void "test custom and async queries"() {
        given:
        Vector.IntVector vec = Vector.of([10, 11, 12] as int[])

        when:
        Future<Integer> saveFut = vectorRepository.saveAsync(vec)

        then:
        saveFut.get(10, SECONDS) == 1

        when:
        def all = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))
        def last = all.last()
        Future<List<VectorIntDoc>> findFut = vectorRepository.findAsync(last.id)

        then:
        def found = findFut.get(10, SECONDS)
        found != null
        found.size() == 1
        found.get(0).embedding.toIntegerArray().toList() == [10, 11, 12]

        when:
        Vector.IntVector vec2 = Vector.of([13, 14, 15] as int[])
        Future<Integer> updFut = vectorRepository.updateAsync(last.id, vec2)

        then:
        updFut.get(10, SECONDS) != null
        with(vectorRepository.findById(last.id)) {
            it.isPresent()
            it.get().embedding.toIntegerArray().toList() == [13, 14, 15]
        }
    }

    void "test paging over VectorIntDoc"() {
        given:
        Vector.IntVector v1 = Vector.of([1, 2, 3] as int[])
        Vector.IntVector v2 = Vector.of([4, 5, 6] as int[])
        vectorRepository.saveCustom(v1)
        vectorRepository.saveCustom(v2)

        when:
        def p0 = vectorRepository.findAll(io.micronaut.data.model.Pageable.from(0, 1))
        def p1 = vectorRepository.findAll(io.micronaut.data.model.Pageable.from(1, 1))

        then:
        p0 != null
        p1 != null
        p0.getContent().size() == 1
        p1.getContent().size() == 1
        p0.getTotalSize() >= 2
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

@MappedEntity("vector_int_doc")
class VectorIntDoc {
    @Id
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE, ref = "VECTOR_DOC_SEQ")
    Long id
    Vector.IntVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    Vector.IntVector getEmbedding() { return embedding }
    void setEmbedding(Vector.IntVector embedding) { this.embedding = embedding }
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface VectorIntDocRepository extends PageableRepository<VectorIntDoc, Long> {

    @Query("INSERT INTO vector_int_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_int_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") Vector.IntVector vec)

    @Query("SELECT * FROM vector_int_doc WHERE id = :id")
    Optional<VectorIntDoc> findById(Long id)

    @Query("UPDATE vector_int_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_int_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector.IntVector vec)

    @Query("INSERT INTO vector_int_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    Future<Integer> saveAsync(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_int_doc(id, embedding) VALUES (VECTOR_DOC_SEQ.nextval, :vec)")
    Future<Integer> saveAsync(@Parameter("vec") Vector.IntVector vec)

    @Query("SELECT * FROM vector_int_doc WHERE id = :id")
    Future<List<VectorIntDoc>> findAsync(Long id)

    @Query("UPDATE vector_int_doc SET embedding = :vec WHERE id = :id")
    Future<Integer> updateAsync(Long id, @Parameter("vec") Vector vec)

    @Query("UPDATE vector_int_doc SET embedding = :vec WHERE id = :id")
    Future<Integer> updateAsync(Long id, @Parameter("vec") Vector.IntVector vec)

}
