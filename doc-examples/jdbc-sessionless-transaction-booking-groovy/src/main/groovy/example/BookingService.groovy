package example

import io.micronaut.transaction.annotation.OracleTransactional
import jakarta.inject.Singleton

@Singleton
class BookingService {

    private final SeatRepository seatRepository

    BookingService(SeatRepository seatRepository) {
        this.seatRepository = seatRepository
    }

    @OracleTransactional(sessionless = OracleTransactional.Sessionless.SUSPEND, timeout = 60)
    Long holdSeat(Seat seat) {
        return seatRepository.save(seat).id
    }

    @OracleTransactional(sessionless = OracleTransactional.Sessionless.REQUIRES_SUSPENDED)
    void ticketSeat(Long id) {
        Seat seat = seatRepository.findById(id).orElseThrow { new RuntimeException("Seat not found") }
        seat.status = "TICKETED"
        seatRepository.update(seat)
    }
}
