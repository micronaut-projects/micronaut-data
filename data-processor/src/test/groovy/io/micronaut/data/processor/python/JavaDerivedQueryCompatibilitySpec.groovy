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
package io.micronaut.data.processor.python

import io.micronaut.data.processor.visitors.AbstractDataSpec

import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class JavaDerivedQueryCompatibilitySpec extends AbstractDataSpec {

    void "Java retains literal underscore properties and association paths"() {
        when:
        def repository = buildRepository("test.BookRepository", """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@MappedEntity
record Author(@Id Long id, String first_name, String name_in_list) {}

@MappedEntity
record Book(@Id Long id, String first_name, String name_in_list,
            @Relation(Relation.Kind.MANY_TO_ONE) Author author) {}

@JdbcRepository(dialect = Dialect.H2)
interface BookRepository extends GenericRepository<Book, Long> {
    List<Book> findByFirst_name(String first_name);
    List<Book> findByName_in_list(String name_in_list);
    List<Book> findByauthor_first_name(String first_name);
    List<Book> findByauthor_name_in_list(String name_in_list);
}
""")

        then:
        getQuery(repository.getRequiredMethod("findByFirst_name", String)).endsWith('WHERE (book_.`first_name` = ?)')
        getQuery(repository.getRequiredMethod("findByName_in_list", String)).endsWith('WHERE (book_.`name_in_list` = ?)')
        getQuery(repository.getRequiredMethod("findByauthor_first_name", String)).endsWith('WHERE (book_author_.`first_name` = ?)')
        getQuery(repository.getRequiredMethod("findByauthor_name_in_list", String)).endsWith('WHERE (book_author_.`name_in_list` = ?)')
    }

    void "Python snake case grammar is not enabled for Java"() {
        when:
        buildRepository("test.BookRepository", """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@MappedEntity
record Book(@Id Long id, String first_name) {}

@JdbcRepository(dialect = Dialect.H2)
interface BookRepository extends GenericRepository<Book, Long> {
    List<Book> find_by_first_name(String first_name);
}
""")

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains("Cannot project on non-existent property: _by_first_name")
    }
}
