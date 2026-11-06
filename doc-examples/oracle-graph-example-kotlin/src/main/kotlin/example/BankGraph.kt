package example

import io.micronaut.data.annotation.Query
import io.micronaut.data.graph.annotation.PathLimit
import io.micronaut.data.graph.annotation.PropertyGraph
import io.micronaut.data.oracle.graph.annotation.OracleGraphRepository
import io.micronaut.data.repository.GenericRepository
import java.math.BigDecimal

// tag::repository[]
@OracleGraphRepository(graph = "bank_graph", language = PropertyGraph.QueryLanguage.SQL_PGQ) // <1>
interface BankGraph : GenericRepository<Account, Long> {

    fun findById(id: Long): Account?

    fun findByTransfersAmountGreaterThan(amount: BigDecimal): List<Account> // <2>

    @PathLimit(3) // <3>
    fun existsPathByTransfers(from: Long, to: Long): Boolean
    // end::repository[]

    // tag::query[]
    @Query("""
        SELECT * FROM GRAPH_TABLE (bank_graph
          MATCH (a IS Account) -[t IS TRANSFERS]-> (b IS Account)
          WHERE t.amount > ${'$'}amount
          COLUMNS (a.name AS "source", b.name AS "target", t.amount AS "amount"))
        ORDER BY "amount"
        """)
    fun largeTransfers(amount: BigDecimal): List<TransferView>
    // end::query[]
// tag::repository[]
}
// end::repository[]
