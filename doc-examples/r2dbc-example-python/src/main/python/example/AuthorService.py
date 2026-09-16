from jakarta.inject import Singleton
from jakarta.transaction import Transactional
from java.lang import Void
from reactor.core.publisher import Mono

from example.Author import Author
from example.AuthorRepository import AuthorRepository
from example.Book import Book
from example.BookRepository import BookRepository


@Singleton
class AuthorService:

    def __init__(self, author_repository: AuthorRepository, book_repository: BookRepository):  # <1>
        self.author_repository = author_repository
        self.book_repository = book_repository

    @Transactional  # <2>
    def setup_data(self) -> Mono[Void]:
        return (Mono.from_(self.author_repository.save(Author("Stephen King")))
                .flatMapMany(lambda author: self.book_repository.saveAll([
                    Book("The Stand", 1000, author),
                    Book("The Shining", 400, author),
                ]))
                .then(Mono.from_(self.author_repository.save(Author("James Patterson"))))
                .flatMapMany(lambda author: self.book_repository.save(Book("Along Came a Spider", 300, author)))
                .then())
