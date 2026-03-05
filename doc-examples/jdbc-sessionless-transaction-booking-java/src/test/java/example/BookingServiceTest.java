package example;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
public class BookingServiceTest {

    @Inject
    BookingService bookingService;

    @Inject
    SeatRepository seatRepository;

    @Test
    void testTransactionResumed() {
        Seat seat = new Seat("JU501", "2c", "msid");
        Long seatId = bookingService.holdSeat(seat);

        List<Seat> seats = seatRepository.findAll();
        assertTrue(CollectionUtils.isEmpty(seats));

        bookingService.ticketSeat(seatId);

        seats = seatRepository.findAll();
        assertFalse(CollectionUtils.isEmpty(seats));
        assertEquals(1, seats.size());
        assertEquals("TICKETED", seats.getFirst().getSeatId());
    }
}
