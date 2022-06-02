package io.micronaut.data.r2dbc.h2

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.exceptions.NoTransactionException
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@MicronautTest(transactional = false)
@Stepwise
class H2TransactionSpec extends Specification implements H2TestPropertyProvider {

    @Shared @Inject H2OwnerRepository ownerRepository

    void 'test fail'() {
        when:
        Flux.from(ownerRepository.findAll()).collectList().block()

        then:
        thrown(NoTransactionException)
    }
}
