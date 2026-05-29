package io.micronaut.data.jdbc.oraclexe.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.model.vector.Vector
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.SQLException

/**
 * Negative scenario for using Vector on a non-Oracle dialect (H2).
 * Schema generator should filter out VECTOR entities, table won't be created
 * and repository operations should fail accordingly.
 */
class OracleJdbcVectorNegativeSpec extends Specification {

    void "vectors are not supported on non-Oracle dialect: schema is filtered and operations fail (H2)"() {
        given: "An H2 ApplicationContext with this package scanned"
        Map<String, Object> h2Props = [
                'datasources.default.url'            : "jdbc:h2:mem:vectorNeg;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
                'datasources.default.schema-generate': "CREATE",
                'datasources.default.dialect'        : "h2",
                'datasources.default.username'       : "",
                'datasources.default.password'       : "",
                'datasources.default.packages'       : [this.class.package.name],
                'datasources.default.driverClassName': "org.h2.Driver"
        ]
        ApplicationContext h2Ctx = ApplicationContext.run(h2Props)

        and: "H2 datasource and the repository bean"
        DataSource h2Ds = h2Ctx.getBean(DataSource)
        // Use repository defined in this package in other tests; bean is generated for tests too
        VectorDoubleDocRepository repoOnH2 = h2Ctx.getBean(VectorDoubleDocRepository)

        and: "Schema generator should have filtered out vector entity, table is absent"
        boolean tableExists = tableExists(h2Ds, "vector_double_doc")

        expect: "The vector table is not created on non-Oracle dialect"
        !tableExists

        when: "Attempt to use repository with Vector on H2"
        def v = Vector.of([1f, 2f, 3f] as float[])
        repoOnH2.saveCustom(v)

        then: "Operation fails due to missing table / unsupported feature"
        Throwable ex = thrown()
        ex instanceof DataAccessException || ex instanceof SQLException || (ex?.cause instanceof SQLException)

        cleanup:
        try {
            h2Ctx?.close()
        } catch (ignored) { }
    }

    // --- helpers ---

    private boolean tableExists(DataSource ds, String tableName) {
        java.sql.Connection c = null
        ResultSet rs = null
        try {
            c = ds.getConnection()
            DatabaseMetaData md = c.getMetaData()
            rs = md.getTables(c.getCatalog(), c.getSchema(), "%", ["TABLE"] as String[])
            while (rs.next()) {
                def t = rs.getString("TABLE_NAME")
                if (t != null && t.equalsIgnoreCase(tableName)) {
                    return true
                }
            }
            return false
        } catch (Throwable ignored) {
            return false
        } finally {
            try { rs?.close() } catch (ignored) {}
            try { c?.close() } catch (ignored) {}
        }
    }
}
