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

}
