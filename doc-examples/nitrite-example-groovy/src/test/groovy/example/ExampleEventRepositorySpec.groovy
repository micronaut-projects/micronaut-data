package example

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

@MicronautTest(transactional = false)
class ExampleEventRepositorySpec extends Specification {

    @Inject
    ExampleEventRepository repository

    def cleanup() {
        repository.deleteAll()
    }

    // tag::json-query-operators-usage[]
    def "json query operators"() {
        given:
        ExampleEvent first = event("created", "hello world", ExampleEvent.Status.ACTIVE, "10.00")
        first.priority = 1
        first.tags = ["audit", "customer"]
        repository.save(first)

        ExampleEvent second = event("updated", "", ExampleEvent.Status.INACTIVE, "20.00")
        second.priority = 2
        second.tags = ["audit"]
        repository.save(second)

        ExampleEvent third = event("deleted", null, ExampleEvent.Status.ACTIVE, "30.00")
        third.tags = ["system"]
        repository.save(third)

        expect:
        repository.findByTypeJson("created").present
        repository.findByTypeNotEqualJson("created").size() == 2
        repository.findByPayloadRegex(".*world.*").size() == 1
        repository.findByPriorityExists(true).size() == 2
        repository.findByPriorityInJson([1, 2]).size() == 2
        repository.findByPriorityNotInJson([1, 2]).size() == 1
        repository.findByPayloadLike("%world%").size() == 1
        repository.findByPriorityNot(1).size() == 2
        repository.findByPayloadEmpty(true).size() == 2
        repository.findByTagsAll(["audit", "customer"]).size() == 1
    }
    // end::json-query-operators-usage[]

    // tag::aggregation-usage[]
    def "aggregations"() {
        given:
        repository.save(event("created", "a", ExampleEvent.Status.ACTIVE, "10.00"))
        repository.save(event("updated", "b", ExampleEvent.Status.ACTIVE, "30.00"))
        repository.save(event("archived", "c", ExampleEvent.Status.INACTIVE, "100.00"))

        expect:
        repository.findMaxScoreByStatus(ExampleEvent.Status.ACTIVE).orElseThrow() == 30.0d
        repository.findMinScoreByStatus(ExampleEvent.Status.ACTIVE).orElseThrow() == 10.0d
        repository.findSumScoreByStatus(ExampleEvent.Status.ACTIVE).orElseThrow() == 40.0d
        repository.findAvgScoreByStatus(ExampleEvent.Status.ACTIVE).orElseThrow() == 20.0d
        repository.findMaxEventDateByStatus(ExampleEvent.Status.ACTIVE).orElseThrow() == LocalDate.of(2024, 1, 2)
        repository.findMinEventDateByStatus(ExampleEvent.Status.ACTIVE).orElseThrow() == LocalDate.of(2024, 1, 1)
        repository.countDistinctType() == 3
    }
    // end::aggregation-usage[]

    // tag::json-project-usage[]
    def "json project"() {
        given:
        repository.save(event("created", "a", ExampleEvent.Status.ACTIVE, "10.00"))
        repository.save(event("updated", "b", ExampleEvent.Status.ACTIVE, "20.00"))
        repository.save(event("archived", "c", ExampleEvent.Status.INACTIVE, "30.00"))

        expect:
        repository.findTypesByStatus(ExampleEvent.Status.ACTIVE) == ["created", "updated"]
    }
    // end::json-project-usage[]

    // tag::dto-projection-usage[]
    def "dto projection"() {
        given:
        repository.save(event("created", "a", ExampleEvent.Status.ACTIVE, "10.00"))
        repository.save(event("updated", "b", ExampleEvent.Status.ACTIVE, "20.00"))
        repository.save(event("archived", "c", ExampleEvent.Status.INACTIVE, "30.00"))

        expect:
        repository.findByStatus(ExampleEvent.Status.ACTIVE)*.type == ["created", "updated"]
    }
    // end::dto-projection-usage[]

    // tag::type-mapping-usage[]
    def "type mapping round trip"() {
        given:
        Instant occurredAt = Instant.parse("2024-01-01T10:15:30Z")
        LocalDateTime eventDateTime = LocalDateTime.of(2024, 1, 1, 10, 15, 30)
        ExampleEvent event = event("typed", "payload", ExampleEvent.Status.PENDING, "123.45")
        event.eventDate = LocalDate.of(2024, 1, 1)
        event.occurredAt = occurredAt
        event.eventDateTime = eventDateTime
        event.data = "payload-bytes".bytes
        event.metadata = [source: "docs", tenant: "primary"]
        event.attempts = [
            first: new ExampleEvent.EventAttempt("worker-a", 1),
            second: new ExampleEvent.EventAttempt("worker-b", 2)
        ]
        event.note = Optional.of("optional note")
        event.location = new ExampleEvent.EventLocation("eu-west", "zone-a")

        when:
        ExampleEvent saved = repository.save(event)
        ExampleEvent reloaded = repository.findById(saved.id).orElseThrow()

        then:
        reloaded.status == ExampleEvent.Status.PENDING
        reloaded.eventDate == LocalDate.of(2024, 1, 1)
        reloaded.eventDateTime == eventDateTime
        reloaded.occurredAt == occurredAt
        reloaded.amount == new BigDecimal("123.45")
        reloaded.data == "payload-bytes".bytes
        reloaded.metadata.source == "docs"
        reloaded.attempts.first.handler == "worker-a"
        reloaded.attempts.second.retries == 2
        reloaded.note != null
        reloaded.note.toString().contains("optional note")
        reloaded.location.region == "eu-west"
    }
    // end::type-mapping-usage[]

    private static ExampleEvent event(String type, String payload, ExampleEvent.Status status, String amount) {
        ExampleEvent event = new ExampleEvent(type, payload, status, new BigDecimal(amount))
        event.score = Double.valueOf(amount)
        event.eventDate = switch (type) {
            case "created" -> LocalDate.of(2024, 1, 1)
            case "updated" -> LocalDate.of(2024, 1, 2)
            default -> LocalDate.of(2024, 1, 3)
        }
        event
    }
}
