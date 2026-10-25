package example;

import io.micronaut.transaction.annotation.OracleTransactional;
import jakarta.inject.Singleton;

@Singleton
public class BookingService {

    private final SeatRepository seatRepository;

    public BookingService(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    @OracleTransactional(sessionless = OracleTransactional.Sessionless.SUSPEND, timeout = 60)
    public Long holdSeat(Seat seat) {
        return seatRepository.save(seat).getId();
    }

    @OracleTransactional(sessionless = OracleTransactional.Sessionless.REQUIRES_SUSPENDED)
    public void ticketSeat(Long id) {
        Seat seat = seatRepository.findById(id).orElseThrow(() -> new RuntimeException("Seat not found"));
        seat.setStatus("TICKETED");
        seatRepository.update(seat);
    }
}
