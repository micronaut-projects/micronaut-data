package io.micronaut.data.document.mongodb.reactive


import io.micronaut.data.document.mongodb.repositories.MongoOwnerRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import reactor.core.publisher.Mono
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@MicronautTest(transactional = false, rollback = false)
@Stepwise
class MongoTransactionManagementSpec extends Specification implements MongoSelectReactiveDriver {

    @Shared @Inject MongoOwnerRepository ownerRepository

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
