/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.jdbc.oraclexe.jsonview

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.query.builder.sql.SqlSchemaUtils
import io.micronaut.data.model.query.builder.sql.validation.SchemaValidationException
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import javax.sql.DataSource

/**
 * Validates the JSON relational duality views created by the schema generation of the {@code oracle-jsonview} environment.
 */
@MicronautTest(environments = ["oracle-jsonview"])
class OracleJdbcJsonViewSchemaValidationSpec extends Specification {

    @Inject
    ApplicationContext context

    @Inject
    DataSource dataSource

    @Inject
    RuntimeEntityRegistry runtimeEntityRegistry

    void 'JSON view mapping is derived from the view entity'() {
        when:
        def mapping = SqlSchemaUtils.getSqlJsonViewMapping(runtimeEntityRegistry.getEntity(StudentView))

        then:
        mapping.name().equalsIgnoreCase('student_view')
        mapping.rootTable().equalsIgnoreCase('TBL_STUDENT')
        mapping.tables().collect { it.toUpperCase() } as Set == ['TBL_STUDENT', 'TBL_ADDRESS', 'TBL_STUDENT_CLASSES', 'TBL_CLASS', 'TBL_TEACHER'] as Set
        mapping.fields().collect { [it.table().toUpperCase(), it.key()] }.containsAll([
                ['TBL_STUDENT', '_id'], ['TBL_STUDENT', 'name'], ['TBL_STUDENT', 'averageGrade'],
                ['TBL_ADDRESS', 'street'], ['TBL_CLASS', 'room']
        ])
    }

    void 'created JSON views are valid'() {
        when:
        ApplicationContext.run(validateProperties()).close()

        then:
        noExceptionThrown()
    }

    void 'JSON view differences are reported'() {
        given:"A view missing a field"
        execute("CREATE OR REPLACE JSON RELATIONAL DUALITY VIEW CROCODILE_VIEW AS SELECT JSON {'_id': c.ID} FROM CROCODILE c WITH INSERT UPDATE DELETE")
        and:"A missing view"
        execute("DROP VIEW PERSON_VIEW")

        when:
        ApplicationContext.run(validateProperties()).close()

        then:
        def e = thrown(Exception)
        def message = findValidationException(e).message
        message.contains('Schema validation failed with 4 errors')
        message.contains('JSON view [crocodile_view] field [name] of table [crocodile] not found')
        message.contains('JSON view [crocodile_view] field [weight] of table [crocodile] not found')
        message.contains('JSON view [crocodile_view] field [length] of table [crocodile] not found')
        message.contains('Expected JSON view [person_view] not found')

        cleanup:
        recreateView(CrocodileView)
        recreateView(PersonView)
    }

    private Map<String, Object> validateProperties() {
        return [
                'datasources.default.url'            : context.getRequiredProperty('datasources.default.url', String),
                'datasources.default.username'       : context.getRequiredProperty('datasources.default.username', String),
                'datasources.default.password'       : context.getRequiredProperty('datasources.default.password', String),
                'datasources.default.driver-class-name': context.getProperty('datasources.default.driver-class-name', String).orElse('oracle.jdbc.OracleDriver'),
                'datasources.default.dialect'        : 'ORACLE',
                'datasources.default.schema-generate': 'VALIDATE',
                'datasources.default.packages'       : getClass().package.name
        ] as Map<String, Object>
    }

    private void recreateView(java.lang.Class<?> viewType) {
        for (String sql : new SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(runtimeEntityRegistry.getEntity(viewType))) {
            execute(sql)
        }
    }

    private void execute(String sql) {
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            connection.prepareStatement(sql).withCloseable { it.execute() }
        }
    }

    private static SchemaValidationException findValidationException(Throwable e) {
        Throwable current = e
        while (current != null) {
            if (current instanceof SchemaValidationException) {
                return current
            }
            current = current.cause
        }
        throw new AssertionError("SchemaValidationException expected", e)
    }
}
