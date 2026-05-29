package io.micronaut.data.jdbc.mysql.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.repository.PageableRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.Statement

/**
 * Dimension length behavior for MySQL HeatWave VECTOR columns.
 *
 * This mirrors OracleJdbcVectorLengthSpec scenarios but adapted to MySQL:
 * - Create a table with VECTOR(3)
 * - Insert vectors with 4 and 2 dimensions and document current engine/driver behavior
 *   (at the time of writing, insertion is accepted; the value is stored as-is).
 *
 * NOTE: This spec relies on MySQL Test Resources via MySQLTestPropertyProvider.
 */
class MySqlJdbcVectorLengthSpec extends Specification implements MySqlVectorTestPropertyProvider {

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
        // also try to drop table quietly to avoid cross-test interference
        executeSilently "DROP TABLE vector_len_doc"
        // small delay to reduce flakiness on CI
        try { Thread.sleep(25) } catch (ignored) {}
    }

    void "VECTOR(3) - inserting 4 dimensions is accepted (documenting current behavior)"() {
        given: "A table with a fixed 3-length VECTOR column"
        createVectorLenTable(3)

        and: "A 4-dimensional vector"
        Vector v = Vector.of([1d, 2d, 3d, 4d] as float[])

        when: "Persist a vector with more than declared dimensions"
        repo.saveCustom(v)
        def rows = repo.findAll(Sort.of(Sort.Order.asc("id")))
        def last = rows.last()

        then: "Insert succeeds; driver/database accepts dimension mismatch (documenting observed behavior)"
        last != null
        last.embedding != null
        last.embedding.type == Float.TYPE
        last.embedding.toFloatArray().length == 4
    }

    void "VECTOR(3) - inserting 2 then 3 dimensions is accepted (documenting current behavior)"() {
        given: "A table with a fixed 3-length VECTOR column"
        createVectorLenTable(3)

        and: "A 2D vector and a 3D vector"
        Vector v2 = Vector.of([1d, 2d] as float[])
        Vector v3 = Vector.of([7d, 8d, 9d] as float[])

        when: "Persist both vectors"
        repo.saveCustom(v2)
        repo.saveCustom(v3)
        def rows = repo.findAll(Sort.of(Sort.Order.asc("id")))
        def prev = rows[rows.size() - 2]
        def last = rows.last()

        then: "Both inserts succeed; vectors are stored as-is"
        prev.embedding != null
        prev.embedding.type == Float.TYPE
        prev.embedding.toFloatArray().length == 2

        last.embedding != null
        last.embedding.type == Float.TYPE
        last.embedding.toFloatArray().toList() == [7d, 8d, 9d]
    }

    // --- helpers ---

    private void createVectorLenTable(int len) {
        // drop any leftovers
        executeSilently "DROP TABLE vector_len_doc"
        // create objects
        executeSilently "CREATE TABLE vector_len_doc (id BIGINT PRIMARY KEY AUTO_INCREMENT, embedding VECTOR(${len}))"
    }

    private void executeSilently(String sql) {
        Connection c = null
        Statement st = null
        try {
            c = dataSource.getConnection()
            st = c.createStatement()
            st.execute(sql)
        } catch (Throwable ignored) {
            // ignore errors from already exists/doesn't exist
        } finally {
            try { st?.close() } catch (ignored) {}
            try { c?.close() } catch (ignored) {}
        }
    }
}

@MappedEntity("vector_len_doc")
class VectorLenDoc {
    @Id
    @GeneratedValue
    Long id
    // Using io.micronaut.data.model.vector.Vector to let converter pick the appropriate concrete type on read
    Vector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    Vector getEmbedding() { return embedding }
    void setEmbedding(Vector embedding) { this.embedding = embedding }
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface VectorLenDocRepository extends PageableRepository<VectorLenDoc, Long> {

    @Query("INSERT INTO vector_len_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") Vector vec)
}
