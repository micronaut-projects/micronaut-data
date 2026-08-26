package io.micronaut.transaction.jdbc.oracle

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.transaction.annotation.OracleTransactional
import io.micronaut.transaction.exceptions.CannotCreateTransactionException
import io.micronaut.transaction.sessionless.SessionlessTransactionHandler
import jakarta.inject.Singleton
import spock.lang.Specification

/**
 * The sessionless handler is an {@code @EachBean(DataSource.class)} bean, so its qualifier is the
 * datasource name. Nothing may assume that name is "default".
 */
class OracleSessionlessDataSourceNameSpec extends Specification {

    private static final String SPEC_NAME = "OracleSessionlessDataSourceNameSpec"

    def "the handler is qualified by the datasource name, not by 'default'"() {
        given:
        def context = newContext()

        expect:
        context.findBean(SessionlessTransactionHandler, Qualifiers.byName("mydb")).isPresent()
        context.findBean(SessionlessTransactionHandler, Qualifiers.byName("default")).isEmpty()
        context.findBean(SessionlessTransactionHandler).isPresent()

        cleanup:
        context.close()
    }

    def "sessionless advice resolves the handler when the only datasource is not named 'default'"() {
        given:
        def context = newContext()
        def service = context.getBean(SessionlessService)

        when:
        service.suspend()

        then: "the handler is found and runs; it then rejects the call for its own reasons"
        def e = thrown(CannotCreateTransactionException)
        // Not a TransactionSuspensionNotSupportedException, which is what an unresolved handler raises.
        e.class == CannotCreateTransactionException
        e.message == "Oracle sessionless transaction propagation is not active"

        cleanup:
        context.close()
    }

    def "a sessionless method on a non-Oracle datasource fails before any application code runs, naming the datasource and the method"() {
        given:
        def context = ApplicationContext.run([
            "spec.name"            : SPEC_NAME,
            "datasources.other.url": "jdbc:h2:mem:oracleSessionlessOther;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE"
        ])
        def service = context.getBean(OtherDataSourceService)
        def propagation = context.getBean(OracleSessionlessTransactionPropagationOperations)

        when:
        propagation.withPropagation({ service.suspend(); null })

        then:
        def e = thrown(CannotCreateTransactionException)
        e.message.contains("datasource 'other'")
        e.message.contains("OtherDataSourceService.suspend")
        e.message.startsWith("Oracle sessionless transactions require an Oracle JDBC connection")

        cleanup:
        context.close()
    }

    private static ApplicationContext newContext() {
        ApplicationContext.run([
            "spec.name"           : SPEC_NAME,
            "datasources.mydb.url": "jdbc:h2:mem:oracleSessionlessDsName;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE"
        ])
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class OtherDataSourceService {

        @OracleTransactional(value = "other", sessionless = OracleTransactional.Sessionless.SUSPEND)
        void suspend() {
            throw new IllegalStateException("must not be reached")
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class SessionlessService {

        @OracleTransactional(sessionless = OracleTransactional.Sessionless.SUSPEND)
        void suspend() {
            throw new IllegalStateException("must not be reached")
        }
    }
}
