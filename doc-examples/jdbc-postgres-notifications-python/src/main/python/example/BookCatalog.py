from example.Book import Book
from example.BookRepository import BookRepository
from jakarta.inject import Singleton
from java.util import Optional
from micronaut.context.event import StartupEvent
from micronaut.core.annotation import Order
from micronaut.core.order import Ordered
from micronaut.data.annotation import ChangeListener
from micronaut.data.jdbc.annotation import PostgresChangeNotification
from micronaut.data.jdbc.notification.postgres import PostgresChangeEventMetadata
from micronaut.data.notification import ChangeEvent, ChangeOperation
from micronaut.runtime.event.annotation import EventListener
from threading import RLock


# tag::listener[]
@Singleton
class BookCatalog:
    # end::listener[]
    def __init__(self, repository: BookRepository):
        self.repository = repository
        self.books = {}
        self.cacheEnabled = True
        self.lock = RLock()

    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    def loadInitialState(self, event: StartupEvent) -> None:
        with self.lock:
            self.refresh()

    # tag::listener[]
    # tag::listener-signature[]
    @ChangeListener
    # end::listener-signature[]
    @PostgresChangeNotification(channel="book_changes") # <1>
    # tag::events[]
    # tag::listener-signature[]
    def changed(self, event: ChangeEvent[Book]) -> None:
        # Handle the change.
        # end::listener[]
    # end::listener-signature[]
        with self.lock:
            if not self.cacheEnabled:
                return
            operation = event.operation()
            if operation == ChangeOperation.INSERT:
                book = event.entity().orElse(None)
                if book is not None:
                    self.books[book.id] = book
            elif operation == ChangeOperation.UPDATE:
                metadata = event.metadata(PostgresChangeEventMetadata).orElseThrow()
                # An UPDATE can change the primary key.
                self.books.pop(int(metadata.oldKeys().get("id")), None)
                book = event.entity().orElse(None)
                if book is not None:
                    self.books[book.id] = book
            elif operation == ChangeOperation.DELETE:
                metadata = event.metadata(PostgresChangeEventMetadata).orElseThrow()
                self.books.pop(int(metadata.oldKeys().get("id")), None)
            elif operation == ChangeOperation.INVALIDATE: # <1>
                self.refresh()
            elif operation == ChangeOperation.TERMINATED:
                self.cacheEnabled = False # <2>
                self.books.clear()

    # end::events[]

    def find(self, id: int) -> Optional[Book]:
        with self.lock:
            return Optional.ofNullable(self.books.get(id)) if self.cacheEnabled else self.repository.findById(id)

    def refresh(self) -> None:
        if not self.cacheEnabled:
            return
        self.books.clear()
        for book in self.repository.findAll():
            self.books[book.id] = book
