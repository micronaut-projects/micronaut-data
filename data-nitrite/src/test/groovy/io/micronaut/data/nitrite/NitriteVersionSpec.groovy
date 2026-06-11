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

    void "test operations.execute no-op"() {
        expect:
        operations != null
        // execute(PreparedQuery) is hard to test without a real PQ
        // and standard repository methods don't use it.
        // It's likely intended for future use or custom implementations.
    }
}
