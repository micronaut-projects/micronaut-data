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
