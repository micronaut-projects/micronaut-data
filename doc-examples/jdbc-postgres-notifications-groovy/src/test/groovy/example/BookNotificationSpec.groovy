package example

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.notification.DefaultChangeEvent
import io.micronaut.data.notification.ChangeOperation
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.sql.DataSource

@MicronautTest(transactional = false)
class BookNotificationSpec extends Specification {
    @Inject DataSource dataSource
    @Inject BookCatalog catalog
    @Inject BookRepository repository
    @Inject @Client("/") HttpClient client

    void "migrations and external changes maintain the catalog"() {
        given:
        def conditions = new PollingConditions(timeout: 10)
        assert catalog.find(1L).orElseThrow().title() == "Present before startup"
        assert client.toBlocking().retrieve("/books/1", Book).title() == "Present before startup"

        expect:
        try (def connection = DelegatingDataSource.unwrapDataSource(dataSource).connection;
             def statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO public.book VALUES (2, 'Inserted externally')")
            conditions.eventually {
                assert catalog.find(2L).orElseThrow().title() == "Inserted externally"
            }
            assert client.toBlocking().retrieve("/books/2", Book).title() == "Inserted externally"
            statement.executeUpdate("UPDATE public.book SET id = 3, title = 'Updated externally' WHERE id = 2")
            conditions.eventually {
                assert catalog.find(2L).isEmpty()
                assert catalog.find(3L).orElseThrow().title() == "Updated externally"
            }
            statement.executeUpdate("DELETE FROM public.book WHERE id = 3")
            conditions.eventually { assert catalog.find(3L).isEmpty() }
            statement.execute("TRUNCATE public.book")
            conditions.eventually { assert catalog.find(1L).isEmpty() }
        }
    }

    void "a terminated catalog reads from the database and ignores later callbacks"() {
        given:
        def isolated = new BookCatalog(repository)
        def connection = DelegatingDataSource.unwrapDataSource(dataSource).connection
        def statement = connection.createStatement()
        statement.executeUpdate("INSERT INTO public.book VALUES (99, 'Database value')")
        isolated.changed(new DefaultChangeEvent(ChangeOperation.INSERT, new Book(99L, 'Cached value'), null))
        assert isolated.find(99L).orElseThrow().title() == 'Cached value'

        when:
        isolated.changed(new DefaultChangeEvent(ChangeOperation.TERMINATED, null, null))
        isolated.changed(new DefaultChangeEvent(ChangeOperation.INSERT, new Book(99L, 'Late callback'), null))
        isolated.changed(new DefaultChangeEvent(ChangeOperation.INVALIDATE, null, null))

        then:
        isolated.find(99L).orElseThrow().title() == 'Database value'

        when:
        statement.executeUpdate("UPDATE public.book SET title = 'Changed after termination' WHERE id = 99")

        then:
        isolated.find(99L).orElseThrow().title() == 'Changed after termination'

        when:
        statement.executeUpdate('DELETE FROM public.book WHERE id = 99')

        then:
        isolated.find(99L).empty

        cleanup:
        statement?.executeUpdate('DELETE FROM public.book WHERE id = 99')
        statement?.close()
        connection?.close()
    }
}
