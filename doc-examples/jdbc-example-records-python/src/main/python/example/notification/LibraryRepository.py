from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.repository import CrudRepository

from example.notification.Library import Library

@JdbcRepository(dialect=Dialect.ORACLE)
class LibraryRepository(CrudRepository[Library, int]):

    def findByCapacityGreaterThanEquals(self, bookCount: int) -> list[Library]: ...
