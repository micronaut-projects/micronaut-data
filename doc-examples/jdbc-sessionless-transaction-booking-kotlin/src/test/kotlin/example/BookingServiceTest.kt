package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.transaction.jdbc.oracle.OracleSessionlessTransactionPropagationOperations
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MicronautTest(transactional = false)
class BookingServiceTest {

    @Inject
    lateinit var bookingService: BookingService

    @Inject
    lateinit var seatRepository: SeatRepository

    @Inject
    lateinit var transactionPropagationOperations: OracleSessionlessTransactionPropagationOperations

    @BeforeEach
    fun cleanUp() {
        seatRepository.deleteAll()
    }

    @Test
    fun testTransactionResumed() {
        transactionPropagationOperations.withPropagation {
            val seat = Seat(flightId = "JU501", seatId = "2c", customerId = "msid")
            val seatId = bookingService.holdSeat(seat)

            assertTrue(seatRepository.findAll().isEmpty())

            bookingService.ticketSeat(seatId)

            val seats = seatRepository.findAll()
            assertEquals(1, seats.size)
            assertEquals("TICKETED", seats.first().status)
        }
    }

    @Test
    fun testCurrentTransactionIdExportsSuspendedTransactionId() {
        // tag::propagation[]
        val suspendedSeat = transactionPropagationOperations.withPropagation {
            val seatId = bookingService.holdSeat(Seat(flightId = "JU502", seatId = "3a", customerId = "msid"))
            val transactionId = transactionPropagationOperations.currentTransactionId().orElseThrow()
            SuspendedSeat(seatId, transactionId)
        }

        // end::propagation[]
        assertNotNull(suspendedSeat)

        // tag::propagation[]
        transactionPropagationOperations.withPropagation(suspendedSeat.transactionId) {
            bookingService.ticketSeat(suspendedSeat.seatId)
        }
        // end::propagation[]
        assertTicketedSeat()
    }

    @Test
    fun testSetTransactionIdImportsIntoActivePropagationState() {
        val suspendedSeat = transactionPropagationOperations.withPropagation {
            val seatId = bookingService.holdSeat(Seat(flightId = "JU503", seatId = "4b", customerId = "msid"))
            val transactionId = transactionPropagationOperations.currentTransactionId().orElseThrow()
            SuspendedSeat(seatId, transactionId)
        }

        transactionPropagationOperations.withPropagation {
            assertTrue(transactionPropagationOperations.currentTransactionId().isEmpty)

            transactionPropagationOperations.setTransactionId(suspendedSeat.transactionId)
            assertEquals(suspendedSeat.transactionId, transactionPropagationOperations.currentTransactionId().orElseThrow())

            bookingService.ticketSeat(suspendedSeat.seatId)
            assertTrue(transactionPropagationOperations.currentTransactionId().isEmpty)
        }

        assertTicketedSeat()
    }

    private fun assertTicketedSeat() {
        val seats = seatRepository.findAll()
        assertEquals(1, seats.size)
        assertEquals("TICKETED", seats.first().status)
    }

    private data class SuspendedSeat(val seatId: Long, val transactionId: String)
}
