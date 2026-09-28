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
package io.micronaut.data.jdbc.schemavalidation.mysql.explicitschema

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.mysql.MySQLTestPropertyProvider
import io.micronaut.data.runtime.config.SchemaGenerate
import spock.lang.Specification

import javax.sql.DataSource

class MySqlCaseSensitiveTableValidationSpec extends Specification implements MySQLTestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.NONE
    }

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    void 'table names differing only in case are validated separately'() {
        given:"MySQL on Linux (lower_case_table_names=0) with the distinct tables Case_item and case_item"
        def context = ApplicationContext.run(properties)
        def dataSource = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource))
        def validateProperties = properties + ['datasources.default.schema-generate': 'VALIDATE']
        // The database is shared with the other specs
        execute(dataSource, 'DROP TABLE IF EXISTS Case_item')
        execute(dataSource, 'DROP TABLE IF EXISTS case_item')
        execute(dataSource, 'CREATE TABLE Case_item (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(255) NOT NULL)')
        execute(dataSource, 'CREATE TABLE case_item (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(255) NOT NULL)')

        when:
        ApplicationContext.run(validateProperties).close()

        then:
        noExceptionThrown()

        when:"One of the tables is missing"
        execute(dataSource, 'DROP TABLE Case_item')
        ApplicationContext.run(validateProperties).close()

        then:
        def e = thrown(Exception)
        rootMessage(e) == 'Schema validation failed. Expected table [Case_item] not found'

        cleanup:
        execute(dataSource, 'DROP TABLE IF EXISTS Case_item')
        execute(dataSource, 'DROP TABLE IF EXISTS case_item')
        context?.close()
    }

    private static void execute(DataSource dataSource, String sql) {
        dataSource.connection.withCloseable { connection ->
            connection.prepareStatement(sql).withCloseable { it.executeUpdate() }
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable current = e
        while (current.cause != null) {
            current = current.cause
        }
        return current.message
    }
}

@MappedEntity(value = "Case_item", escape = false)
class UpperCaseItem {
    @Id
    Long id
    String name
}

@MappedEntity(value = "case_item", escape = false)
class LowerCaseItem {
    @Id
    Long id
    String name
}
