# tag::repository[]
from java.math import BigDecimal
from micronaut.data.graph.annotation import PathLimit
from micronaut.data.oracle.graph.r2dbc.annotation import OracleGraphR2dbcRepository
from micronaut.data.repository.reactive import ReactorCrudRepository
from reactor.core.publisher import Flux, Mono

from example.Account import Account


@OracleGraphR2dbcRepository(graph="bank_graph")  # <1>
class BankGraph(ReactorCrudRepository[Account, int]):  # <2>

    def findByTransfersAmountGreaterThan(self, amount: BigDecimal) -> Flux[Account]: ...  # <3>

    @PathLimit(3)
    def existsPathByTransfers(self, start: int, end: int) -> Mono[bool]: ...
# end::repository[]
