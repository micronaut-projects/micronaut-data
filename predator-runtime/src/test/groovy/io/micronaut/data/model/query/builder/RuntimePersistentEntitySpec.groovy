/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.model.query.builder

import io.micronaut.data.annotation.Relation
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.entities.Author
import io.micronaut.data.model.entities.Book
import io.micronaut.data.model.entities.Person
import spock.lang.Specification

class RuntimePersistentEntitySpec extends Specification {

    void "test runtime entity"() {
        given:
        PersistentEntity entity = PersistentEntity.of(Person)

        expect:
        entity.name == Person.name
        entity.identity
        entity.identity.name == 'id'
        entity.persistentProperties
        entity.getPropertyByName("name")
        !entity.getPropertyByName("name").isReadOnly()
        entity.getPropertyByName("someId").isReadOnly()
    }

    void "test associations"() {
        given:
        PersistentEntity author = PersistentEntity.of(Author)
        PersistentEntity book = PersistentEntity.of(Book)

        expect:
        author.associations.size() == 1
        author.associations[0].name == 'books'
        author.associations[0].kind == Relation.Kind.ONE_TO_MANY
        book.associations.size() == 2
        book.associations[0].name == 'author'
        book.associations[0].kind == Relation.Kind.MANY_TO_ONE
    }

    void "test getPath"() {
        given:
        PersistentEntity author = PersistentEntity.of(Author)
        PersistentEntity book = PersistentEntity.of(Book)

        expect:
        book.getPath("authorName").isPresent()
        book.getPath("authorName").get() == 'author.name'
        author.getPath("booksTitle").isPresent()
        author.getPath("booksTitle").get() == 'books.title'
        author.getPath("booksPublisherZipCode").get() == 'books.publisher.zipCode'
    }
}
