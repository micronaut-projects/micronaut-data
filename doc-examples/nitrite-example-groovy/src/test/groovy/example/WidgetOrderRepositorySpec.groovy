package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class WidgetOrderRepositorySpec extends Specification {

    @Inject WidgetRepository widgetRepository
    @Inject WidgetOrderRepository orderRepository

    def cleanup() {
        orderRepository.deleteAll()
        widgetRepository.deleteAll()
    }

    // tag::uuid-association-usage[]
    def "uuid association lookup"() {
        given:
        Widget anvil = widgetRepository.save(new Widget("Anvil Pro"))
        Widget lathe = widgetRepository.save(new Widget("Lathe"))
        orderRepository.save(new WidgetOrder("A-1", anvil))
        orderRepository.save(new WidgetOrder("A-2", anvil))
        orderRepository.save(new WidgetOrder("L-1", lathe))

        expect:
        orderRepository.findByWidgetValue(anvil.id.toString())*.orderNumber as Set == ["A-1", "A-2"] as Set
        orderRepository.findByWidgetValue("Anvil Pro")*.orderNumber as Set == ["A-1", "A-2"] as Set
    }
    // end::uuid-association-usage[]
}
