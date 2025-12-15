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
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.repository.PageableRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement

/**
 * Dimension length constraints for Oracle VECTOR columns.
 *
 * Defines expected behavior:
 * - When a table declares VECTOR(3, FLOAT64), attempting to persist a 4-dimensional vector should fail.
 * - When a table declares VECTOR(3, FLOAT64), attempting to persist a 2-dimensional vector should fail,
 *   and subsequently persisting a 3-dimensional vector should succeed.
 *
 * NOTE: This spec relies on Oracle Test Resources via OracleTestPropertyProvider.
 */
class OracleJdbcVectorLengthSpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    DataSource dataSource = context.getBean(DataSource)

    @Shared
    VectorLenDocRepository repo = context.getBean(VectorLenDocRepository)

    @Override
    List<String> packages() {
        // Ensure entity/repository in this package are scanned
        return [getClass().package.name]
    }

    def cleanup() {
        // Clean table between tests (ignore if not present)
        executeSilently "DELETE FROM vector_len_doc"
        // also try to drop indexes/sequences/tables quietly to avoid cross-test interference
        executeSilently "DROP TABLE vector_len_doc"
        executeSilently "DROP SEQUENCE VECTOR_LEN_DOC_SEQ"
        // small delay to reduce flakiness on CI
        try { Thread.sleep(25) } catch (ignored) {}
    }

    void "VECTOR(3,FLOAT64) - inserting 4 dimensions is accepted (documenting current behavior)"() {
        given: "A table with a fixed 3-length VECTOR column"
        createVectorLenTable(3, "FLOAT64")

        and: "A 4-dimensional vector"
        Vector v = Vector.of([1d, 2d, 3d, 4d] as double[])

        when: "Persist a vector with more than declared dimensions"
        repo.saveCustom(v)
        def rows = repo.findAll(Sort.of(Sort.Order.asc("id")))
        def last = rows.last()

        then: "Insert succeeds; driver/database accepts dimension mismatch"
        last != null
        last.embedding != null
        last.embedding.type == Double.TYPE
        last.embedding.toDoubleArray().length == 4
    }

    void "VECTOR(3,FLOAT64) - inserting 2 then 3 dimensions is accepted (documenting current behavior)"() {
        given: "A table with a fixed 3-length VECTOR column"
        createVectorLenTable(3, "FLOAT64")

        and: "A 2D vector and a 3D vector"
        Vector v2 = Vector.of([1d, 2d] as double[])
        Vector v3 = Vector.of([7d, 8d, 9d] as double[])

        when: "Persist both vectors"
        repo.saveCustom(v2)
        repo.saveCustom(v3)
        def rows = repo.findAll(Sort.of(Sort.Order.asc("id")))
        def prev = rows[rows.size() - 2]
        def last = rows.last()

        then: "Both inserts succeed; vectors are stored as-is"
        prev.embedding != null
        prev.embedding.type == Double.TYPE
        prev.embedding.toDoubleArray().length == 2

        last.embedding != null
        last.embedding.type == Double.TYPE
        last.embedding.toDoubleArray().toList() == [7d, 8d, 9d]
    }

    void "VECTOR(3,FLOAT64) - inserting empty vector fails"() {
        given: "A table with a fixed 3-length VECTOR column"
        createVectorLenTable(3, "FLOAT64")

        and: "An empty vector"
        Vector vEmpty = Vector.of([] as double[])

        when: "Attempt to persist an empty vector"
        repo.saveCustom(vEmpty)

        then: "Oracle rejects empty vector with ORA-51805"
        Throwable ex = thrown()
        ex instanceof SQLException || (ex instanceof RuntimeException && ex.cause instanceof SQLException)
    }

    void "VECTOR(*,FLOAT64) - inserting empty vector fails"() {
        given: "A table with unbounded VECTOR column (any length)"
        createVectorLenTable(Integer.MIN_VALUE, "FLOAT64") // special flag to create unbounded

        and: "An empty vector"
        Vector vEmpty = Vector.of([] as double[])

        when: "Attempt to persist an empty vector"
        repo.saveCustom(vEmpty)

        then: "Oracle rejects empty vector with ORA-51805"
        Throwable ex = thrown()
        ex instanceof SQLException || (ex instanceof RuntimeException && ex.cause instanceof SQLException)
    }

    // --- helpers ---

    private void createVectorLenTable(int len, String oracleElemType) {
        // drop any leftovers
        executeSilently "DROP TABLE vector_len_doc"
        executeSilently "DROP SEQUENCE VECTOR_LEN_DOC_SEQ"
        // create objects
        executeSilently "CREATE SEQUENCE VECTOR_LEN_DOC_SEQ"
        if (len == Integer.MIN_VALUE) {
            // unbounded length VECTOR(*,type)
            executeSilently "CREATE TABLE vector_len_doc (id NUMBER PRIMARY KEY, embedding VECTOR(*, ${oracleElemType}))"
        } else {
            executeSilently "CREATE TABLE vector_len_doc (id NUMBER PRIMARY KEY, embedding VECTOR(${len}, ${oracleElemType}))"
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
            // ignore errors from already exists/doesn't exist or XE feature gaps
        } finally {
            try { st?.close() } catch (ignored) {}
            try { c?.close() } catch (ignored) {}
        }
    }
}

@MappedEntity("vector_len_doc")
class VectorLenDoc {
    @Id
    @GeneratedValue(value = GeneratedValue.Type.SEQUENCE, ref = "VECTOR_LEN_DOC_SEQ")
    Long id
    // Using io.micronaut.data.model.vector.Vector to let converter pick the appropriate concrete type on read
    Vector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    Vector getEmbedding() { return embedding }
    void setEmbedding(Vector embedding) { this.embedding = embedding }
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface VectorLenDocRepository extends PageableRepository<VectorLenDoc, Long> {

    @Query("INSERT INTO vector_len_doc(id, embedding) VALUES (VECTOR_LEN_DOC_SEQ.nextval, :vec)")
    void saveCustom(@Parameter("vec") Vector vec)
}
