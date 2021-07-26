package io.micronaut.data.r2dbc.h2


import io.micronaut.test.extensions.spock.annotation.MicronautTest
import reactor.core.publisher.Mono
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import jakarta.inject.Inject

@MicronautTest(rollback = false)
@Stepwise
class H2TransactionManagementSpec extends Specification implements H2TestPropertyProvider {

    @Shared @Inject H2OwnerRepository ownerRepository

    def setup() {
        Mono.from(ownerRepository.deleteAll()).block()
    }

    def cleanup() {
        Mono.from(ownerRepository.deleteAll()).block()
    }

    void 'test rollback only'() {
        when:"When setRollbackOnly is called"
        ownerRepository.testSetRollbackOnly().block()

        then:"The transaction is rolled back"
        Mono.from(ownerRepository.count()).block() == 0
    }

    void 'test rollback on exception'() {
        when:"When setRollbackOnly is called"
        ownerRepository.testRollbackOnException().block()

        then:"The transaction is rolled back"
        def e = thrown(RuntimeException)
        e.message == "Something bad happened"
        Mono.from(ownerRepository.count()).block() == 0
    }

    void 'test success'() {
        when:"When setRollbackOnly is called"
        ownerRepository.setupData().block()

        then:"The transaction is rolled back"
        Mono.from(ownerRepository.count()).block() == 2
    }
}
