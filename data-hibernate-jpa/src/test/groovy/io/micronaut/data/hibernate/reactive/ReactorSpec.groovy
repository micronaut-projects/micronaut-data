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
package io.micronaut.data.hibernate.reactive

import io.micronaut.context.annotation.Property
import io.micronaut.data.hibernate.H2DBProperties
import io.micronaut.data.hibernate.entities.UserWithWhere
import io.micronaut.data.hibernate.reactive.ReactorPersonRepo
import io.micronaut.data.hibernate.reactive.ReactorAuthorRepository
import io.micronaut.data.hibernate.reactive.ReactorUserWithWhereRepository
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.data.tck.entities.Author
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.JoinType
import org.hibernate.SessionFactory
import spock.lang.Specification

@MicronautTest(rollback = false, packages = "io.micronaut.data.tck.entities")
@H2DBProperties
//@Property(name = 'jpa.default.properties.hibernate.show_sql', value = 'true')
class ReactorSpec extends Specification{

    @Inject
    ReactorPersonRepo reactiveRepo

    @Inject
    ReactorUserWithWhereRepository userWithWhereRepository

    @Inject
    ReactorAuthorRepository authorRepository

    @Inject
    SessionFactory sessionFactory

    void "test @where with nullable property values"() {
        when:
            userWithWhereRepository.update(new UserWithWhere(id: UUID.randomUUID(), email: null, deleted: null)).block()
        then:
            noExceptionThrown()
    }

    void "test @where on find one"() {
        when:
            def e = userWithWhereRepository.insert(new UserWithWhere(id: UUID.randomUUID(), email: null, deleted: false)).block()
            def found = userWithWhereRepository.findById(e.id).blockOptional()
        then:
            found.isPresent()
    }

    void "test @where on find one deleted"() {
        when:
            def e = userWithWhereRepository.insert(new UserWithWhere(id: UUID.randomUUID(), email: null, deleted: true)).block()
            def found = userWithWhereRepository.findById(e.id).blockOptional()
        then:
            !found.isPresent()
    }

    void "test reactive reactor CRUD"() {
        when:"An entity is saved"
        Person p = reactiveRepo.save(new Person(name: "Fred", age: 18)).block()

        then:"The entity was saved"
        p != null
        p.id != null

        when:"The entity is retrieved"
        p = reactiveRepo.findById(p.id).block()

        then:"Tne entity is found"
        p != null
        p.name == 'Fred'
        reactiveRepo.existsById(p.id).block()
        reactiveRepo.count().block() == 1

        when:"another entity is saved and all entities are listed"
        def result = reactiveRepo.saveAll([new Person(name: "Bob", age: 20), new Person(name: "Chuck", age: 30)])
                .collectList().block()
        def john = reactiveRepo.save("John", 22).block()

        def list = reactiveRepo.findAll().collectList().block()
        def withLetterO = reactiveRepo.findAllByNameContains("o").collectList().block()
        def page = reactiveRepo.findAllByAgeBetween(19, 25, Pageable.from(0)).block()
        def dto = reactiveRepo.searchByName("Bob").block()

        then:"The results are correct"
        reactiveRepo.findByName("Bob").block().name == 'Bob'
        dto.age == 20
        list.size() == 4
        page.totalSize == 2
        page.content.size() == 2
        page.content.find({it.name == 'Bob'})
        page.content.find({it.name == 'John'})
        john.name == 'John'
        result.size() == 2
        withLetterO.size() == 2

        when:"an entity is updated"
        def updated = reactiveRepo.updateByName("Bob", 50).block()
        sessionFactory.getCurrentSession().clear()

        then:"The update is executed correctly"
        updated == 1
        reactiveRepo.findByName("Bob").block().age == 50

        when:"An entity is deleted"
        reactiveRepo.deleteById(john.id).block() == null
        reactiveRepo.delete(p).block() == null
        list = reactiveRepo.findAll().collectList().block()

        then:"The results are correct"
        list.size() == 2

        when:"All are deleted"
        reactiveRepo.deleteAll().block() == null

        then:"All are gone"
        reactiveRepo.count().block() == 0
    }

    void "test reactor specification pagination with fetch join"() {
        given:
            def prefix = UUID.randomUUID().toString()
            String firstAuthorName = "${prefix}-a"
            String secondAuthorName = "${prefix}-b"
            String firstBookTitle = "${prefix}-first"
            String secondBookTitle = "${prefix}-second"
            def firstAuthor = author(firstAuthorName, firstBookTitle)
            def secondAuthor = author(secondAuthorName, secondBookTitle)
            def savedAuthors = authorRepository.saveAll([firstAuthor, secondAuthor]).collectList().block()
            QuerySpecification<Author> specification = { root, query, criteriaBuilder ->
                Join<Author, Book> books = query.resultType != Long
                        ? root.fetch("books", JoinType.LEFT) as Join<Author, Book>
                        : root.join("books", JoinType.LEFT)
                criteriaBuilder.or(
                        root.get("name").in(firstAuthorName, secondAuthorName),
                        books.get("title").in(firstBookTitle, secondBookTitle)
                )
            } as QuerySpecification<Author>

        when:
            def page0 = authorRepository.findAll(specification, Pageable.from(0, 1, Sort.of(Sort.Order.asc("name")))).block()
            def page1 = authorRepository.findAll(specification, Pageable.from(1, 1, Sort.of(Sort.Order.asc("name")))).block()

        then:
            page0.size == 1
            page0.totalSize == 2
            page0.content*.name == [firstAuthorName]
            page0.content[0].books*.title == [firstBookTitle]
            page1.size == 1
            page1.totalSize == 2
            page1.content*.name == [secondAuthorName]
            page1.content[0].books*.title == [secondBookTitle]

        cleanup:
            authorRepository.deleteAll(savedAuthors).block()
    }

    private static Author author(String name, String bookTitle) {
        def author = new Author()
        author.name = name
        def book = new Book()
        book.title = bookTitle
        book.author = author
        author.books.add(book)
        return author
    }
}
