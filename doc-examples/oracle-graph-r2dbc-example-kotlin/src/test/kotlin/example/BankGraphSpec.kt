package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@MicronautTest(transactional = false)
class BankGraphSpec {

    @Inject
    lateinit var bankGraph: BankGraph

    @AfterEach
    fun cleanup() {
        bankGraph.deleteById(5L).block()
    }

    @Test
    fun testReactive() {
        // tag::reactive[]
        val bigSenders = bankGraph.save(Account(5L, "Erin")) // <1>
            .thenMany(bankGraph.findByTransfersAmountGreaterThan(BigDecimal(1000))) // <2>
            .map { it.name }
            .collectList()
            .block() // <3>
        // end::reactive[]

        assertEquals(listOf("Bob"), bigSenders)
        assertEquals("Erin", bankGraph.findById(5L).block()!!.name)
        assertTrue(bankGraph.existsPathByTransfers(1L, 3L).block()!!)
    }
}
