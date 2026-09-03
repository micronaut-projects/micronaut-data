package example;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest(transactional = false)
class BookingControllerTest {

    private static final String SESSIONLESS_TRANSACTION_HEADER = "Oracle-Sessionless-Transaction-Id";

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    SeatRepository seatRepository;

    @BeforeEach
    void cleanUp() {
        seatRepository.deleteAll();
    }

    @Test
    void testTransactionSuspendedAndResumedOverHttp() {
        // tag::http-propagation-suspend[]
        HttpResponse<String> holdResponse = client.toBlocking()
            .exchange(HttpRequest.POST("/bookings/hold/JU501/2c/msid", ""), String.class);
        // end::http-propagation-suspend[]
        assertEquals(HttpStatus.OK, holdResponse.getStatus());
        // tag::http-propagation-suspend[]
        String transactionId = holdResponse.getHeaders().get(SESSIONLESS_TRANSACTION_HEADER);
        // end::http-propagation-suspend[]
        assertNotNull(transactionId);
        // tag::http-propagation-suspend[]
        Long seatId = Long.valueOf(holdResponse.getBody().orElseThrow());
        // end::http-propagation-suspend[]

        List<Seat> seats = seatRepository.findAll();
        assertTrue(CollectionUtils.isEmpty(seats));

        // tag::http-propagation-resume[]
        HttpRequest<String> ticketRequest = HttpRequest.POST("/bookings/ticket/" + seatId, "")
            .header(SESSIONLESS_TRANSACTION_HEADER, transactionId);
        HttpResponse<Void> ticketResponse = client.toBlocking().exchange(ticketRequest, Void.class);
        // end::http-propagation-resume[]

        assertEquals(HttpStatus.NO_CONTENT, ticketResponse.getStatus());
        assertFalse(ticketResponse.getHeaders().contains(SESSIONLESS_TRANSACTION_HEADER));

        seats = seatRepository.findAll();
        assertFalse(CollectionUtils.isEmpty(seats));
        assertEquals(1, seats.size());
        assertEquals("TICKETED", seats.getFirst().getStatus());
    }
}
