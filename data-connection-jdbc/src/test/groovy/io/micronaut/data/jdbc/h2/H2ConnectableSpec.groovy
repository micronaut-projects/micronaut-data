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
package io.micronaut.data.jdbc.h2


import io.micronaut.data.jdbc.AbstractJdbcConnectableSpec
import io.micronaut.data.tck.repositories.SimpleBookRepository

class H2ConnectableSpec extends AbstractJdbcConnectableSpec implements H2TestPropertyProvider {

    @Override
    Class<? extends SimpleBookRepository> getBookRepositoryClass() {
        return H2BookRepository.class
    }

    @Override
    List<String> createStatements() {
        return super.createStatements() + ["CREATE TABLE book(title VARCHAR(255), id BIGINT PRIMARY KEY auto_increment, total_pages BIGINT);"]
    }

    @Override
    List<String> dropStatements() {
        return super.dropStatements() + ["DROP TABLE book"]
    }

    void "test read only connection doesn't work"() {
        when:
            connectionOperations.executeRead {
                bookService.mandatoryConnection()
            }
        then:
            bookService.countBooks() == 1
    }

}

