package io.micronaut.data.r2dbc.connection

import io.r2dbc.spi.Connection
import io.r2dbc.spi.IsolationLevel
import org.slf4j.Logger
import reactor.core.publisher.Mono
import spock.lang.Specification

class R2dbcConnectionStateSpec extends Specification {

    void "nothing is restored when the state didn't change"() {
        given:
        def connection = Mock(Connection)
        def state = new R2dbcConnectionState(true, IsolationLevel.READ_COMMITTED)

        when:
        state.restore(connection, Mock(Logger)).block()

        then:
        1 * connection.isAutoCommit() >> true
        1 * connection.getTransactionIsolationLevel() >> IsolationLevel.READ_COMMITTED
        0 * connection._
    }

    void "the open transaction is rolled back before auto-commit is enabled"() {
        given:
        def connection = Mock(Connection)
        def state = new R2dbcConnectionState(true, IsolationLevel.READ_COMMITTED)

        when:
        state.restore(connection, Mock(Logger)).block()

        then:
        1 * connection.isAutoCommit() >> false

        then:
        1 * connection.rollbackTransaction() >> Mono.empty()

        then:
        1 * connection.setAutoCommit(true) >> Mono.empty()

        then:
        1 * connection.getTransactionIsolationLevel() >> IsolationLevel.SERIALIZABLE

        then:
        1 * connection.setTransactionIsolationLevel(IsolationLevel.READ_COMMITTED) >> Mono.empty()
        0 * connection._
    }

    void "auto-commit disabled when the connection was opened is restored without a rollback"() {
        given:
        def connection = Mock(Connection)
        def state = new R2dbcConnectionState(false, null)

        when:
        state.restore(connection, Mock(Logger)).block()

        then:
        1 * connection.isAutoCommit() >> true
        1 * connection.setAutoCommit(false) >> Mono.empty()
        0 * connection.rollbackTransaction()
        0 * connection.setTransactionIsolationLevel(_)
    }

    void "a failure to restore the state is logged and not propagated"() {
        given:
        def connection = Mock(Connection)
        def log = Mock(Logger)
        def failure = new IllegalStateException("broken connection")
        def state = new R2dbcConnectionState(true, null)

        when:
        state.restore(connection, log).block()

        then:
        1 * connection.isAutoCommit() >> false
        1 * connection.rollbackTransaction() >> Mono.error(failure)
        1 * log.warn(_ as String, failure)
        0 * connection.setAutoCommit(_)
    }
}
