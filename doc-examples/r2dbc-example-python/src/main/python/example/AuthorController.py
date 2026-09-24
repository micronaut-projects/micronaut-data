from micronaut.http.annotation import Controller, Get
from reactor.core.publisher import Flux, Mono

from example.Author import Author
from example.AuthorRepository import AuthorRepository


@Controller("/authors")
class AuthorController:

    def __init__(self, repository: AuthorRepository):
        self.repository = repository

    @Get
    def all(self) -> Flux[Author]:  # <1>
        return self.repository.findAll()

    @Get("/id")
    def get(self, id: int) -> Mono[Author]:  # <2>
        return self.repository.findById(id)
