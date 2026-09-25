from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.repository import GenericRepository

from example.Manufacturer import Manufacturer


@JdbcRepository(dialect=Dialect.H2)
class ManufacturerRepository(GenericRepository[Manufacturer, int]):
    def findByName(self, name: str) -> Manufacturer: ...

    def save(self, name: str) -> Manufacturer: ...

    def deleteAll(self) -> None: ...
