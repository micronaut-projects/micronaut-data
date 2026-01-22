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
package io.micronaut.data.processor.visitors

import spock.lang.Unroll

class SnakeCaseFindersSpec extends AbstractDataSpec {

    void "test simple find_by_title"() {
        given:
        def repository = buildRepository('test.BookRepository', """
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Book;

@Repository
interface BookRepository extends CrudRepository<Book, Long> {

    Book find_by_title(String title);
}
""")
        expect:
        repository.getRequiredMethod("find_by_title", String) != null
    }

    void "test count_distinct_by_name"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.model.entities.Person;

@Repository
interface PersonRepository extends CrudRepository<Person, Long> {

    long count_distinct_by_name(String name);
}
""")
        when:
        def method = repository.getRequiredMethod("count_distinct_by_name", String)
        then:
        method != null
    }

    void "test delete_by_id_returning compiles"() {
        given:
        def repository = buildRepository('test.BookRepository', """
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Book;

@JdbcRepository(dialect = Dialect.POSTGRES)
interface BookRepository extends CrudRepository<Book, Long> {

    int delete_by_id_returning(Long id);
}
""")
        expect:
        repository.getRequiredMethod("delete_by_id_returning", Long) != null
    }

    @Unroll
    void "test complex snake_case '#name' parses"() {
        given:
        def repository = buildRepository('test.BookRepository', """
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Author;

@Repository
interface BookRepository extends CrudRepository<Book, Long> {

    java.util.List<Book> ${name}(String a, String b);
}
""")
        expect:
        repository.findPossibleMethods(name).findFirst().isPresent()
        where:
        name << [
            'find_all_by_author_name_or_title_like_order_by_total_pages_desc',
        ]
    }

    void "test find_first_10_by_name parses"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.model.entities.Person;

@Repository
interface PersonRepository extends CrudRepository<Person, Long> {

    java.util.List<Person> find_first_10_by_name(String name);
}
""")
        expect:
        repository.findPossibleMethods("find_first_10_by_name").findFirst().isPresent()
    }
}
