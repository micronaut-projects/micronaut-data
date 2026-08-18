package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.DatasourceRecord
import io.micronaut.data.nitrite.repository.PrimaryDatasourceRepository
import io.micronaut.data.nitrite.repository.SecondaryDatasourceRepository
import io.micronaut.data.nitrite.transaction.NitriteConnectionOperations
import io.micronaut.data.nitrite.transaction.NitriteTransactionHolder
import io.micronaut.data.nitrite.transaction.NitriteTransactionOperations
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The Nitrite database is created per datasource, so the connection and transaction layer is per
 * datasource too. A single global instance would bind every repository to the primary database.
 */
class NitritePerDatasourceTransactionSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run([
        "micronaut.nitrite.primary.storage-mode"  : "IN_MEMORY",
        "micronaut.nitrite.secondary.storage-mode": "IN_MEMORY"
    ])

    void "the connection and transaction beans are created per datasource"() {
        expect:
        context.getBeansOfType(NitriteConnectionOperations).size() == 2
        context.getBeansOfType(NitriteTransactionOperations).size() == 2
        context.getBeansOfType(NitriteTransactionHolder).size() == 2

        and: "each datasource resolves its own instance by name"
        context.getBean(NitriteConnectionOperations, Qualifiers.byName("primary")) !=
            context.getBean(NitriteConnectionOperations, Qualifiers.byName("secondary"))
        context.getBean(NitriteTransactionHolder, Qualifiers.byName("primary")) !=
            context.getBean(NitriteTransactionHolder, Qualifiers.byName("secondary"))
    }

    void "a transaction of one datasource writes only to that datasource"() {
        given:
        def primary = context.getBean(PrimaryDatasourceRepository)
        def secondary = context.getBean(SecondaryDatasourceRepository)
        def secondaryTransactions = context.getBean(NitriteTransactionOperations, Qualifiers.byName("secondary"))

        when: "a write runs inside the secondary datasource's transaction manager"
        secondaryTransactions.executeWrite { status ->
            secondary.save(new DatasourceRecord("written-in-secondary"))
        }

        then: "it is committed to the secondary database and is invisible to the primary one"
        secondary.findByLabel("written-in-secondary")*.label == ["written-in-secondary"]
        primary.findByLabel("written-in-secondary").isEmpty()
    }
}
