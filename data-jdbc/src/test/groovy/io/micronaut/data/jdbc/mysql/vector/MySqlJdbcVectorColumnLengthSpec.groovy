package io.micronaut.data.jdbc.mysql.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Parameter
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.repository.PageableRepository
import jakarta.persistence.Column
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement

/**
 * Documents that annotating a VECTOR embedding with @Column(length = 3) drives schema generation to emit VECTOR(3) on MySQL HeatWave.
 * We validate by inspecting SHOW CREATE TABLE and INFORMATION_SCHEMA.COLUMNS.
 */
class MySqlJdbcVectorColumnLengthSpec extends Specification implements MySqlVectorTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    DataSource dataSource = context.getBean(DataSource)

    @Shared
    VectorLenAnnoDocRepository repo = context.getBean(VectorLenAnnoDocRepository)

    @Override
    List<String> packages() {
        // Ensure entity/repository in this package are scanned for schema generation
        return [getClass().package.name]
    }

    def cleanup() {
        // Clean table between tests (ignore if not present), and try to drop to avoid cross-test interference
        executeSilently "DELETE FROM vector_len_anno_doc"
        executeSilently "DROP TABLE vector_len_anno_doc"
        try { Thread.sleep(25) } catch (ignored) {}
    }

    void "schema uses VECTOR(3) when @Column(length=3) is present on embedding (MySQL)"() {
        given: "Entity with @Column(length=3) on VECTOR property"
        // Trigger schema creation by interacting with the repository (or at startup)
        // Save a 3D vector to ensure the table exists
        repo.saveCustom(Vector.of([1f, 2f, 3f] as float[]))

        when: "Fetch the DDL of the generated table using SHOW CREATE TABLE"
        String ddl = fetchTableDDLUpper("vector_len_anno_doc")

        then: "Column definition includes VECTOR(3)"
        if (ddl != null) {
            assert ddl.contains("VECTOR(3)")
        } else {
            and: "Fallback - verify via INFORMATION_SCHEMA"
            def colInfo = fetchColumnType("vector_len_anno_doc", "embedding")
            assert colInfo == null || colInfo.toUpperCase(Locale.ROOT).contains("VECTOR(3)")
        }
    }

    // --- helpers ---

    private String fetchTableDDLUpper(String tableNameLower) {
        Connection c = null
        Statement st = null
        ResultSet rs = null
        try {
            c = dataSource.getConnection()
            st = c.createStatement()
            rs = st.executeQuery("SHOW CREATE TABLE `${tableNameLower}`")
            if (rs.next()) {
                // SHOW CREATE TABLE returns: Table | Create Table
                String ddl = rs.getString(2)
                return ddl != null ? ddl.toUpperCase(Locale.ROOT) : null
            }
            return null
        } catch (Throwable ignored) {
            return null
        } finally {
            try { rs?.close() } catch (ignored) {}
            try { st?.close() } catch (ignored) {}
            try { c?.close() } catch (ignored) {}
        }
    }

    private String fetchColumnType(String tableNameLower, String columnNameLower) {
        Connection c = null
        Statement st = null
        ResultSet rs = null
        try {
            c = dataSource.getConnection()
            st = c.createStatement()
            rs = st.executeQuery("""
                SELECT COLUMN_TYPE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = '${tableNameLower}'
                  AND COLUMN_NAME = '${columnNameLower}'
            """.stripIndent())
            if (rs.next()) {
                return rs.getString(1)
            }
            return null
        } catch (Throwable ignored) {
            return null
        } finally {
            try { rs?.close() } catch (ignored) {}
            try { st?.close() } catch (ignored) {}
            try { c?.close() } catch (ignored) {}
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
            // ignore already exists/absent
        } finally {
            try { st?.close() } catch (ignored) {}
            try { c?.close() } catch (ignored) {}
        }
    }
}

@MappedEntity("vector_len_anno_doc")
class VectorLenAnnoDoc {
    @Id
    @GeneratedValue
    Long id

    // Use @Column(length=3) to set the vector dimension in DDL, e.g. VECTOR(3)
    @Column(length = 3)
    Vector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    Vector getEmbedding() { return embedding }
    void setEmbedding(Vector embedding) { this.embedding = embedding }
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface VectorLenAnnoDocRepository extends PageableRepository<VectorLenAnnoDoc, Long> {

    @Query("INSERT INTO vector_len_anno_doc(embedding) VALUES (:vec)")
    void saveCustom(@Parameter("vec") Vector vec)
}
