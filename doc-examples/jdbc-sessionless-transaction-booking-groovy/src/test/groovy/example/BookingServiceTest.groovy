package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.jdbc.oracle.OracleSessionlessTransactionPropagationOperations
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class BookingServiceTest extends Specification {

    @Inject
    BookingService bookingService

    @Inject
    SeatRepository seatRepository

    @Inject
    OracleSessionlessTransactionPropagationOperations transactionPropagationOperations

    void setup() {
        seatRepository.deleteAll()
    }

    void "test transaction resumed"() {
        when:
        List<Seat> seatsBeforeTicketing = null
        List<Seat> seatsAfterTicketing = transactionPropagationOperations.withPropagation {
            Long seatId = bookingService.holdSeat(new Seat("JU501", "2c", "msid"))
            seatsBeforeTicketing = seatRepository.findAll()
            bookingService.ticketSeat(seatId)
            seatRepository.findAll()
        }

        then:
        seatsBeforeTicketing.isEmpty()
        seatsAfterTicketing.size() == 1
        seatsAfterTicketing.first().status == "TICKETED"
    }

    void "test current transaction id exports suspended transaction id"() {
        when:
        // tag::propagation[]
        SuspendedSeat suspendedSeat = transactionPropagationOperations.withPropagation {
            Long seatId = bookingService.holdSeat(new Seat("JU502", "3a", "msid"))
            String transactionId = transactionPropagationOperations.currentTransactionId().orElseThrow()
            new SuspendedSeat(seatId, transactionId)
        }

        // end::propagation[]
        then:
        suspendedSeat != null

        when:
        // tag::propagation[]
        transactionPropagationOperations.withPropagation(suspendedSeat.transactionId()) {
            bookingService.ticketSeat(suspendedSeat.seatId())
        }
        // end::propagation[]

        then:
        assertTicketedSeat()
    }

    void "test set transaction id imports into active propagation state"() {
        given:
        SuspendedSeat suspendedSeat = transactionPropagationOperations.withPropagation {
            Long seatId = bookingService.holdSeat(new Seat("JU503", "4b", "msid"))
            String transactionId = transactionPropagationOperations.currentTransactionId().orElseThrow()
            new SuspendedSeat(seatId, transactionId)
        }

        when:
        Map<String, Object> state = transactionPropagationOperations.withPropagation {
            boolean emptyBefore = transactionPropagationOperations.currentTransactionId().isEmpty()
            transactionPropagationOperations.setTransactionId(suspendedSeat.transactionId())
            String imported = transactionPropagationOperations.currentTransactionId().orElseThrow()
            bookingService.ticketSeat(suspendedSeat.seatId())
            boolean emptyAfter = transactionPropagationOperations.currentTransactionId().isEmpty()
            [emptyBefore: emptyBefore, imported: imported, emptyAfter: emptyAfter] as Map<String, Object>
        }

        then:
        state.emptyBefore
        state.imported == suspendedSeat.transactionId()
        state.emptyAfter
        assertTicketedSeat()
    }

    private boolean assertTicketedSeat() {
        List<Seat> seats = seatRepository.findAll()
        assert seats.size() == 1
        assert seats.first().status == "TICKETED"
        return true
    }

    private static record SuspendedSeat(Long seatId, String transactionId) {
    }
}
