package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@MicronautTest(transactional = false)
class BankGraphSpec {

    @Inject
    lateinit var bankGraph: BankGraph

    @Test
    fun testQueries() {
        // tag::find[]
        val bigSenders = bankGraph.findByTransfersAmountGreaterThan(BigDecimal(1000)) // <1>
        val connected = bankGraph.existsPathByTransfers(1L, 3L) // <2>
        // end::find[]

        assertEquals(listOf("Bob"), bigSenders.map { it.name })
        assertTrue(connected)
        assertFalse(bankGraph.existsPathByTransfers(3L, 1L))
        assertEquals("Alice", bankGraph.findById(1L)?.name)
        assertEquals(listOf("Alice", "Bob"), bankGraph.largeTransfers(BigDecimal(100)).map { it.source })
    }
}
