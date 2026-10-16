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
package io.micronaut.data.jdbc.h2

import io.micronaut.context.annotation.Property
import io.micronaut.data.jdbc.operations.DefaultJdbcRepositoryOperations
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.tck.entities.Author
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

/**
 * The flat entity mapping switched on for a data source: an entity is read with the typed getters, through the
 * mapper the annotation processor generated for it when there is one, and reads the same rows as the general mapper.
 */
@MicronautTest
@H2DBProperties
@Property(name = "datasources.default.flat-entity-mapping", value = "true")
class H2FlatEntityMappingSpec extends Specification {

    @Inject
    @Shared
    H2PersonRepository personRepository

    @Inject
    @Shared
    H2BookRepository bookRepository

    @Inject
    @Shared
    H2AuthorRepository authorRepository

    @Inject
    @Shared
    DefaultJdbcRepositoryOperations operations

    void "reads flat entities with the typed mapper"() {
        given:
        personRepository.deleteAll()
        def withIncome = personRepository.save(new Person(name: "Fred", age: 30, income: 1500.5d))
        def withoutIncome = personRepository.save(new Person(name: "Barney", age: 0, enabled: false, income: null))

        when:
        List<Person> all = personRepository.findAll().sort { it.name }
        Person one = personRepository.findById(withoutIncome.id).get()
        Person byName = personRepository.findByName("Fred")
        List<Person> streamed = personRepository.findAll(Pageable.from(0, 10)).content.sort { it.name }

        then:
        all*.name == ["Barney", "Fred"]
        all*.age == [0, 30]
        all*.enabled == [false, true]
        all*.income == [null, 1500.5d]
        one.name == "Barney"
        one.income == null
        !one.enabled
        byName.id == withIncome.id
        byName.income == 1500.5d
        streamed*.name == ["Barney", "Fred"]

        and: "the operations resolved a plan for the flat entity"
        plans()[operations.getEntity(Person)].present
    }

    void "entities with associations are read by the generated mapper"() {
        given:
        def author = authorRepository.save(new Author(name: "Stephen King"))
        bookRepository.save(new Book(title: "It", totalPages: 1138, author: author))

        when:
        List<Book> books = bookRepository.findAll()

        then:
        books.size() == 1
        books[0].title == "It"
        books[0].author.id == author.id

        and: "the plan of the entity with associations holds the mapper the annotation processor generated"
        plans()[operations.getEntity(Book)].get().generatedMapper != null
        plans()[operations.getEntity(Person)].get().generatedMapper != null

        cleanup:
        bookRepository.deleteAll()
        authorRepository.deleteAll()
    }

    private Map<RuntimePersistentEntity<?>, Optional<?>> plans() {
        operations.flatEntityMappingPlans
    }
}
