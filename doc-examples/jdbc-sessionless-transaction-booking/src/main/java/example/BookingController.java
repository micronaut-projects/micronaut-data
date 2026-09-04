package example;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

@Controller("/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Post("/hold/{flightId}/{seatId}/{customerId}")
    public HttpResponse<String> holdSeat(String flightId, String seatId, String customerId) {
        Long seatIdValue = bookingService.holdSeat(new Seat(flightId, seatId, customerId));
        return HttpResponse.ok(seatIdValue.toString());
    }

    @Post("/ticket/{id}")
    public HttpResponse<Void> ticketSeat(Long id) {
        bookingService.ticketSeat(id);
        return HttpResponse.noContent();
    }
}
