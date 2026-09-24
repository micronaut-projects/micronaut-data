import logging

from jakarta.inject import Singleton
from micronaut.context.annotation import Factory
from micronaut.data.event.listeners import PostPersistEventListener, PrePersistEventListener

from example.Book import Book

LOG = logging.getLogger(__name__)


@Factory
class BookListeners:

    @Singleton
    def before_book_persist(self) -> PrePersistEventListener[Book]:  # <1>
        def listener(book):
            LOG.debug("Inserting book: %s", book.title)
            return True  # <2>
        return listener

    @Singleton
    def after_book_persist(self) -> PostPersistEventListener[Book]:  # <3>
        return lambda book: LOG.debug("Book inserted: %s", book.title)
