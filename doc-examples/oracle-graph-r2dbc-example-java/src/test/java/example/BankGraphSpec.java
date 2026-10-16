package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
class BankGraphSpec {

    @Inject
    BankGraph bankGraph;

    @AfterEach
    void cleanup() {
        bankGraph.deleteById(5L).block();
    }

    @Test
    void testReactive() {
        // tag::reactive[]
        List<String> bigSenders = bankGraph.save(new Account(5L, "Erin", List.of())) // <1>
            .thenMany(bankGraph.findByTransfersAmountGreaterThan(new BigDecimal(1000))) // <2>
            .map(Account::name)
            .collectList()
            .block(); // <3>
        // end::reactive[]

        assertEquals(List.of("Bob"), bigSenders);
        assertEquals("Erin", bankGraph.findById(5L).block().name());
        assertTrue(bankGraph.existsPathByTransfers(1L, 3L).block());
    }
}
