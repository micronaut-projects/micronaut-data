package example

import io.micronaut.data.graph.annotation.PathLimit
import io.micronaut.data.oracle.graph.r2dbc.annotation.OracleGraphR2dbcRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal

// tag::repository[]
@OracleGraphR2dbcRepository(graph = "bank_graph") // <1>
interface BankGraph : ReactorCrudRepository<Account, Long> { // <2>

    fun findByTransfersAmountGreaterThan(amount: BigDecimal): Flux<Account> // <3>

    @PathLimit(3)
    fun existsPathByTransfers(from: Long, to: Long): Mono<Boolean>
}
// end::repository[]
