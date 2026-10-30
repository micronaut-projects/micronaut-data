package example.notification

import io.micronaut.data.notification.ChangeOperation
import io.micronaut.data.jdbc.notification.DefaultChangeEvent
import io.micronaut.data.jdbc.notification.DeferredChangeEvent
import io.micronaut.data.model.geo.Point
import spock.lang.Specification

import java.util.function.Supplier

class LibraryCacheTerminationSpec extends Specification {

    void "terminated cache uses database reads and ignores later callbacks (#name)"() {
        given:
        def repository = Mock(LibraryRepository)
        def original = new Library(1L, 'Library', 'original@example.com', 15000, new Point(20, 44))
        def updated = new Library(1L, 'Library', 'updated@example.com', 15000, new Point(20, 44))
        def latest = new Library(1L, 'Library', 'latest@example.com', 15000, new Point(20, 44))
        def rows = [original]
        int reads = 0
        repository.findAll() >> { reads++; rows }
        repository.findByCapacityGreaterThanEquals(10000) >> { reads++; rows }
        def cache = factory.call(repository)
        cache.initialize()
        assert cache.find('Library').orElseThrow() == original
        assert reads == 1
        boolean loadedLateEntity = false
        def lateUpdate = new DeferredChangeEvent<Library>(ChangeOperation.UPDATE, null, {
            loadedLateEntity = true
            original
        } as Supplier<Library>)

        when:
        cache.onLibraryChanged(new DefaultChangeEvent<Library>(ChangeOperation.TERMINATED, null, null))
        rows = [updated]
        cache.onLibraryChanged(lateUpdate)
        cache.onLibraryChanged(new DefaultChangeEvent<Library>(ChangeOperation.INVALIDATE, null, null))

        then:
        !loadedLateEntity
        reads == 1

        when:
        def firstRead = cache.find('Library').orElseThrow()
        rows = [latest]
        def secondRead = cache.find('Library').orElseThrow()

        then:
        firstRead == updated
        secondRead == latest
        reads == 3

        where:
        name     | factory
        'object' | { LibraryRepository repo -> new LibraryCache(repo) }
        'query'  | { LibraryRepository repo -> new LargeLibraryCache(repo) }
    }
}
