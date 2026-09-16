from java.util import Optional
from micronaut.data.annotation import Join
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.repository import CrudRepository

from example.Quantity import Quantity
from example.Sale import Sale


@JdbcRepository(dialect=Dialect.H2)
class SaleRepository(CrudRepository[Sale, int]):

    @Join("product")
    @Join("product.manufacturer")
    def getById(self, id: int) -> Optional[Sale]: ...  # TODO(python): overriding the inherited findById(Integer) drops the method

    @Join("product")
    @Join("product.manufacturer")
    def findByQuantity(self, quantity: Quantity) -> Optional[Sale]: ...
