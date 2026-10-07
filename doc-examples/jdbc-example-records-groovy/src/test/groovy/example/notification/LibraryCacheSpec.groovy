package example.notification

import io.micronaut.context.ApplicationContext
import io.micronaut.data.jdbc.operations.DefaultJdbcRepositoryOperations
import io.micronaut.data.jdbc.runtime.ConnectionCallback
import io.micronaut.data.model.geo.Point
import spock.lang.Specification

import java.sql.Statement

class LibraryCacheSpec extends Specification {

    def setupSpec() {
        withContext([
            'datasources.default.username': 'system',
            'datasources.default.password': 'test',
            'datasources.default.schema-generate': 'NONE'
        ]) { ApplicationContext context ->
            ConnectionCallback<Boolean> callback = { connection ->
                Statement statement = connection.createStatement()
                try {
                    statement.execute('GRANT CHANGE NOTIFICATION TO test')
                } finally {
                    statement.close()
                }
                true
            } as ConnectionCallback<Boolean>
            context.getBean(DefaultJdbcRepositoryOperations).execute(callback)
        }
    }

    def setup() {
        withContext(['datasources.default.schema-generate': 'CREATE']) { ApplicationContext context ->
            LibraryRepository repository = context.getBean(LibraryRepository)
            repository.save(new Library(null, 'Library 1', 'library1@example.com', 5000, new Point(20.46513, 44.80401)))
            repository.save(new Library(null, 'Library 2', 'library2@example.com', 9000, new Point(19.83355, 45.26714)))
            repository.save(new Library(null, 'Library 3', 'library3@example.com', 10000, new Point(21.89830, 43.32090)))
            repository.save(new Library(null, 'Library 4', 'library4@example.com', 15000, new Point(20.68960, 44.01650)))
        }
    }

    def cleanup() {
        withContext(['datasources.default.schema-generate': 'NONE']) { ApplicationContext context ->
            context.getBean(LibraryRepository).deleteAll()
        }
    }

    def 'test object change notifications'() {
        when:
        withContext([
            'query-notification.object.enabled': 'true',
            'datasources.default.schema-generate': 'NONE'
        ]) { ApplicationContext context ->
            LibraryRepository repository = context.getBean(LibraryRepository)
            LibraryCache libraryCache = context.getBean(LibraryCache)

            Library library1 = libraryCache.find('Library 1').orElseThrow()
            Library library2 = libraryCache.find('Library 2').orElseThrow()
            Library library3 = libraryCache.find('Library 3').orElseThrow()
            Library library4 = libraryCache.find('Library 4').orElseThrow()

            assert library1.email() == 'library1@example.com'
            assert library2.capacity() == 9000
            assert library3.capacity() == 10000
            assert library4.location() == new Point(20.68960, 44.01650)

            repository.save(new Library(library1.id(), 'Library 1 Updated', library1.email(), library1.capacity(), library1.location()))
            waitUntil { libraryCache.find('Library 1 Updated').isPresent() }
            assert libraryCache.find('Library 1 Updated').orElseThrow().id() == library1.id()

            repository.save(new Library(library2.id(), library2.name(), library2.email(), 12000, library2.location()))
            waitUntil { libraryCache.find('Library 2').orElseThrow().capacity() == 12000 }
            assert libraryCache.find('Library 2').orElseThrow().id() == library2.id()

            Point updatedLocation = new Point(20.91140, 44.81250)
            repository.save(new Library(library3.id(), library3.name(), library3.email(), library3.capacity(), updatedLocation))
            waitUntil { libraryCache.find('Library 3').orElseThrow().location() == updatedLocation }
            assert libraryCache.find('Library 3').orElseThrow().id() == library3.id()

            Library library5 = repository.save(new Library(null, 'Library 5', 'library5@example.com', 8000, new Point(21.16560, 44.77220)))
            waitUntil { libraryCache.find('Library 5').isPresent() }
            assert libraryCache.find('Library 5').orElseThrow().id() == library5.id()
        }

        then:
        noExceptionThrown()
    }

    def 'test query change notifications'() {
        when:
        withContext([
            'query-notification.query.enabled': 'true',
            'datasources.default.schema-generate': 'NONE'
        ]) { ApplicationContext context ->
            LibraryRepository repository = context.getBean(LibraryRepository)
            CustomLibraryCache libraryCache = context.getBean(CustomLibraryCache)

            assert !libraryCache.find('Library 1').isPresent()
            assert !libraryCache.find('Library 2').isPresent()
            Library library3 = libraryCache.find('Library 3').orElseThrow()
            Library library4 = libraryCache.find('Library 4').orElseThrow()
            assert library3.capacity() == 10000
            assert library4.capacity() == 15000

            Library library1 = repository.findAll().find { it.name() == 'Library 1' }
            repository.save(new Library(library1.id(), library1.name(), library1.email(), 10000, library1.location()))
            waitUntil { libraryCache.find(library1.name()).isPresent() }
            assert libraryCache.find(library1.name()).orElseThrow().id() == library1.id()

            repository.save(new Library(library3.id(), library3.name(), library3.email(), 9000, library3.location()))
            waitUntil { !libraryCache.find(library3.name()).isPresent() }
        }

        then:
        noExceptionThrown()
    }

    private static <T> T withContext(Map<String, Object> properties, Closure<T> action) {
        ApplicationContext context = ApplicationContext.run(properties)
        try {
            action.call(context)
        } finally {
            context.close()
        }
    }

    private static void waitUntil(Closure<Boolean> condition) {
        long end = System.currentTimeMillis() + 10000
        while (System.currentTimeMillis() < end) {
            Thread.sleep(50)
            if (condition.call()) {
                return
            }
        }
        throw new IllegalStateException('Condition was not fulfilled within 10 seconds')
    }
}
