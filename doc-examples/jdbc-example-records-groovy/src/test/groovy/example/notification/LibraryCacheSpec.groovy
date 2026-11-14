package example.notification

import io.micronaut.context.ApplicationContext
import io.micronaut.data.jdbc.operations.DefaultJdbcRepositoryOperations
import io.micronaut.data.jdbc.runtime.ConnectionCallback
import io.micronaut.data.model.geo.Point
import io.micronaut.data.jdbc.notification.oracle.OracleChangeEventMetadata
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.notification.ChangeOperation
import org.jspecify.annotations.Nullable
import spock.lang.Specification

import java.sql.Statement
import java.util.concurrent.TimeUnit

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
            def repository = context.getBean(LibraryRepository)
            def cache = context.getBean(LibraryCache)
            def events = context.getBean(ChangeEventRecorder)
            def library1 = cache.find('Library 1').orElseThrow()
            def library2 = cache.find('Library 2').orElseThrow()
            def library3 = cache.find('Library 3').orElseThrow()
            assert library1.email() == 'library1@example.com'
            assert library2.capacity() == 9000
            assert library3.capacity() == 10000
            assert cache.find('Library 4').orElseThrow().location() == new Point(20.68960, 44.01650)

            def renamed = new Library(library1.id(), 'Library 1 Updated', library1.email(), library1.capacity(), library1.location())
            repository.save(renamed)
            assertRowEvent(events, ChangeOperation.UPDATE, renamed)
            assert cache.find(renamed.name()).orElseThrow() == renamed

            def expanded = new Library(library2.id(), library2.name(), library2.email(), 12000, library2.location())
            repository.save(expanded)
            assertRowEvent(events, ChangeOperation.UPDATE, expanded)
            assert cache.find(expanded.name()).orElseThrow() == expanded

            def relocated = new Library(library3.id(), library3.name(), library3.email(), library3.capacity(), new Point(20.91140, 44.81250))
            repository.save(relocated)
            assertRowEvent(events, ChangeOperation.UPDATE, relocated)
            assert cache.find(relocated.name()).orElseThrow() == relocated

            def inserted = repository.save(new Library(null, 'Library 5', 'library5@example.com', 8000, new Point(21.16560, 44.77220)))
            def insert = assertRowEvent(events, ChangeOperation.INSERT, inserted)
            assert cache.find(inserted.name()).orElseThrow() == inserted

            repository.deleteById(inserted.id())
            def delete = assertRowEvent(events, ChangeOperation.DELETE, null)
            assert delete.metadata(OracleChangeEventMetadata).orElseThrow().rowId() == insert.metadata(OracleChangeEventMetadata).orElseThrow().rowId()
            assert cache.find(inserted.name()).isEmpty()

            truncateLibraries(context)
            assertInvalidation(events)
            assert [renamed.name(), expanded.name(), relocated.name(), 'Library 4'].every { cache.find(it).isEmpty() }
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
            def repository = context.getBean(LibraryRepository)
            def cache = context.getBean(LibraryNameCache)
            def events = context.getBean(ChangeEventRecorder)
            assert !cache.contains('Library 1')
            assert !cache.contains('Library 2')
            assert cache.contains('Library 3')
            def library3 = repository.findAll().find { it.name() == 'Library 3' }
            assert cache.contains('Library 4')
            def library4 = repository.findAll().find { it.name() == 'Library 4' }
            def library1 = repository.findAll().find { it.name() == 'Library 1' }

            def entered = new Library(library1.id(), library1.name(), library1.email(), 10000, library1.location())
            repository.save(entered)
            assertRowEvent(events, ChangeOperation.UPDATE, entered)
            assert cache.contains(entered.name())
            assert cache.find(entered.id()).orElseThrow() == entered.name()

            // The event reloads the complete entity; the cache retains only its ID and name.
            def updated = new Library(library4.id(), 'Library 4 Updated', 'updated@example.com', 16000, new Point(20.91140, 44.81250))
            repository.save(updated)
            assertRowEvent(events, ChangeOperation.UPDATE, updated)
            assert cache.contains(updated.name())
            assert !cache.contains(library4.name())
            assert cache.find(updated.id()).orElseThrow() == updated.name()

            def inserted = repository.save(new Library(null, 'Library 5', 'library5@example.com', 12000, new Point(21.16560, 44.77220)))
            assertRowEvent(events, ChangeOperation.INSERT, inserted)
            assert cache.contains(inserted.name())

            def left = new Library(library3.id(), library3.name(), library3.email(), 9000, library3.location())
            repository.save(left)
            assertRowEvent(events, ChangeOperation.UPDATE, left)
            assert !cache.contains(left.name())
            assert cache.find(left.id()).isEmpty()

            repository.deleteById(updated.id())
            assertRowEvent(events, ChangeOperation.DELETE, null)
            assert !cache.contains(updated.name())

            truncateLibraries(context)
            assertInvalidation(events)
            assert !cache.contains(entered.name())
            assert !cache.contains(inserted.name())
        }

        then:
        noExceptionThrown()
    }

    def 'query change notifications ignore changes outside the filter'() {
        when:
        withContext([
            'query-notification.query.enabled': 'true',
            'datasources.default.schema-generate': 'NONE'
        ]) { ApplicationContext context ->
            def repository = context.getBean(LibraryRepository)
            def cache = context.getBean(LibraryNameCache)
            def events = context.getBean(ChangeEventRecorder)
            assert cache.contains('Library 3')
            def included = repository.findAll().find { it.name() == 'Library 3' }
            def control = new Library(included.id(), 'Library 3 Included', included.email(), included.capacity(), included.location())
            repository.save(control)
            assertRowEvent(events, ChangeOperation.UPDATE, control)

            def excluded = repository.findAll().find { it.name() == 'Library 1' }
            repository.save(new Library(excluded.id(), 'Library 1 Still Excluded', excluded.email(), 9999, excluded.location()))
            assert events.poll(1, TimeUnit.SECONDS) == null: 'An update outside the registered query must not invoke the listener'
            assert !cache.contains('Library 1 Still Excluded')

            repository.save(new Library(null, 'Excluded Insert', 'excluded@example.com', 9999, new Point(20, 44)))
            assert events.poll(1, TimeUnit.SECONDS) == null: 'An insert outside the registered query must not invoke the listener'
            assert !cache.contains('Excluded Insert')
        }

        then:
        noExceptionThrown()
    }

    private static ChangeEvent<Library> nextEvent(ChangeEventRecorder events, ChangeOperation operation) {
        def event = events.poll(10, TimeUnit.SECONDS)
        assert event != null: "Expected $operation notification"
        assert event.operation() == operation
        event
    }

    private static ChangeEvent<Library> assertRowEvent(ChangeEventRecorder events, ChangeOperation operation, @Nullable Library entity) {
        def event = nextEvent(events, operation)
        assert event.entity().orElse(null) == entity
        assert !event.metadata(OracleChangeEventMetadata).orElseThrow().rowId().isBlank()
        event
    }

    private static void assertInvalidation(ChangeEventRecorder events) {
        def event = nextEvent(events, ChangeOperation.INVALIDATE)
        assert event.entity().isEmpty()
        assert event.metadata(OracleChangeEventMetadata).isEmpty()
    }

    private static void truncateLibraries(ApplicationContext context) {
        ConnectionCallback<Boolean> callback = { connection ->
            Statement statement = connection.createStatement()
            try {
                statement.execute('TRUNCATE TABLE LIBRARY')
            } finally {
                statement.close()
            }
            true
        } as ConnectionCallback<Boolean>
        context.getBean(DefaultJdbcRepositoryOperations).execute(callback)
    }

    private static <T> T withContext(Map<String, Object> properties, Closure<T> action) {
        ApplicationContext context = ApplicationContext.run(properties, 'oracle-cqn')
        try {
            action.call(context)
        } finally {
            context.close()
        }
    }

}
