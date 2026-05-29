package io.micronaut.data.jdbc.mysql.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.Sort
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.Vector
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

/**
 * MySQL HeatWave VECTOR JDBC integration spec mirroring OracleJdbcVectorEntitySpec,
 * adapted to MySQL specifics (no sequences, VECTOR(N) without element type).
 */
class MySqlJdbcVectorEntitySpec extends Specification implements MySqlVectorTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    VectorDocRepository vectorRepository = context.getBean(VectorDocRepository)

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
        // no-op transaction boundary to flush
        context.getBean(SynchronousTransactionManager).executeWrite { status -> null }
    }

    void "test save, find and update single entity (custom queries with io.micronaut.data.model.Vector)"() {
        given:
        float[] dv = [1f, 2.5f, -3.75f] as float[]
        Vector v1 = Vector.of(dv)

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
        float[] dv2 = [3d, 0.0d, 7.25d] as float[]
        Vector v2 = Vector.of(dv2)
        vectorRepository.updateCustom(e.id, v2)
        def updated = vectorRepository.findById(e.id).orElse(null)

        then:
        updated != null
        updated.embedding.type == Float.TYPE
        updated.embedding.toFloatArray().toList() == dv2.toList()
    }

    void "test save, find and update multiple entities"() {
        given:
        Vector vA = Vector.of([1f, 2f, 3f] as float[])
        Vector vB = Vector.of([4f, 5f, 6f] as float[])

        when:
        vectorRepository.saveCustom(vA)
        vectorRepository.saveCustom(vB)
        def rows = vectorRepository.findAll(Sort.of(Sort.Order.asc("id")))

        then:
        def idA = rows.find { it.embedding.toFloatArray().toList() == [1d, 2d, 3d] }?.id
        def idB = rows.find { it.embedding.toFloatArray().toList() == [4d, 5d, 6d] }?.id
        idA != null
        idB != null
        idA != idB

        when: "update both"
        Vector vA2 = Vector.of([7f, 8f, 9f] as float[])
        Vector vB2 = Vector.of([0f, -1f, -2f] as float[])
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
        Vector vec = Vector.of([10f, 11f, 12f] as float[])

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
        found.get(0).embedding.toFloatArray().toList() == [10f, 11f, 12f]

        when:
        Vector vec2 = Vector.of([13f, 14f, 15f] as float[])
        Future<Integer> updFut = vectorRepository.updateAsync(last.id, vec2)

        then:
        updFut.get(10, TimeUnit.SECONDS) != null
        with(vectorRepository.findById(last.id)) {
            it.isPresent()
            it.get().embedding.toFloatArray().toList() == [13f, 14f, 15f]
        }
    }

    private void executeSilently(String sql) {
        Connection c = null
        Statement st = null
        try {
            c = dataSource.getConnection()
            st = c.createStatement()
            st.execute(sql)
        } catch (Throwable ignored) {
            // ignore if already exists or unsupported
        } finally {
            try { st?.close() } catch (ignored) {}
            try { c?.close() } catch (ignored) {}
        }
    }
}

@MappedEntity("vector_doc")
class VectorDoc {
    @Id
    @GeneratedValue
    Long id
    Vector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    Vector getEmbedding() { return embedding }
    void setEmbedding(Vector embedding) { this.embedding = embedding }
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface VectorDocRepository extends PageableRepository<VectorDoc, Long> {

    @Query("INSERT INTO vector_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") FloatVector vec)

    @Query("SELECT * FROM vector_doc WHERE id = :id")
    Optional<VectorDoc> findById(Long id)

    @Query("UPDATE vector_doc SET embedding = :vec WHERE id = :id")
    void updateCustom(Long id, @Parameter("vec") Vector vec)

    @Query("INSERT INTO vector_doc(embedding) VALUES (:vec)")
    Future<Integer> saveAsync(@Parameter("vec") Vector vec)

    @Query("SELECT * FROM vector_doc WHERE id = :id")
    Future<List<VectorDoc>> findAsync(Long id)

    @Query("UPDATE vector_doc SET embedding = :vec WHERE id = :id")
    Future<Integer> updateAsync(Long id, @Parameter("vec") Vector vec)
}
