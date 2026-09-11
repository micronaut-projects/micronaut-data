package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.VersionedBook
import io.micronaut.data.nitrite.repository.VersionedBookRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class NitriteVersionSpec extends Specification {

    @Inject
    VersionedBookRepository repository

    @Inject
    io.micronaut.data.nitrite.runtime.DefaultNitriteRepositoryOperations operations

    void "optimistic locking uses the version parameter even when the method name has no version text"() {
        given:
        def book = repository.save(new VersionedBook("v1"))

        when:
        def updated = repository.update(book.id, book.version, "v2")

        then:
        updated == 1
        repository.findById(book.id).get().title == "v2"
        repository.findById(book.id).get().version == 1

        when:
        repository.update(book.id, 0L, "stale")

        then:
        thrown(io.micronaut.data.exceptions.OptimisticLockException)
    }

    void "an entity update carries the version into the identity fallback filter"() {
        given:
        def book = repository.save(new VersionedBook("v1"))

        when:
        book.title = "v2"
        repository.update(book)

        then:
        book.version == 1
        repository.findById(book.id).get().title == "v2"

        when: "an entity holding a stale version is written back"
        def stale = new VersionedBook("stale")
        stale.id = book.id
        stale.version = 0L
        repository.update(stale)

        then:
        thrown(io.micronaut.data.exceptions.OptimisticLockException)
    }

    void "a batch entity update carries the version into the identity fallback filter"() {
        given:
        def books = repository.saveAll([new VersionedBook("a"), new VersionedBook("b")]).toList()

        when:
        books.each { it.title = it.title + "-updated" }
        repository.updateAll(books)

        then:
        books.every { it.version == 1 }
        repository.findById(books[0].id).get().title == "a-updated"
        repository.findById(books[1].id).get().title == "b-updated"

        when: "one entity of the batch holds a stale version"
        def stale = new VersionedBook("stale")
        stale.id = books[0].id
        stale.version = 0L
        repository.updateAll([stale])

        then:
        thrown(io.micronaut.data.exceptions.OptimisticLockException)
    }

    void "deleting a versioned entity carries the version into the identity fallback filter"() {
        given:
        def book = repository.save(new VersionedBook("doomed"))

        when:
        repository.delete(book)

        then:
        repository.findById(book.id).isEmpty()
    }
}
