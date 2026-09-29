package io.micronaut.transaction.jdbc

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.exceptions.UnexpectedRollbackException
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import spock.lang.Specification

import java.sql.Connection

/**
 * Verifies the "Exceptions and transactions" behaviour described in the guide: a failure in a joined transactional
 * call marks the whole transaction rollback-only, even if the caller catches the exception.
 */
@MicronautTest(transactional = false)
@Property(name = "datasources.default.name", value = "joinedfailuredb")
@Property(name = "spec.name", value = "JoinedTransactionFailureSpec")
class JoinedTransactionFailureSpec extends Specification {

    @Inject OuterService outer
    @Inject InnerService inner

    void "catching the failure of a joined transactional call leads to UnexpectedRollbackException on commit"() {
        given:
            inner.init()

        when:
            outer.insertAndSwallowJoinedFailure()

        then:
            thrown(UnexpectedRollbackException)
            inner.count() == 0
    }

    void "a failure in a REQUIRES_NEW call can be caught without affecting the outer transaction"() {
        given:
            inner.init()

        when:
            outer.insertAndSwallowNewTransactionFailure()

        then:
            noExceptionThrown()
            inner.count() == 1
    }

    @Requires(property = "spec.name", value = "JoinedTransactionFailureSpec")
    @Singleton
    static class OuterService {
        @Inject InnerService inner

        @Transactional
        void insertAndSwallowJoinedFailure() {
            inner.insert()
            try {
                inner.failJoined()
            } catch (IllegalStateException ignored) {
            }
        }

        @Transactional
        void insertAndSwallowNewTransactionFailure() {
            inner.insert()
            try {
                inner.failInNewTransaction()
            } catch (IllegalStateException ignored) {
            }
        }
    }

    @Requires(property = "spec.name", value = "JoinedTransactionFailureSpec")
    @Singleton
    static class InnerService {
        @Inject Connection connection

        @Transactional
        void init() {
            connection.prepareStatement("drop table joined_row if exists").execute()
            connection.prepareStatement("create table joined_row (id bigint not null auto_increment, primary key (id))").execute()
        }

        @Transactional
        void insert() {
            connection.prepareStatement("insert into joined_row default values").execute()
        }

        @Transactional
        void failJoined() {
            throw new IllegalStateException("inner failure")
        }

        @Transactional(Transactional.TxType.REQUIRES_NEW)
        void failInNewTransaction() {
            throw new IllegalStateException("inner failure")
        }

        @Transactional
        int count() {
            def rs = connection.prepareStatement("select count(*) from joined_row").executeQuery()
            rs.next()
            return rs.getInt(1)
        }
    }
}
