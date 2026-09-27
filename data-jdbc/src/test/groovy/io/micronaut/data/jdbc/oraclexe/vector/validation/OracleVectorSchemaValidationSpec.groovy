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
package io.micronaut.data.jdbc.oraclexe.vector.validation

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.VectorShape
import io.micronaut.data.annotation.VectorStorage
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.Vector
import jakarta.persistence.Column
import spock.lang.Specification

import javax.sql.DataSource

class OracleVectorSchemaValidationSpec extends Specification implements OracleTestPropertyProvider {

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    void 'vector columns are validated including the dimension and format'() {
        given:"The schema is created"
        def context = ApplicationContext.run(properties)
        def dataSource = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource))
        def validateProperties = properties + ['datasources.default.schema-generate': 'VALIDATE']

        when:"The created schema is validated"
        ApplicationContext.run(validateProperties).close()

        then:
        noExceptionThrown()

        when:"The vector dimension is different"
        execute(dataSource, "DROP TABLE VECTOR_VALIDATION_DOC")
        execute(dataSource, "CREATE TABLE VECTOR_VALIDATION_DOC (ID NUMBER(19) NOT NULL PRIMARY KEY, EMBEDDING VECTOR(4, FLOAT32) NOT NULL)")
        ApplicationContext.run(validateProperties).close()

        then:
        def e = thrown(Exception)
        rootMessage(e).contains('Column [EMBEDDING] in table [VECTOR_VALIDATION_DOC] has vector dimension [4] but the mapped dimension is [3]')

        when:"Only the vector format is different"
        execute(dataSource, "DROP TABLE VECTOR_VALIDATION_DOC")
        execute(dataSource, "CREATE TABLE VECTOR_VALIDATION_DOC (ID NUMBER(19) NOT NULL PRIMARY KEY, EMBEDDING VECTOR(3, FLOAT64) NOT NULL)")
        ApplicationContext.run(validateProperties).close()

        then:"It's only a warning"
        noExceptionThrown()

        when:"A dense vector is mapped to a sparse vector column"
        execute(dataSource, "DROP TABLE VECTOR_VALIDATION_DOC")
        execute(dataSource, "CREATE TABLE VECTOR_VALIDATION_DOC (ID NUMBER(19) NOT NULL PRIMARY KEY, EMBEDDING VECTOR(3, FLOAT32, SPARSE) NOT NULL)")
        ApplicationContext.run(validateProperties).close()

        then:
        e = thrown(Exception)
        rootMessage(e).contains('Column [EMBEDDING] in table [VECTOR_VALIDATION_DOC] has vector storage [SPARSE] but the mapped storage is [DENSE]')

        when:"A sparse vector is mapped to a dense vector column"
        execute(dataSource, "DROP TABLE VECTOR_VALIDATION_DOC")
        execute(dataSource, "CREATE TABLE VECTOR_VALIDATION_DOC (ID NUMBER(19) NOT NULL PRIMARY KEY, EMBEDDING VECTOR(3, FLOAT32) NOT NULL)")
        execute(dataSource, "DROP TABLE SPARSE_VECTOR_VALIDATION_DOC")
        execute(dataSource, "CREATE TABLE SPARSE_VECTOR_VALIDATION_DOC (ID NUMBER(19) NOT NULL PRIMARY KEY, EMBEDDING VECTOR(5, FLOAT64, DENSE))")
        ApplicationContext.run(validateProperties).close()

        then:
        e = thrown(Exception)
        rootMessage(e).contains('Column [EMBEDDING] in table [SPARSE_VECTOR_VALIDATION_DOC] has vector storage [DENSE] but the mapped storage is [SPARSE]')

        cleanup:
        executeSilently(dataSource, "DROP TABLE VECTOR_VALIDATION_DOC")
        executeSilently(dataSource, "DROP SEQUENCE VECTOR_VALIDATION_DOC_SEQ")
        executeSilently(dataSource, "DROP TABLE SPARSE_VECTOR_VALIDATION_DOC")
        executeSilently(dataSource, "DROP SEQUENCE SPARSE_VECTOR_VALIDATION_DOC_SEQ")
        context?.close()
    }

    private static void execute(DataSource dataSource, String sql) {
        dataSource.connection.withCloseable { connection ->
            connection.prepareStatement(sql).withCloseable { it.executeUpdate() }
        }
    }

    private static void executeSilently(DataSource dataSource, String sql) {
        try {
            execute(dataSource, sql)
        } catch (Exception ignored) {
            // does not exist
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

@MappedEntity("vector_validation_doc")
class VectorValidationDoc {

    @Id
    @GeneratedValue
    Long id

    @Column(length = 3)
    FloatVector embedding
}

@MappedEntity("sparse_vector_validation_doc")
class SparseVectorValidationDoc {

    @Id
    @GeneratedValue
    Long id

    @Nullable
    @VectorStorage(length = 5, shape = VectorShape.SPARSE)
    Vector embedding
}
