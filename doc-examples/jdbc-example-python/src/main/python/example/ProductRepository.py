from java.util import Optional
from java.util.concurrent import CompletableFuture
from micronaut.data.annotation import Join, Query
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.repository import CrudRepository

from example.Product import Product

try:
    from io.reactivex.rxjava3.core import Maybe, Single
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from reactivex.rxjava3.core import Maybe, Single


# tag::join[]
# tag::async[]
@JdbcRepository(dialect=Dialect.H2)
class ProductRepository(CrudRepository[Product, int]):
    # end::join[]
    # end::async[]

    # tag::join[]
    @Join(value="manufacturer", type="FETCH")  # <1>
    def list(self) -> list[Product]: ...
    # end::join[]

    # tag::async[]
    @Join("manufacturer")
    def findByNameContains(self, str: str) -> CompletableFuture[Product]: ...

    def countByManufacturerName(self, name: str) -> CompletableFuture[int]: ...
    # end::async[]

    # tag::reactive[]
    @Join("manufacturer")
    def queryByNameContains(self, str: str) -> Maybe[Product]: ...

    def countDistinctByManufacturerName(self, name: str) -> Single[int]: ...
    # end::reactive[]

    # tag::native[]
    @Query("""
        SELECT *, m_.name as m_name, m_.id as m_id
        FROM product p
        INNER JOIN manufacturer m_ ON p.manufacturer_id = m_.id
        WHERE p.name like :name limit 5""")
    @Join(value="manufacturer", alias="m_")
    def searchProducts(self, name: str) -> list[Product]: ...
    # end::native[]

    @Join("manufacturer")
    def findByName(self, name: str) -> Optional[Product]: ...
