from typing import Annotated

from jakarta.validation import Valid
from jakarta.validation.constraints import NotNull
from micronaut.http import HttpResponse
from micronaut.http.annotation import Controller, Delete, Get, Post, Put
from reactor.core.publisher import Flux, Mono

from example.Book import Book
from example.BookRepository import BookRepository


@Controller("/books")
class BookController:

    def __init__(self, book_repository: BookRepository):
        self.book_repository = book_repository

    # tag::create[]
    @Post("/")
    def create(self, book: Annotated[Book, Valid]) -> Mono[Book]:
        return Mono.from_(self.book_repository.save(book))
    # end::create[]

    # tag::read[]
    @Get("/")
    def all(self) -> Flux[Book]:
        return self.book_repository.findAll()  # <1>

    @Get("/{id}")
    def show(self, id: int) -> Mono[Book]:
        return self.book_repository.findById(id)  # <2>
    # end::read[]

    # tag::update[]
    @Put("/{id}")
    def update(self, id: Annotated[int, NotNull], book: Annotated[Book, Valid]) -> Mono[Book]:
        return Mono.from_(self.book_repository.update(book))
    # end::update[]

    # tag::delete[]
    @Delete("/{id}")
    def delete(self, id: Annotated[int, NotNull]) -> Mono[HttpResponse]:
        return (Mono.from_(self.book_repository.deleteById(id))
                .map(lambda deleted: HttpResponse.noContent() if deleted > 0 else HttpResponse.notFound()))
    # end::delete[]
