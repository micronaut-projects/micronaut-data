package example;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.transaction.jdbc.oracle.OracleSessionlessTransactionPropagationOperations;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
class BookingServiceTest {

    @Inject
    BookingService bookingService;

    @Inject
    SeatRepository seatRepository;

    @Inject
    OracleSessionlessTransactionPropagationOperations transactionPropagationOperations;

    @BeforeEach
    void cleanUp() {
        seatRepository.deleteAll();
    }

    @Test
    void testTransactionResumed() {
        transactionPropagationOperations.withPropagation(() -> {
            Seat seat = new Seat("JU501", "2c", "msid");
            Long seatId = bookingService.holdSeat(seat);

            List<Seat> seats = seatRepository.findAll();
            assertTrue(CollectionUtils.isEmpty(seats));

            bookingService.ticketSeat(seatId);

            seats = seatRepository.findAll();
            assertFalse(CollectionUtils.isEmpty(seats));
            assertEquals(1, seats.size());
            assertEquals("TICKETED", seats.getFirst().getStatus());
            return null;
        });
    }

    @Test
    void testCurrentTransactionIdExportsSuspendedTransactionId() {
        // tag::propagation[]
        SuspendedSeat suspendedSeat = transactionPropagationOperations.withPropagation(() -> {
            Long seatId = bookingService.holdSeat(new Seat("JU502", "3a", "msid"));
            String transactionId = transactionPropagationOperations.currentTransactionId().orElseThrow();
            return new SuspendedSeat(seatId, transactionId);
        });

        // end::propagation[]
        assertNotNull(suspendedSeat);

        // tag::propagation[]
        transactionPropagationOperations.withPropagation(suspendedSeat.transactionId(), () -> {
            bookingService.ticketSeat(suspendedSeat.seatId());
            return null;
        });
        // end::propagation[]
        assertTicketedSeat();
    }

    @Test
    void testSetTransactionIdImportsIntoActivePropagationState() {
        SuspendedSeat suspendedSeat = requireNonNull(transactionPropagationOperations.withPropagation(() -> {
            Long seatId = bookingService.holdSeat(new Seat("JU503", "4b", "msid"));
            String transactionId = transactionPropagationOperations.currentTransactionId().orElseThrow();
            return new SuspendedSeat(seatId, transactionId);
        }));

        transactionPropagationOperations.withPropagation(() -> {
            assertTrue(transactionPropagationOperations.currentTransactionId().isEmpty());

            transactionPropagationOperations.setTransactionId(suspendedSeat.transactionId());
            assertEquals(suspendedSeat.transactionId(), transactionPropagationOperations.currentTransactionId().orElseThrow());

            bookingService.ticketSeat(suspendedSeat.seatId());
            assertTrue(transactionPropagationOperations.currentTransactionId().isEmpty());
            return null;
        });

        assertTicketedSeat();
    }

    private void assertTicketedSeat() {
        List<Seat> seats = seatRepository.findAll();
        assertFalse(CollectionUtils.isEmpty(seats));
        assertEquals(1, seats.size());
        assertEquals("TICKETED", seats.getFirst().getStatus());
    }

    private record SuspendedSeat(Long seatId, String transactionId) {
    }
}
