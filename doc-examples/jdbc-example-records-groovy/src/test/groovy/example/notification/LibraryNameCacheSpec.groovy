package example.notification

import io.micronaut.data.jdbc.notification.DefaultChangeEvent
import io.micronaut.data.jdbc.notification.DeferredChangeEvent
import io.micronaut.data.model.geo.Point
import io.micronaut.data.notification.ChangeOperation
import spock.lang.Specification

import java.util.function.Supplier

class LibraryNameCacheSpec extends Specification {

    void "inserts and updates change names by ID without refreshing the directory"() {
        given:
        def repository = Mock(LibraryRepository)
        repository.findByCapacityGreaterThanEquals(10000) >> [library(1, 'Shared'), library(2, 'Shared')]
        def cache = new LibraryNameCache(repository)
        cache.initialize()

        when:
        cache.onLibraryChanged(event(ChangeOperation.INSERT, library(3, 'New')))
        cache.onLibraryChanged(event(ChangeOperation.UPDATE, library(1, 'Renamed')))

        then:
        cache.find(1).orElseThrow() == 'Renamed'
        cache.find(2).orElseThrow() == 'Shared'
        cache.find(3).orElseThrow() == 'New'
        cache.contains('Shared')
        0 * repository._

        when:
        cache.onLibraryChanged(event(ChangeOperation.UPDATE, library(2, 'Shared', 9999)))
        cache.onLibraryChanged(event(ChangeOperation.INSERT, library(4, 'Excluded', 9999)))

        then:
        cache.find(2).empty
        cache.find(4).empty
        !cache.contains('Shared')
        !cache.contains('Excluded')
        cache.find(1).orElseThrow() == 'Renamed'
        0 * repository._
    }

    void "#operation refreshes names and preserves remaining libraries with duplicate names"() {
        given:
        def repository = Mock(LibraryRepository)
        def rows = [library(1, 'Shared'), library(2, 'Shared')]
        repository.findByCapacityGreaterThanEquals(10000) >> { rows }
        def cache = new LibraryNameCache(repository)
        cache.initialize()
        rows = [library(2, 'Shared'), library(3, 'New')]

        when:
        cache.onLibraryChanged(event(operation, null))

        then:
        cache.find(1).empty
        cache.find(2).orElseThrow() == 'Shared'
        cache.find(3).orElseThrow() == 'New'
        cache.contains('Shared')

        where:
        operation << [ChangeOperation.DELETE, ChangeOperation.INVALIDATE]
    }

    void "termination uses current filtered database reads and ignores late callbacks"() {
        given:
        def repository = Mock(LibraryRepository)
        def original = library(1, 'Original')
        def current = original
        def rows = [original]
        int reads = 0
        repository.findByCapacityGreaterThanEquals(10000) >> { reads++; rows }
        repository.findById(1L) >> { reads++; Optional.ofNullable(current) }
        def cache = new LibraryNameCache(repository)
        cache.initialize()
        assert cache.find(1).orElseThrow() == 'Original'
        boolean loadedLateEntity = false
        def lateUpdate = new DeferredChangeEvent<Library>(ChangeOperation.UPDATE, null, {
            loadedLateEntity = true
            original
        } as Supplier<Library>)

        when:
        cache.onLibraryChanged(event(ChangeOperation.TERMINATED, null))
        cache.onLibraryChanged(lateUpdate)
        cache.onLibraryChanged(event(ChangeOperation.INVALIDATE, null))

        then:
        !loadedLateEntity
        reads == 1

        when:
        current = library(1, 'Renamed')
        rows = [current]

        then:
        cache.find(1).orElseThrow() == 'Renamed'
        cache.contains('Renamed')

        when:
        current = library(1, 'Renamed', 9999)
        rows = []

        then:
        cache.find(1).empty
        !cache.contains('Renamed')
        reads == 5
    }

    private static Library library(long id, String name, int capacity = 15000) {
        new Library(id, name, 'library@example.com', capacity, new Point(20, 44))
    }

    private static DefaultChangeEvent<Library> event(ChangeOperation operation, Library library) {
        new DefaultChangeEvent<Library>(operation, library, null)
    }
}
