package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
class BankGraphSpec {

    @Inject
    BankGraph bankGraph;

    @Test
    void testQueries() {
        // tag::find[]
        List<Account> bigSenders = bankGraph.findByTransfersAmountGreaterThan(new BigDecimal(1000)); // <1>
        boolean connected = bankGraph.existsPathByTransfers(1L, 3L); // <2>
        // end::find[]

        assertEquals(List.of("Bob"), bigSenders.stream().map(Account::name).toList());
        assertTrue(connected);
        assertFalse(bankGraph.existsPathByTransfers(3L, 1L));
        assertEquals("Alice", bankGraph.findById(1L).orElseThrow().name());
        assertEquals(List.of("Alice", "Bob"), bankGraph.largeTransfers(new BigDecimal(100)).stream().map(TransferView::source).toList());
    }

    @Test
    void testSave() {
        // tag::save[]
        bankGraph.save(new Account(5L, "Erin", List.of())); // <1>
        List<Account> erin = bankGraph.findByNameStartsWithOrderByName("Er"); // <2>
        // end::save[]

        assertEquals(List.of("Erin"), erin.stream().map(Account::name).toList());
        bankGraph.deleteById(5L);
        assertTrue(bankGraph.findById(5L).isEmpty());
    }
}
