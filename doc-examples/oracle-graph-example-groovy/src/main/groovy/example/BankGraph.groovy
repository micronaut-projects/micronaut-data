package example

import io.micronaut.data.annotation.Query
import io.micronaut.data.graph.annotation.PathLimit
import io.micronaut.data.graph.annotation.PropertyGraph
import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository
import io.micronaut.data.repository.GenericRepository

// tag::repository[]
@OracleGraphRepository(graph = "bank_graph", language = PropertyGraph.QueryLanguage.SQL_PGQ) // <1>
interface BankGraph extends GenericRepository<Account, Long> {

    Optional<Account> findById(Long id)

    List<Account> findByTransfersAmountGreaterThan(BigDecimal amount) // <2>

    @PathLimit(3) // <3>
    boolean existsPathByTransfers(Long from, Long to)
    // end::repository[]

    // tag::query[]
    @Query('''
        SELECT * FROM GRAPH_TABLE (bank_graph
          MATCH (a IS Account) -[t IS TRANSFERS]-> (b IS Account)
          WHERE t.amount > $amount
          COLUMNS (a.name AS "source", b.name AS "target", t.amount AS "amount"))
        ORDER BY "amount"
        ''')
    List<TransferView> largeTransfers(BigDecimal amount)
    // end::query[]
// tag::repository[]
}
// end::repository[]
