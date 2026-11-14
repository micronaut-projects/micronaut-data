from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.repository import CrudRepository

from example.Book import Book


@JdbcRepository(dialect=Dialect.POSTGRES)
class BookRepository(CrudRepository[Book, int]):
    pass
