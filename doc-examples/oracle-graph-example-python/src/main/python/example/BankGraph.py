# tag::repository[]
from java.math import BigDecimal
from micronaut.data.annotation import Query
from micronaut.data.graph.annotation import PathLimit, PropertyGraph
from micronaut.data.oracle.graph.annotation import OracleGraphRepository
from micronaut.data.repository import GenericRepository

from example.Account import Account
from example.TransferView import TransferView


@OracleGraphRepository(graph="bank_graph", language=PropertyGraph.QueryLanguage.SQL_PGQ)  # <1>
class BankGraph(GenericRepository[Account, int]):

    def findById(self, id: int) -> Account | None: ...

    def findByTransfersAmountGreaterThan(self, amount: BigDecimal) -> list[Account]: ...  # <2>

    @PathLimit(3)  # <3>
    def existsPathByTransfers(self, start: int, end: int) -> bool: ...
    # end::repository[]

    # tag::query[]
    @Query("""
        SELECT * FROM GRAPH_TABLE (bank_graph
          MATCH (a IS Account) -[t IS TRANSFERS]-> (b IS Account)
          WHERE t.amount > $amount
          COLUMNS (a.name AS "source", b.name AS "target", t.amount AS "amount"))
        ORDER BY "amount"
        """)
    def largeTransfers(self, amount: BigDecimal) -> list[TransferView]: ...
    # end::query[]
