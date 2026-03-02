package io.micronaut.data.nitrite.service

import io.micronaut.data.nitrite.repository.EventRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import spock.lang.Stepwise
import io.micronaut.transaction.annotation.Transactional

@MicronautTest(transactional = false, rollback = false)
@Stepwise
class NitriteTransactionManagementSpec extends Specification {

    @Inject EventRepository repo
    @Inject NitriteTransactionManagementService svc
    @Inject io.micronaut.transaction.TransactionOperations<?> txOp

    def setup() {
        svc.cleanup()
    }

    void "test basic commit"() {
        when:
        svc.doSuccess("test1")

        then:
        !repo.findByType("test1").isEmpty()
    }

    void "test basic rollback"() {
        when:
        svc.doFail("test2")

        then:
        thrown(RuntimeException)
        repo.findByType("test2").isEmpty()
    }

    void "test mandatory propagation fails without TX"() {
        when:
        svc.doMandatory("test3")

        then:
        def e = thrown(Exception)
        e.message.contains("mandatory")
    }

    void "test mandatory propagation success with TX"() {
        when:
        txOp.executeWrite { status ->
            svc.doMandatory("test4")
        }

        then:
        !repo.findByType("test4").isEmpty()
    }

    void "test never propagation fails with TX"() {
        when:
        txOp.executeWrite { status ->
            svc.doNever("test5")
        }

        then:
        def e = thrown(Exception)
        e.message.contains("never")
    }

    void "test never propagation success without TX"() {
        when:
        svc.doNever("test6")

        then:
        !repo.findByType("test6").isEmpty()
    }

    void "test requires_new starts a fresh TX"() {
        when:
        txOp.executeWrite { status ->
            svc.doRequiresNew("test7")
        }

        then:
        !repo.findByType("test7").isEmpty()
    }

    void "test not_supported propagation suspends TX"() {
        when:
        txOp.executeWrite { status ->
            svc.doNotSupported("test8")
        }

        then:
        !repo.findByType("test8").isEmpty()
    }

    void "test manual setRollbackOnly"() {
        when:
        svc.setRollbackOnlyManually("test9")

        then:
        thrown(io.micronaut.transaction.exceptions.UnexpectedRollbackException)
        repo.findByType("test9").isEmpty()
    }

    void "test connection exhaustion/leakage stress test"() {
        when: "running 100 transactions in sequence"
        100.times { i ->
            svc.doSuccess("stress-$i")
        }

        then: "all transactions committed and no connections were leaked"
        repo.count() == 100
    }
}
