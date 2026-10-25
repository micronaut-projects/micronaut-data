package example

import io.micronaut.transaction.annotation.OracleTransactional
import jakarta.inject.Singleton

@Singleton
open class BookingService(private val seatRepository: SeatRepository) {

    @OracleTransactional(sessionless = OracleTransactional.Sessionless.SUSPEND, timeout = 60)
    open fun holdSeat(seat: Seat): Long {
        return seatRepository.save(seat).id!!
    }

    @OracleTransactional(sessionless = OracleTransactional.Sessionless.REQUIRES_SUSPENDED)
    open fun ticketSeat(id: Long) {
        val seat = seatRepository.findById(id).orElseThrow { RuntimeException("Seat not found") }
        seatRepository.update(seat.copy(status = "TICKETED"))
    }
}
