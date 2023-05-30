/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.jdbc.postgres

import io.micronaut.data.jdbc.AbstractJdbcConnectionSpec
import io.micronaut.data.jdbc.h2.H2BookRepository
import io.micronaut.data.tck.repositories.SimpleBookRepository

class PostgresConnectionSpec extends AbstractJdbcConnectionSpec implements PostgresTestPropertyProvider {

    @Override
    Class<? extends SimpleBookRepository> getBookRepositoryClass() {
        return H2BookRepository.class
    }

    @Override
    List<String> createStatements() {
        return Arrays.asList(
                "CREATE TABLE patient(name VARCHAR(255), id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY, history VARCHAR(1000), doctor_notes VARCHAR(255), appointments JSONB);",
                "CREATE TABLE book(title VARCHAR(255), id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY, total_pages BIGINT);",

        )
    }

    @Override
    List<String> dropStatements() {
        return Arrays.asList(
                "DROP TABLE patient",
                "DROP TABLE book"
        )
    }

    void "test read only connection 1"() {
        when:
            connectionOperations.executeRead { status ->
                assert status.getConnection().isReadOnly()
                bookService.addBookNoConnection()
                assert status.getConnection().isReadOnly()
            }
        then:
            bookService.countBooks() == 1
    }

}

