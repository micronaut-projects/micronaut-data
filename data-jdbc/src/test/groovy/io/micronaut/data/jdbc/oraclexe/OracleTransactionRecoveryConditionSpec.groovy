package io.micronaut.data.jdbc.oraclexe

import io.micronaut.context.ApplicationContext
import io.micronaut.transaction.recovery.CommitOutcomeResolver
import spock.lang.Specification

class OracleTransactionRecoveryConditionSpec extends Specification implements OracleTestPropertyProvider {

    void "recovery bean is disabled by default"() {
        given:
        ApplicationContext context = ApplicationContext.run(baseProperties())

        expect:
        !context.findBean(CommitOutcomeResolver).present

        cleanup:
        context.close()
    }

    void "recovery bean is enabled when oracle recovery flag is set"() {
        given:
        ApplicationContext context = ApplicationContext.run(baseProperties() + [
            'datasources.default.enable-oracle-transaction-recovery': 'true'
        ])

        expect:
        context.findBean(CommitOutcomeResolver).present

        cleanup:
        context.close()
    }

    private Map<String, Object> baseProperties() {
        OracleTestPropertyProvider.super.getProperties() + [
            'datasources.default.schema-generate': 'NONE',
            'datasources.default.packages'       : ''
        ]
    }
}
