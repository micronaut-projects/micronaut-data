from dataclasses import dataclass

from jakarta.inject import Singleton
from jakarta.transaction import Transactional
from micronaut.context.event import ApplicationEventPublisher
from micronaut.transaction.annotation import TransactionalEventListener

from example.Book import Book
from example.BookRepository import BookRepository


@dataclass
class NewBookEvent:
    book: Book


@Singleton
class BookManager:

    def __init__(self, book_repository: BookRepository, event_publisher: ApplicationEventPublisher[NewBookEvent]):  # <1>
        self.book_repository = book_repository
        self.event_publisher = event_publisher

    @Transactional
    def save_book(self, title: str, pages: int) -> None:
        book = Book(title, pages)
        self.book_repository.save(book)
        self.event_publisher.publishEvent(NewBookEvent(book))  # <2>

    @TransactionalEventListener
    def on_new_book(self, event: NewBookEvent) -> None:
        print(f"book = {event.book}")  # <3>
