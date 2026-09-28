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
package io.micronaut.data.jdbc.mysql.vector.validation

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.mysql.vector.MySqlVectorTestPropertyProvider
import io.micronaut.data.model.vector.FloatVector
import jakarta.persistence.Column
import spock.lang.Specification

import javax.sql.DataSource

class MySqlVectorSchemaValidationSpec extends Specification implements MySqlVectorTestPropertyProvider {

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    void 'vector columns are validated including the dimension'() {
        given:"The schema is created"
        def context = ApplicationContext.run(properties)
        def dataSource = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource))
        def validateProperties = properties + ['datasources.default.schema-generate': 'VALIDATE']

        when:"The created schema is validated"
        ApplicationContext.run(validateProperties).close()

        then:
        noExceptionThrown()

        when:"The vector dimension is different"
        execute(dataSource, "DROP TABLE mysql_vector_validation_doc")
        execute(dataSource, "CREATE TABLE mysql_vector_validation_doc (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, embedding VECTOR(4) NOT NULL)")
        ApplicationContext.run(validateProperties).close()

        then:
        def e = thrown(Exception)
        rootMessage(e).contains('Column [embedding] in table [mysql_vector_validation_doc] has vector dimension [4] but the mapped dimension is [3]')

        when:"The column is not a vector"
        execute(dataSource, "DROP TABLE mysql_vector_validation_doc")
        execute(dataSource, "CREATE TABLE mysql_vector_validation_doc (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, embedding BIGINT NOT NULL)")
        ApplicationContext.run(validateProperties).close()

        then:
        e = thrown(Exception)
        rootMessage(e).contains('Column [embedding] in table [mysql_vector_validation_doc] of type [BIGINT] is mapped to definition [VECTOR(3)')

        cleanup:
        execute(dataSource, "DROP TABLE IF EXISTS mysql_vector_validation_doc")
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

@MappedEntity("mysql_vector_validation_doc")
class MySqlVectorValidationDoc {

    @Id
    @GeneratedValue
    Long id

    @Column(length = 3)
    FloatVector embedding
}
