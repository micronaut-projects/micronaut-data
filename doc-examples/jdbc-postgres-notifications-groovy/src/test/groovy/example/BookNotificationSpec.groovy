package example

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.notification.DefaultChangeEvent
import io.micronaut.data.jdbc.notification.postgres.PostgresChangeEventMetadata
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.notification.ChangeOperation
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import javax.sql.DataSource
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@MicronautTest(transactional = false)
class BookNotificationSpec extends Specification {
    @Inject DataSource dataSource
    @Inject BookCatalog catalog
    @Inject ChangeEventRecorder events
    @Inject @Client("/") HttpClient client

    void "migrations and external changes maintain the catalog"() {
        expect:
        catalog.find(1L).orElseThrow() == new Book(1L, 'Present before startup')
        client.toBlocking().retrieve('/books/1', Book).title() == 'Present before startup'
        try (def connection = DelegatingDataSource.unwrapDataSource(dataSource).connection;
             def statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO public.book VALUES (2, 'Inserted externally')")
            def inserted = new Book(2L, 'Inserted externally')
            assertEvent(ChangeOperation.INSERT, inserted, [:], [id: '2'])
            assert catalog.find(2L).orElseThrow() == inserted
            assert client.toBlocking().retrieve('/books/2', Book) == inserted

            statement.executeUpdate("UPDATE public.book SET title = 'Title updated externally' WHERE id = 2")
            def updated = new Book(2L, 'Title updated externally')
            assertEvent(ChangeOperation.UPDATE, updated, [id: '2'], [id: '2'])
            assert catalog.find(2L).orElseThrow() == updated

            statement.executeUpdate("UPDATE public.book SET id = 3, title = 'Updated externally' WHERE id = 2")
            def moved = new Book(3L, 'Updated externally')
            assertEvent(ChangeOperation.UPDATE, moved, [id: '2'], [id: '3'])
            assert catalog.find(2L).empty
            assert catalog.find(3L).orElseThrow() == moved

            statement.executeUpdate('DELETE FROM public.book WHERE id = 3')
            assertEvent(ChangeOperation.DELETE, null, [id: '3'], [:])
            assert catalog.find(3L).empty
            statement.execute('TRUNCATE public.book')
            assertEvent(ChangeOperation.INVALIDATE, null, [:], [:])
            assert catalog.find(1L).empty
        }
    }

    void "invalidation reconciles nonempty results without metadata"() {
        given:
        def repository = Mock(BookRepository)
        def preserved = new Book(4L, 'Preserved')
        def rows = [new Book(1L, 'Original'), new Book(2L, 'Removed'), preserved]
        int fullReads = 0
        repository.findAll() >> { fullReads++; rows }
        def isolated = new BookCatalog(repository)
        isolated.changed(new DefaultChangeEvent(ChangeOperation.INVALIDATE, null, null))
        rows = [new Book(1L, 'Changed'), new Book(3L, 'New'), preserved]
        assert isolated.find(1L).orElseThrow().title() == 'Original'
        assert isolated.find(2L).present
        assert isolated.find(3L).empty

        when:
        isolated.changed(new DefaultChangeEvent(ChangeOperation.INVALIDATE, null, null))

        then:
        isolated.find(1L).orElseThrow().title() == 'Changed'
        isolated.find(2L).empty
        isolated.find(3L).orElseThrow().title() == 'New'
        isolated.find(4L).orElseThrow() == preserved
        fullReads == 2
        0 * repository.findById(_)
    }

    void "a terminated catalog reads from the repository and ignores later callbacks"() {
        given:
        def repository = Mock(BookRepository)
        def rows = [new Book(99L, 'Cached value')]
        int fullReads = 0
        int idReads = 0
        repository.findAll() >> { fullReads++; rows }
        repository.findById(99L) >> { idReads++; Optional.ofNullable(rows.find { it.id() == 99L }) }
        def isolated = new BookCatalog(repository)
        isolated.changed(new DefaultChangeEvent(ChangeOperation.INVALIDATE, null, null))
        assert isolated.find(99L).orElseThrow().title() == 'Cached value'
        rows = [new Book(99L, 'Database value')]
        def entityReads = new AtomicInteger()
        // Count entity() access itself, without depending on listener advice to materialize an event.
        def late = [
            operation: { ChangeOperation.INSERT },
            entity: { entityReads.incrementAndGet(); Optional.of(new Book(99L, 'Late callback')) },
            metadata: { Class type -> Optional.empty() }
        ] as ChangeEvent<Book>

        when:
        isolated.changed(new DefaultChangeEvent(ChangeOperation.TERMINATED, null, null))
        isolated.changed(late)
        isolated.changed(new DefaultChangeEvent(ChangeOperation.INVALIDATE, null, null))

        then:
        entityReads.get() == 0
        fullReads == 1
        idReads == 0
        isolated.find(99L).orElseThrow().title() == 'Database value'

        when:
        rows = [new Book(99L, 'Changed after termination')]

        then:
        isolated.find(99L).orElseThrow().title() == 'Changed after termination'

        when:
        rows = []

        then:
        isolated.find(99L).empty
        idReads == 3
        fullReads == 1
    }

    private void assertEvent(ChangeOperation operation, Book entity,
                             Map<String, String> oldKeys, Map<String, String> newKeys) {
        def event = events.poll(10, TimeUnit.SECONDS)
        assert event != null: "Expected $operation notification"
        assert event.operation() == operation
        assert event.entity().orElse(null) == entity
        def metadata = event.metadata(PostgresChangeEventMetadata).orElseThrow()
        assert metadata.channel() == 'book_changes'
        assert metadata.schema() == 'public'
        assert metadata.table() == 'book'
        assert metadata.oldKeys() == oldKeys
        assert metadata.newKeys() == newKeys
    }
}
