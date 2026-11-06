from example.Book import Book
from example.BookRepository import BookRepository
from jakarta.inject import Singleton
from java.lang import Long
from java.util import Optional
from java.util.concurrent import ConcurrentHashMap
from java.util.concurrent.atomic import AtomicBoolean, AtomicReference
from micronaut.context.event import StartupEvent
from micronaut.core.annotation import Order
from micronaut.core.order import Ordered
from micronaut.data.annotation import ChangeListener
from micronaut.data.jdbc.annotation import PostgresChangeNotification
from micronaut.data.jdbc.notification.postgres import PostgresChangeEventMetadata
from micronaut.data.notification import ChangeEvent, ChangeOperation
from micronaut.runtime.event.annotation import EventListener


# tag::listener[]
@Singleton
class BookCatalog:
    # end::listener[]
    def __init__(self, repository: BookRepository):
        self.repository = repository
        self.books = AtomicReference(ConcurrentHashMap())
        self.cacheEnabled = AtomicBoolean(True)

    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    def loadInitialState(self, event: StartupEvent) -> None:
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
        if not self.cacheEnabled.get():
            return
        operation = event.operation()
        if operation == ChangeOperation.INSERT:
            book = event.entity().orElse(None)
            if book is not None:
                self.books.get().put(Long.valueOf(book.id), book)
        elif operation == ChangeOperation.UPDATE:
            metadata = event.metadata(PostgresChangeEventMetadata).orElseThrow()
            # An UPDATE can change the primary key.
            self.books.get().remove(Long.valueOf(metadata.oldKeys().get("id")))
            book = event.entity().orElse(None)
            if book is not None:
                self.books.get().put(Long.valueOf(book.id), book)
        elif operation == ChangeOperation.DELETE:
            metadata = event.metadata(PostgresChangeEventMetadata).orElseThrow()
            self.books.get().remove(Long.valueOf(metadata.oldKeys().get("id")))
        elif operation == ChangeOperation.INVALIDATE: # <1>
            self.refresh()
        elif operation == ChangeOperation.TERMINATED:
            self.cacheEnabled.set(False) # <2>
            self.books.get().clear()

    # end::events[]

    def find(self, id: int) -> Optional[Book]:
        return Optional.ofNullable(self.books.get().get(Long.valueOf(id))) if self.cacheEnabled.get() else self.repository.findById(id)

    def refresh(self) -> None:
        if not self.cacheEnabled.get():
            return
        refreshed = ConcurrentHashMap()
        for book in self.repository.findAll():
            refreshed.put(Long.valueOf(book.id), book)
        self.books.set(refreshed)
