package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.Widget
import io.micronaut.data.nitrite.model.WidgetOrder
import io.micronaut.data.nitrite.repository.WidgetOrderRepository
import io.micronaut.data.nitrite.repository.WidgetRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class NitriteForwardAssociationLookupSpec extends Specification {

    @Inject
    WidgetRepository widgetRepository

    @Inject
    WidgetOrderRepository orderRepository

    def setup() {
        orderRepository.deleteAll()
        widgetRepository.deleteAll()
    }

    void "direct MANY_TO_ONE query with non-UUID String triggers forward lookup sub-query"() {
        given:
        def anvil = widgetRepository.save(new Widget("Anvil Pro"))
        def lathe = widgetRepository.save(new Widget("Lathe"))
        orderRepository.saveAll([
                new WidgetOrder("A-1", anvil),
                new WidgetOrder("A-2", anvil),
                new WidgetOrder("L-1", lathe)
        ])

        when:
        // field "widget" (no dot) + non-UUID String -> buildForwardLookupFilter
        def orders = orderRepository.findByWidgetStringValue("Anvil Pro")

        then:
        orders*.orderNumber as Set == ["A-1", "A-2"] as Set
    }
}
