from typing import Annotated

from micronaut.data.annotation import Id, Query
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.repository import CrudRepository

from example.Account import Account


# tag::reservable[]
@JdbcRepository(dialect=Dialect.ORACLE)
class AccountRepository(CrudRepository[Account, int]):

    def reserveIncrementBalance(self, id: Annotated[int, Id], balance: int) -> int: ...
# end::reservable[]


# tag::reservable-raw-query[]
@JdbcRepository(dialect=Dialect.ORACLE)
class AccountRawQueryRepository(CrudRepository[Account, int]):

    @Query('UPDATE "ACCOUNT" SET "BALANCE" = "BALANCE" + :amount WHERE "ID" = :id')
    def reserveBalance(self, id: int, amount: int) -> int: ...
# end::reservable-raw-query[]
