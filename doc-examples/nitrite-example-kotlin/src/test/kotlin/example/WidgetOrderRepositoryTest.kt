package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class WidgetOrderRepositoryTest {

    @Inject
    lateinit var widgetRepository: WidgetRepository

    @Inject
    lateinit var orderRepository: WidgetOrderRepository

    @AfterEach
    fun cleanup() {
        orderRepository.deleteAll()
        widgetRepository.deleteAll()
    }

    // tag::uuid-association-usage[]
    @Test
    fun testUuidAssociationLookup() {
        val anvil = widgetRepository.save(Widget("Anvil Pro"))
        val lathe = widgetRepository.save(Widget("Lathe"))
        orderRepository.save(WidgetOrder("A-1", anvil))
        orderRepository.save(WidgetOrder("A-2", anvil))
        orderRepository.save(WidgetOrder("L-1", lathe))

        val byUuid = orderRepository.findByWidgetValue(anvil.id.toString()).map { it.orderNumber }.toSet()
        val byName = orderRepository.findByWidgetValue("Anvil Pro").map { it.orderNumber }.toSet()

        assertEquals(setOf("A-1", "A-2"), byUuid)
        assertEquals(setOf("A-1", "A-2"), byName)
    }
    // end::uuid-association-usage[]
}
