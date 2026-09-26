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
package io.micronaut.data.tck.tests

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.config.DataJdbcConfiguration
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.tck.entities.schema.SchemaAuthor
import io.micronaut.data.tck.entities.schema.SchemaBook
import io.micronaut.data.tck.entities.schema.SchemaTag
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.DatabaseMetaData

abstract class AbstractSchemaSpec extends Specification {

    static final String SCHEMA_ENTITIES_PACKAGE = "io.micronaut.data.tck.entities.schema"

    abstract Map<String, String> getProperties()

    /**
     * Validates created schema using entities from given package.
     */
    void 'validate schema'() {
        given:
        def props = new HashMap<>(properties)
        props["datasources.default.packages"] = SCHEMA_ENTITIES_PACKAGE
        def initialContext = ApplicationContext.run(props)
        when:
        def schemaValidateProperties = new HashMap<>(props)
        schemaValidateProperties["datasources.default.schema-generate"] =  "validate"
        def validationContext = ApplicationContext.run(schemaValidateProperties)
        then:
        noExceptionThrown()
        cleanup:
        if (initialContext) {
            initialContext.close()
        }
        if (validationContext) {
            validationContext.close()
        }
    }

    /**
     * Creates the schema with foreign keys and validates it including the foreign keys.
     */
    void 'create and validate schema with foreign keys'() {
        given:
        def props = new HashMap<>(properties)
        props["datasources.default.packages"] = SCHEMA_ENTITIES_PACKAGE
        props["datasources.default.schema-generate-foreign-keys"] = "true"
        def initialContext = ApplicationContext.run(props)
        def dataSource = DelegatingDataSource.unwrapDataSource(initialContext.getBean(DataSource))

        when:"The schema is created with foreign keys"
        Map<String, Set<String>> referencedTables = [:]
        dataSource.connection.withCloseable { Connection connection ->
            ["schema_book", "schema_book_schema_tag"].each { table ->
                referencedTables[table] = getReferencedTables(connection, table)
            }
        }

        then:"The foreign keys reference the associated tables"
        referencedTables["schema_book"] == ["schema_author"] as Set
        referencedTables["schema_book_schema_tag"] == ["schema_book", "schema_tag"] as Set

        when:"The schema is validated including the foreign keys"
        def schemaValidateProperties = new HashMap<>(props)
        schemaValidateProperties["datasources.default.schema-generate"] =  "validate"
        def validationContext = ApplicationContext.run(schemaValidateProperties)

        then:
        noExceptionThrown()

        cleanup:
        if (initialContext) {
            dropForeignKeys(initialContext)
            initialContext.close()
        }
        if (validationContext) {
            validationContext.close()
        }
    }

    /**
     * Drops the generated foreign keys, so that the tables can be dropped by the specs not generating foreign keys.
     */
    static void dropForeignKeys(ApplicationContext context) {
        def dialect = context.getBean(DataJdbcConfiguration, Qualifiers.byName("default")).dialect
        def registry = context.getBean(RuntimeEntityRegistry)
        PersistentEntity[] entities = [SchemaBook, SchemaAuthor, SchemaTag].collect { registry.getEntity(it) } as PersistentEntity[]
        def dataSource = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource))
        dataSource.connection.withCloseable { Connection connection ->
            new SqlQueryBuilder(dialect).buildDropForeignKeyStatements([], entities).each { sql ->
                try {
                    connection.prepareStatement(sql).withCloseable { it.executeUpdate() }
                } catch (Exception ignored) {
                    // the constraint does not exist
                }
            }
        }
    }

    /**
     * @return the lower case names of the tables referenced by the foreign keys of the given table
     */
    static Set<String> getReferencedTables(Connection connection, String table) {
        DatabaseMetaData metaData = connection.metaData
        Set<String> referencedTables = [] as Set
        // Escaped table names can be stored as declared, others in the database identifier case
        [table, table.toUpperCase(Locale.ENGLISH)].unique().each { tableName ->
            metaData.getImportedKeys(connection.catalog, connection.schema, tableName).withCloseable { rs ->
                while (rs.next()) {
                    referencedTables << rs.getString("PKTABLE_NAME").toLowerCase(Locale.ENGLISH)
                }
            }
        }
        return referencedTables
    }
}
