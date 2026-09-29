from java.util import Optional
from micronaut.data.annotation import Join
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.repository import CrudRepository

from example.Product import Product


# tag::forUpdate[]
@JdbcRepository(dialect=Dialect.H2)
class ProductLockingRepository(CrudRepository[Product, int]):

    @Join("manufacturer")
    def findByIdForUpdate(self, id: int) -> Optional[Product]: ...

    @Join("manufacturer")
    def findAllOrderByNameForUpdate(self) -> list[Product]: ...

    @Join("manufacturer")
    def findByNameForUpdate(self, name: str) -> list[Product]: ...
# end::forUpdate[]
