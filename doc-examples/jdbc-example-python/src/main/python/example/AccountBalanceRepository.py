from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.repository import CrudRepository
from micronaut.transaction.annotation import Transactional

from example.AccountBalance import AccountBalance


# tag::forUpdate[]
@JdbcRepository(dialect=Dialect.H2)
class AccountBalanceRepository(CrudRepository[AccountBalance, int]):

    def findByIdForUpdate(self, id: int) -> AccountBalance: ...  # <1>

    @Transactional  # <2>
    def addToBalance(self, id: int, amount: int) -> None:
        account_balance = self.findByIdForUpdate(id)  # <3>
        account_balance.balance += amount
        self.update(account_balance)  # <4>
# end::forUpdate[]
