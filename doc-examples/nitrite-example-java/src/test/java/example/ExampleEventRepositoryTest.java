package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(transactional = false)
class ExampleEventRepositoryTest {

    @Inject
    ExampleEventRepository repository;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    // tag::json-query-operators-usage[]
    @Test
    void testJsonQueryOperators() {
        ExampleEvent first = event("created", "hello world", ExampleEvent.Status.ACTIVE, "10.00");
        first.setPriority(1);
        first.setTags(List.of("audit", "customer"));
        repository.save(first);

        ExampleEvent second = event("updated", "", ExampleEvent.Status.INACTIVE, "20.00");
        second.setPriority(2);
        second.setTags(List.of("audit"));
        repository.save(second);

        ExampleEvent third = event("deleted", null, ExampleEvent.Status.ACTIVE, "30.00");
        third.setTags(List.of("system"));
        repository.save(third);

        assertTrue(repository.findByTypeJson("created").isPresent());
        assertEquals(2, repository.findByTypeNotEqualJson("created").size());
        assertEquals(1, repository.findByPayloadRegex(".*world.*").size());
        assertEquals(2, repository.findByPriorityExists(true).size());
        assertEquals(2, repository.findByPriorityInJson(List.of(1, 2)).size());
        assertEquals(1, repository.findByPriorityNotInJson(List.of(1, 2)).size());
        assertEquals(1, repository.findByPayloadLike("%world%").size());
        assertEquals(2, repository.findByPriorityNot(1).size());
        assertEquals(2, repository.findByPayloadEmpty(true).size());
        assertEquals(1, repository.findByTagsAll(List.of("audit", "customer")).size());
    }
    // end::json-query-operators-usage[]

    // tag::aggregation-usage[]
    @Test
    void testAggregations() {
        repository.save(event("created", "a", ExampleEvent.Status.ACTIVE, "10.00"));
        repository.save(event("updated", "b", ExampleEvent.Status.ACTIVE, "30.00"));
        repository.save(event("archived", "c", ExampleEvent.Status.INACTIVE, "100.00"));

        assertEquals(30.0, repository.findMaxScoreByStatus(ExampleEvent.Status.ACTIVE).orElseThrow());
        assertEquals(10.0, repository.findMinScoreByStatus(ExampleEvent.Status.ACTIVE).orElseThrow());
        assertEquals(40.0, repository.findSumScoreByStatus(ExampleEvent.Status.ACTIVE).orElseThrow());
        assertEquals(20.0, repository.findAvgScoreByStatus(ExampleEvent.Status.ACTIVE).orElseThrow());
        assertEquals(LocalDate.of(2024, 1, 2), repository.findMaxEventDateByStatus(ExampleEvent.Status.ACTIVE).orElseThrow());
        assertEquals(LocalDate.of(2024, 1, 1), repository.findMinEventDateByStatus(ExampleEvent.Status.ACTIVE).orElseThrow());
        assertEquals(3, repository.countDistinctType());
    }
    // end::aggregation-usage[]

    // tag::json-project-usage[]
    @Test
    void testJsonProject() {
        repository.save(event("created", "a", ExampleEvent.Status.ACTIVE, "10.00"));
        repository.save(event("updated", "b", ExampleEvent.Status.ACTIVE, "20.00"));
        repository.save(event("archived", "c", ExampleEvent.Status.INACTIVE, "30.00"));

        List<String> activeTypes = repository.findTypesByStatus(ExampleEvent.Status.ACTIVE);

        assertEquals(List.of("created", "updated"), activeTypes);
    }
    // end::json-project-usage[]

    // tag::dto-projection-usage[]
    @Test
    void testDtoProjection() {
        repository.save(event("created", "a", ExampleEvent.Status.ACTIVE, "10.00"));
        repository.save(event("updated", "b", ExampleEvent.Status.ACTIVE, "20.00"));
        repository.save(event("archived", "c", ExampleEvent.Status.INACTIVE, "30.00"));

        List<EventSummary> summaries = repository.findByStatus(ExampleEvent.Status.ACTIVE);

        assertEquals(List.of("created", "updated"), summaries.stream().map(EventSummary::type).toList());
    }
    // end::dto-projection-usage[]

    // tag::type-mapping-usage[]
    @Test
    void testTypeMappingRoundTrip() {
        Instant occurredAt = Instant.parse("2024-01-01T10:15:30Z");
        LocalDateTime eventDateTime = LocalDateTime.of(2024, 1, 1, 10, 15, 30);
        ExampleEvent event = event("typed", "payload", ExampleEvent.Status.PENDING, "123.45");
        event.setEventDate(LocalDate.of(2024, 1, 1));
        event.setOccurredAt(occurredAt);
        event.setEventDateTime(eventDateTime);
        event.setData("payload-bytes".getBytes(StandardCharsets.UTF_8));
        event.setMetadata(Map.of("source", "docs", "tenant", "primary"));
        event.setAttempts(Map.of(
            "first", new ExampleEvent.EventAttempt("worker-a", 1),
            "second", new ExampleEvent.EventAttempt("worker-b", 2)
        ));
        event.setNote(Optional.of("optional note"));
        event.setLocation(new ExampleEvent.EventLocation("eu-west", "zone-a"));

        ExampleEvent saved = repository.save(event);
        ExampleEvent reloaded = repository.findById(saved.getId()).orElseThrow();

        assertEquals(ExampleEvent.Status.PENDING, reloaded.getStatus());
        assertEquals(LocalDate.of(2024, 1, 1), reloaded.getEventDate());
        assertEquals(eventDateTime, reloaded.getEventDateTime());
        assertEquals(occurredAt, reloaded.getOccurredAt());
        assertEquals(new BigDecimal("123.45"), reloaded.getAmount());
        assertArrayEquals("payload-bytes".getBytes(StandardCharsets.UTF_8), reloaded.getData());
        assertEquals("docs", reloaded.getMetadata().get("source"));
        assertEquals("worker-a", reloaded.getAttempts().get("first").getHandler());
        assertEquals(2, reloaded.getAttempts().get("second").getRetries());
        assertNotNull(reloaded.getNote());
        assertTrue(reloaded.getNote().toString().contains("optional note"));
        assertEquals("eu-west", reloaded.getLocation().getRegion());
    }
    // end::type-mapping-usage[]

    private static ExampleEvent event(String type, String payload, ExampleEvent.Status status, String amount) {
        ExampleEvent event = new ExampleEvent(type, payload, status, new BigDecimal(amount));
        event.setScore(Double.valueOf(amount));
        event.setEventDate(switch (type) {
            case "created" -> LocalDate.of(2024, 1, 1);
            case "updated" -> LocalDate.of(2024, 1, 2);
            default -> LocalDate.of(2024, 1, 3);
        });
        return event;
    }
}
