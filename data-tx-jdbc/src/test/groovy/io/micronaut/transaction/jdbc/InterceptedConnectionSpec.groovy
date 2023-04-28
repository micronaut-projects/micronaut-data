package io.micronaut.transaction.jdbc

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.exceptions.NoTransactionException
import spock.lang.Specification

import jakarta.inject.Inject
import java.sql.Connection

@MicronautTest(transactional = false)
@Property(name = "datasources.default.name", value = "mydb")
class InterceptedConnectionSpec extends Specification {

    @Inject
    Connection connection

    void 'test injected connection - no transaction'() {
        when:
        connection.prepareStatement("select * from stuff")

        then:
        thrown(NoTransactionException)
    }


}
