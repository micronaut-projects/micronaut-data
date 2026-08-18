package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.MappedAggregate
import io.micronaut.data.nitrite.repository.MappedAggregateRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * A derived aggregate reads the stored field of the aggregated property, which is whatever name the
 * property is mapped to — not only the Java name or its snake-case form.
 */
class NitriteMappedAggregateFieldSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(["micronaut.nitrite.default.storage-mode": "IN_MEMORY"])

    @Shared
    MappedAggregateRepository repository = context.getBean(MappedAggregateRepository)

    def setup() {
        repository.deleteAll()
    }

    void "a derived aggregate finds a property stored under a custom mapped name"() {
        given: "totalValue is stored as grand_total, which is not its snake-case form"
        repository.save(new MappedAggregate("basket", new BigDecimal("10.00")))
        repository.save(new MappedAggregate("basket", new BigDecimal("30.00")))

        expect:
        repository.findMaxTotalValueByName("basket") == new BigDecimal("30.00")
    }
}
