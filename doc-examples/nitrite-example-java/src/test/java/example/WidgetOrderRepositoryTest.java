package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(transactional = false)
class WidgetOrderRepositoryTest {

    @Inject
    WidgetRepository widgetRepository;

    @Inject
    WidgetOrderRepository orderRepository;

    @AfterEach
    void cleanup() {
        orderRepository.deleteAll();
        widgetRepository.deleteAll();
    }

    // tag::uuid-association-usage[]
    @Test
    void testUuidAssociationLookup() {
        Widget anvil = widgetRepository.save(new Widget("Anvil Pro"));
        Widget lathe = widgetRepository.save(new Widget("Lathe"));
        orderRepository.save(new WidgetOrder("A-1", anvil));
        orderRepository.save(new WidgetOrder("A-2", anvil));
        orderRepository.save(new WidgetOrder("L-1", lathe));

        Set<String> byUuid = orderRepository.findByWidgetValue(anvil.getId().toString())
            .stream().map(WidgetOrder::getOrderNumber).collect(Collectors.toSet());
        Set<String> byName = orderRepository.findByWidgetValue("Anvil Pro")
            .stream().map(WidgetOrder::getOrderNumber).collect(Collectors.toSet());

        assertEquals(Set.of("A-1", "A-2"), byUuid);
        assertEquals(Set.of("A-1", "A-2"), byName);
    }
    // end::uuid-association-usage[]
}
