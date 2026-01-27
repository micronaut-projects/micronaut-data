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
import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class SnakeCaseFindersSpec extends AbstractDataSpec {

    void "test simple find_by_title"() {
        given:
        def repository = buildRepository('test.BookRepository', """
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;

@Repository
interface BookRepository extends GenericRepository<Book, Long> {

    Book find_by_title(String title);
}
""")
        when:
        def method = repository.getRequiredMethod("find_by_title", String)
        def query = getQuery(method)
        then:
        query == 'SELECT book_ FROM io.micronaut.data.tck.entities.Book AS book_ WHERE (book_.title = :p1)'

    }

    void "test count_distinct_by_name"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.entities.Person;
import io.micronaut.data.repository.GenericRepository;

@JdbcRepository(dialect = Dialect.MYSQL)
interface PersonRepository extends GenericRepository<Person, Long> {

    long count_distinct_by_name(String name);
}
""")
        when:
        def method = repository.getRequiredMethod("count_distinct_by_name", String)
        then:
        method != null
        when:
        def query = getQuery(method)
        then:
        query == 'SELECT COUNT(DISTINCT(person_.`id`)) FROM `person` person_ WHERE (person_.`name` = ?)'
    }

    void "test delete_by_id_returning compiles"() {
        given:
        def repository = buildRepository('test.BookRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;

@JdbcRepository(dialect = Dialect.POSTGRES)
interface BookRepository extends GenericRepository<Book, Long> {

    int delete_by_id_returning(Long id);
}
""")
        when:
        def method = repository.getRequiredMethod("delete_by_id_returning", Long)
        def query = getQuery(method)
        then:
        query == 'DELETE  FROM "book"  WHERE ("id" = ?) RETURNING "id","author_id","genre_id","title","total_pages","publisher_id","last_updated"'
    }

    @Unroll
    void "test complex snake_case '#name' parses"() {
        given:
        def repository = buildRepository('test.BookRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Author;

@JdbcRepository(dialect = Dialect.H2)
interface BookRepository extends GenericRepository<Book, Long> {

    java.util.List<Book> ${name}(String a, String b);
}
""")
        expect:
        repository.findPossibleMethods(name).findFirst().isPresent()
        where:
        name << [
            'find_all_by_author_name_or_title_like_order_by_total_pages_desc',
            'query_all_by_title_or_author_name'
        ]
    }

    @Unroll
    void "test invalid snake_case #name is rejected"() {
        when:
        buildRepository('test.BookRepository', """
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.tck.entities.Book;

@Repository
interface BookRepository extends GenericRepository<Book, Long> {

    Book ${name}(String title);
}
""")
        then:
        def ex = thrown(RuntimeException)
        ex.message.contains('Unable to implement Repository method')
        where:
        name << ['_find_by_title', 'find__by_title', 'find_by__title', 'find_by_title_']
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
