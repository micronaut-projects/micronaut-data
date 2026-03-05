package example;

import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

@Singleton
public class BookingService {

    private final SeatRepository seatRepository;

    public BookingService(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    @Transactional(propagation = TransactionDefinition.Propagation.SUSPEND)
    public Long holdSeat(Seat seat) {
        return seatRepository.save(seat).getId();
    }

    @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_SUSPENDED)
    public void ticketSeat(Long id) {
        Seat seat = seatRepository.findById(id).orElseThrow(() -> new RuntimeException("Seat not found"));
        seat.setStatus("TICKETED");
        seatRepository.update(seat);
    }
}
