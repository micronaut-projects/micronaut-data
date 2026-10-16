package example

import io.micronaut.data.graph.annotation.PathLimit
import io.micronaut.data.oracle.graph.r2dbc.annotation.OracleGraphR2dbcRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

// tag::repository[]
@OracleGraphR2dbcRepository(graph = "bank_graph") // <1>
interface BankGraph extends ReactorCrudRepository<Account, Long> { // <2>

    Flux<Account> findByTransfersAmountGreaterThan(BigDecimal amount) // <3>

    @PathLimit(3)
    Mono<Boolean> existsPathByTransfers(Long from, Long to)
}
// end::repository[]
