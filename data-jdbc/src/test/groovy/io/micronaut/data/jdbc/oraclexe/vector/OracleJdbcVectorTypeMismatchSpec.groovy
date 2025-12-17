package io.micronaut.data.jdbc.oraclexe.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import io.micronaut.data.model.vector.Vector
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource

/**
 * Negative scenarios for Oracle VECTOR type mismatches.
 * Verifies that inserting vectors of the wrong primitive backing type fails.
 *
 * NOTE:
 * - This spec uses OracleTestPropertyProvider and will attempt to start Oracle Test Resources.
 * - If Oracle isn't available, you can run only the H2-only negative spec:
 *   ./gradlew :micronaut-data-jdbc:test --tests 'io.micronaut.data.jdbc.oraclexe.vector.OracleJdbcVectorNegativeSpec' -Dmicronaut.test.resources.enabled=false
 */
class OracleJdbcVectorTypeMismatchSpec extends Specification implements OracleTestPropertyProvider {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    DataSource dataSource = context.getBean(DataSource)

    @Shared
    VectorFloatDocRepository floatRepo = context.getBean(VectorFloatDocRepository)

    @Override
    List<String> packages() {
        // Ensure entity/repository in this package are scanned
        return [getClass().package.name]
    }

    def cleanup() {
        // Clean table between tests (ignore if table doesn't exist)
        executeSilently "DELETE FROM vector_doc"
        // flush TX boundary
        context.getBean(io.micronaut.transaction.SynchronousTransactionManager).executeWrite { status -> null }
    }

    void "saving IntVector into FLOAT32 column coerces values (no error)"() {
        given: "A FLOAT32 typed VECTOR column"
        dropSilently("DROP TABLE vector_doc")
        dropSilently("DROP SEQUENCE VECTOR_DOC_SEQ")
        executeSilently "CREATE SEQUENCE VECTOR_DOC_SEQ"
        executeSilently "CREATE TABLE vector_doc (id NUMBER PRIMARY KEY, embedding VECTOR(3, FLOAT32))"

        and: "An int-backed vector"
        byte  [] iv = [1, 2, 3] as byte[]
        def vec = Vector.of(iv)

        when: "Saving an IntVector into a FLOAT32 column"
        floatRepo.saveCustom(vec)

        then: "It succeeds and values are coerced to floats by the driver/database"
        def rows = floatRepo.findAll(io.micronaut.data.model.Sort.of(io.micronaut.data.model.Sort.Order.asc("id")))
        def last = rows.last()
        last.embedding != null
        last.embedding.type == Float.TYPE
        last.embedding.toFloatArray().toList() == [1f, 2f, 3f]
    }



    private void executeSilently(String sql) {
        java.sql.Connection c = null
        java.sql.Statement st = null
        try {
            c = dataSource.getConnection()
            st = c.createStatement()
            st.execute(sql)
        } catch (Throwable ignored) {
            // ignore if already exists/absent or unsupported
        } finally {
            try { st?.close() } catch (ignored) {}
            try { c?.close() } catch (ignored) {}
        }
    }

    private void dropSilently(String sql) {
        executeSilently(sql)
        // small delay can reduce flakiness on CI
        try { Thread.sleep(25) } catch (ignored) {}
    }
}
