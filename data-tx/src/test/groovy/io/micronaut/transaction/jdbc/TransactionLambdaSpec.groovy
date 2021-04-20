package io.micronaut.transaction.jdbc

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification
import spock.lang.Stepwise

import javax.inject.Inject

@MicronautTest(transactional = false)
@Property(name = "datasources.default.name", value = "mydb")
@Stepwise
class TransactionLambdaSpec extends Specification {

    @Inject TestService testService

    void "test transactional lambda handling"() {
        given:
        testService.init()

        when:"an insert is performed in a transaction"
        testService.insertWithTransaction()

        then:"The insert worked"
        testService.readTransactionally() == 1

        when:"A transaction is rolled back"
        testService.insertAndRollback()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Bad things happened'
        testService.readTransactionally() == 1


        when:"A transaction is rolled back"
        testService.insertAndRollbackChecked()

        then:
        def e2 = thrown(Exception)
        e2.cause.message == 'Bad things happened'
        testService.readTransactionally() == 1
    }
}
