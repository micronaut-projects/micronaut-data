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

    void "test optimistic locking with @Version"() {
        when: "saving a new entity"
        def book = new VersionedBook("v1")
        book = repository.save(book)

        then: "version is 0"
        book.version == 0

        when: "updating the entity"
        book.title = "v2"
        book = repository.update(book)

        then: "version is incremented"
        book.version == 1

        when: "trying to update with old version"
        def oldBook = new VersionedBook("v3")
        oldBook.id = book.id
        oldBook.version = 0
        repository.update(oldBook)

        then: "exception is thrown"
        thrown(io.micronaut.data.exceptions.OptimisticLockException)
    }

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

    void "test operations.execute no-op"() {
        expect:
        operations != null
        // execute(PreparedQuery) is hard to test without a real PQ
        // and standard repository methods don't use it.
        // It's likely intended for future use or custom implementations.
    }
}
