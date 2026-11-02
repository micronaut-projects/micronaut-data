from typing import Annotated

from jakarta.inject import Inject
from javax.sql import DataSource
from java.util import Optional
from java.util.concurrent import TimeUnit
from micronaut.data.connection.jdbc.advice import DelegatingDataSource
from micronaut.data.jdbc.notification import DefaultChangeEvent
from micronaut.data.jdbc.notification.postgres import PostgresChangeEventMetadata
from micronaut.data.notification import ChangeEvent, ChangeOperation
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.Book import Book
from example.BookCatalog import BookCatalog
from example.ChangeEventRecorder import ChangeEventRecorder


@MicronautTest(transactional=False)
class BookNotificationTest:
    dataSource: Annotated[DataSource, Inject]
    catalog: Annotated[BookCatalog, Inject]
    events: Annotated[ChangeEventRecorder, Inject]
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def testMigrationsAndExternalChangesMaintainTheCatalog(self):
        self.assertBook(self.catalog.find(1).orElseThrow(), Book(1, "Present before startup"))
        assert self.client.toBlocking().retrieve("/books/1", Book).title == "Present before startup"
        connection = DelegatingDataSource.unwrapDataSource(self.dataSource).getConnection()
        try:
            statement = connection.createStatement()
            try:
                statement.executeUpdate("INSERT INTO public.book VALUES (2, 'Inserted externally')")
                inserted = Book(2, "Inserted externally")
                self.assertEvent(ChangeOperation.INSERT, inserted, {}, {"id": "2"})
                self.assertBook(self.catalog.find(2).orElseThrow(), inserted)
                self.assertBook(self.client.toBlocking().retrieve("/books/2", Book), inserted)

                statement.executeUpdate("UPDATE public.book SET title = 'Title updated externally' WHERE id = 2")
                updated = Book(2, "Title updated externally")
                self.assertEvent(ChangeOperation.UPDATE, updated, {"id": "2"}, {"id": "2"})
                self.assertBook(self.catalog.find(2).orElseThrow(), updated)

                statement.executeUpdate("UPDATE public.book SET id = 3, title = 'Updated externally' WHERE id = 2")
                moved = Book(3, "Updated externally")
                self.assertEvent(ChangeOperation.UPDATE, moved, {"id": "2"}, {"id": "3"})
                assert self.catalog.find(2).isEmpty()
                self.assertBook(self.catalog.find(3).orElseThrow(), moved)

                statement.executeUpdate("DELETE FROM public.book WHERE id = 3")
                self.assertEvent(ChangeOperation.DELETE, None, {"id": "3"}, {})
                assert self.catalog.find(3).isEmpty()
                statement.execute("TRUNCATE public.book")
                self.assertEvent(ChangeOperation.INVALIDATE, None, {}, {})
                assert self.catalog.find(1).isEmpty()
            finally:
                statement.close()
        finally:
            connection.close()

    @Test
    def testInvalidationReconcilesNonemptyResultsWithoutMetadata(self):
        repository = CatalogRepository()
        preserved = Book(4, "Preserved")
        repository.rows = [Book(1, "Original"), Book(2, "Removed"), preserved]
        isolated = BookCatalog(repository)
        isolated.changed(DefaultChangeEvent(ChangeOperation.INVALIDATE, None, None))
        repository.rows = [Book(1, "Changed"), Book(3, "New"), preserved]
        assert isolated.find(1).orElseThrow().title == "Original"
        assert isolated.find(2).isPresent()
        assert isolated.find(3).isEmpty()

        isolated.changed(DefaultChangeEvent(ChangeOperation.INVALIDATE, None, None))

        assert isolated.find(1).orElseThrow().title == "Changed"
        assert isolated.find(2).isEmpty()
        assert isolated.find(3).orElseThrow().title == "New"
        self.assertBook(isolated.find(4).orElseThrow(), preserved)
        assert repository.fullReads == 2
        assert repository.idReads == 0

    @Test
    def testTerminatedCatalogReadsFromTheRepositoryAndIgnoresLaterCallbacks(self):
        repository = CatalogRepository()
        repository.rows = [Book(99, "Cached value")]
        isolated = BookCatalog(repository)
        isolated.changed(DefaultChangeEvent(ChangeOperation.INVALIDATE, None, None))
        assert isolated.find(99).orElseThrow().title == "Cached value"
        repository.rows = [Book(99, "Database value")]
        late = LateChangeEvent()

        isolated.changed(DefaultChangeEvent(ChangeOperation.TERMINATED, None, None))
        isolated.changed(late)
        isolated.changed(DefaultChangeEvent(ChangeOperation.INVALIDATE, None, None))
        assert late.entityReads == 0
        assert repository.fullReads == 1
        assert repository.idReads == 0

        assert isolated.find(99).orElseThrow().title == "Database value"
        repository.rows = [Book(99, "Changed after termination")]
        assert isolated.find(99).orElseThrow().title == "Changed after termination"
        repository.rows = []
        assert isolated.find(99).isEmpty()
        assert repository.idReads == 3
        assert repository.fullReads == 1

    def assertEvent(self, operation: ChangeOperation, entity: Book | None,
                    oldKeys: dict[str, str], newKeys: dict[str, str]) -> None:
        event = self.events.poll(10, TimeUnit.SECONDS)
        assert event is not None, "Expected " + str(operation) + " notification"
        assert event.operation() == operation
        if entity is None:
            assert event.entity().isEmpty()
        else:
            self.assertBook(event.entity().orElseThrow(), entity)
        metadata = event.metadata(PostgresChangeEventMetadata).orElseThrow()
        assert metadata.channel() == "book_changes"
        assert metadata.schema() == "public"
        assert metadata.table() == "book"
        assert metadata.oldKeys().size() == len(oldKeys)
        assert metadata.newKeys().size() == len(newKeys)
        for key, value in oldKeys.items():
            assert metadata.oldKeys().get(key) == value
        for key, value in newKeys.items():
            assert metadata.newKeys().get(key) == value

    def assertBook(self, actual: Book, expected: Book) -> None:
        assert actual.id == expected.id
        assert actual.title == expected.title


class CatalogRepository:
    """Supply current repository results independently of cached state and count catalog reads."""

    def __init__(self):
        self.rows = []
        self.fullReads = 0
        self.idReads = 0

    def findAll(self) -> list[Book]:
        self.fullReads += 1
        return self.rows

    def findById(self, id: int) -> Optional[Book]:
        self.idReads += 1
        return Optional.ofNullable(next((book for book in self.rows if book.id == id), None))


class LateChangeEvent(ChangeEvent[Book]):
    """Count entity() access itself, without requiring advice to materialize an event."""

    def __init__(self):
        self.entityReads = 0

    def operation(self) -> ChangeOperation:
        return ChangeOperation.INSERT

    def entity(self) -> Optional[Book]:
        self.entityReads += 1
        return Optional.of(Book(99, "Late callback"))

    def metadata(self, metadataType) -> Optional:
        return Optional.empty()
