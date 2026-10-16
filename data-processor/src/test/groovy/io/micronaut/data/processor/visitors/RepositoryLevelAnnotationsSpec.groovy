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

import io.micronaut.data.tck.entities.Person

import static io.micronaut.data.processor.visitors.TestUtils.getCountQuery
import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class RepositoryLevelAnnotationsSpec extends AbstractDataSpec {

    void "test repository level @Where applies to finders inherited from a generic base interface"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.tck.entities.Person;

interface BaseRepository<E> extends GenericRepository<E, Long> {

    List<E> findByName(String name);

    Page<E> findByNameLike(String name, Pageable pageable);

    long deleteByName(String name);
}

@Repository
@Where("@.age > 18")
interface PersonRepository extends BaseRepository<Person> {

    List<Person> findByAge(int age);
}
""")

        expect:
        getQuery(repository.getRequiredMethod("findByName", String)) == "SELECT person_ FROM $Person.name AS person_ WHERE (person_.name = :p1 AND person_.age > 18)"
        getQuery(repository.getRequiredMethod("findByAge", int)) == "SELECT person_ FROM $Person.name AS person_ WHERE (person_.age = :p1 AND person_.age > 18)"
        getCountQuery(repository.findPossibleMethods("findByNameLike").findFirst().get()) == "SELECT COUNT(person_) FROM $Person.name AS person_ WHERE (person_.name LIKE :p1 AND person_.age > 18)"
        getQuery(repository.getRequiredMethod("deleteByName", String)) == "DELETE $Person.name  AS person_ WHERE (person_.name = :p1 AND person_.age > 18)"
    }

    void "test repository level @Where applies to an abstract class repository"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.tck.entities.Person;

@Repository
@Where("@.age > 18")
abstract class PersonRepository implements GenericRepository<Person, Long> {

    abstract List<Person> findByName(String name);
}
""")

        expect:
        getQuery(repository.getRequiredMethod("findByName", String)) == "SELECT person_ FROM $Person.name AS person_ WHERE (person_.name = :p1 AND person_.age > 18)"
    }

    void "test repository level @Join applies to finders"() {
        given:
        def repository = buildRepository('test.BookRepository', """
import io.micronaut.data.tck.entities.Book;

@Repository
@Join("author")
interface BookRepository extends GenericRepository<Book, Long> {

    List<Book> findByTitle(String title);
}
""")

        expect:
        getQuery(repository.getRequiredMethod("findByTitle", String)).contains("JOIN FETCH book_.author")
    }
}
