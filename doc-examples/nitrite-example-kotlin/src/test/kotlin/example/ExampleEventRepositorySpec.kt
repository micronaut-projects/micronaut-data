package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

@MicronautTest(transactional = false)
class ExampleEventRepositorySpec {

    @Inject
    lateinit var repository: ExampleEventRepository

    @AfterEach
    fun cleanup() {
        repository.deleteAll()
    }

    // tag::json-query-operators-usage[]
    @Test
    fun testJsonQueryOperators() {
        val first = event("created", "hello world", ExampleEvent.Status.ACTIVE, "10.00")
        first.priority = 1
        first.tags = listOf("audit", "customer")
        repository.save(first)

        val second = event("updated", "", ExampleEvent.Status.INACTIVE, "20.00")
        second.priority = 2
        second.tags = listOf("audit")
        repository.save(second)

        val third = event("deleted", null, ExampleEvent.Status.ACTIVE, "30.00")
        third.tags = listOf("system")
        repository.save(third)

        assertTrue(repository.findByTypeJson("created").isPresent)
        assertEquals(2, repository.findByTypeNotEqualJson("created").size)
        assertEquals(1, repository.findByPayloadRegex(".*world.*").size)
        assertEquals(2, repository.findByPriorityExists(true).size)
        assertEquals(2, repository.findByPriorityInJson(listOf(1, 2)).size)
        assertEquals(1, repository.findByPriorityNotInJson(listOf(1, 2)).size)
        assertEquals(1, repository.findByPayloadLike("%world%").size)
        assertEquals(2, repository.findByPriorityNot(1).size)
        assertEquals(2, repository.findByPayloadEmpty(true).size)
        assertEquals(1, repository.findByTagsAll(listOf("audit", "customer")).size)
    }
    // end::json-query-operators-usage[]

    // tag::aggregation-usage[]
    @Test
    fun testAggregations() {
        repository.save(event("created", "a", ExampleEvent.Status.ACTIVE, "10.00"))
        repository.save(event("updated", "b", ExampleEvent.Status.ACTIVE, "30.00"))
        repository.save(event("archived", "c", ExampleEvent.Status.INACTIVE, "100.00"))

        assertEquals(30.0, repository.findMaxScoreByStatus(ExampleEvent.Status.ACTIVE).orElseThrow())
        assertEquals(10.0, repository.findMinScoreByStatus(ExampleEvent.Status.ACTIVE).orElseThrow())
        assertEquals(40.0, repository.findSumScoreByStatus(ExampleEvent.Status.ACTIVE).orElseThrow())
        assertEquals(20.0, repository.findAvgScoreByStatus(ExampleEvent.Status.ACTIVE).orElseThrow())
        assertEquals(LocalDate.of(2024, 1, 2), repository.findMaxEventDateByStatus(ExampleEvent.Status.ACTIVE).orElseThrow())
        assertEquals(LocalDate.of(2024, 1, 1), repository.findMinEventDateByStatus(ExampleEvent.Status.ACTIVE).orElseThrow())
        assertEquals(3, repository.countDistinctType())
    }
    // end::aggregation-usage[]

    // tag::json-project-usage[]
    @Test
    fun testJsonProject() {
        repository.save(event("created", "a", ExampleEvent.Status.ACTIVE, "10.00"))
        repository.save(event("updated", "b", ExampleEvent.Status.ACTIVE, "20.00"))
        repository.save(event("archived", "c", ExampleEvent.Status.INACTIVE, "30.00"))

        val activeTypes = repository.findTypesByStatus(ExampleEvent.Status.ACTIVE)

        assertEquals(listOf("created", "updated"), activeTypes)
    }
    // end::json-project-usage[]

    // tag::dto-projection-usage[]
    @Test
    fun testDtoProjection() {
        repository.save(event("created", "a", ExampleEvent.Status.ACTIVE, "10.00"))
        repository.save(event("updated", "b", ExampleEvent.Status.ACTIVE, "20.00"))
        repository.save(event("archived", "c", ExampleEvent.Status.INACTIVE, "30.00"))

        val summaries = repository.findByStatus(ExampleEvent.Status.ACTIVE)

        assertEquals(listOf("created", "updated"), summaries.map { it.type })
    }
    // end::dto-projection-usage[]

    // tag::type-mapping-usage[]
    @Test
    fun testTypeMappingRoundTrip() {
        val occurredAt = Instant.parse("2024-01-01T10:15:30Z")
        val eventDateTime = LocalDateTime.of(2024, 1, 1, 10, 15, 30)
        val event = event("typed", "payload", ExampleEvent.Status.PENDING, "123.45")
        event.eventDate = LocalDate.of(2024, 1, 1)
        event.occurredAt = occurredAt
        event.eventDateTime = eventDateTime
        event.data = "payload-bytes".toByteArray(StandardCharsets.UTF_8)
        event.metadata = mapOf("source" to "docs", "tenant" to "primary")
        event.attempts = mapOf(
            "first" to ExampleEvent.EventAttempt("worker-a", 1),
            "second" to ExampleEvent.EventAttempt("worker-b", 2)
        )
        event.note = Optional.of("optional note")
        event.location = ExampleEvent.EventLocation("eu-west", "zone-a")

        val saved = repository.save(event)
        val reloaded = repository.findById(saved.id!!).orElseThrow()

        assertEquals(ExampleEvent.Status.PENDING, reloaded.status) // <1>
        assertEquals(LocalDate.of(2024, 1, 1), reloaded.eventDate) // <2>
        assertEquals(eventDateTime, reloaded.eventDateTime) // <2>
        assertEquals(occurredAt, reloaded.occurredAt) // <2>
        assertEquals(BigDecimal("123.45"), reloaded.amount) // <3>
        assertArrayEquals("payload-bytes".toByteArray(StandardCharsets.UTF_8), reloaded.data) // <4>
        assertEquals("docs", reloaded.metadata?.get("source")) // <5>
        assertEquals("worker-a", reloaded.attempts?.get("first")?.handler) // <5>
        assertEquals(2, reloaded.attempts?.get("second")?.retries) // <5>
        assertNotNull(reloaded.note) // <6>
        assertTrue(reloaded.note.toString().contains("optional note")) // <6>
        assertEquals("eu-west", reloaded.location?.region) // <7>
    }
    // end::type-mapping-usage[]

    private fun event(type: String, payload: String?, status: ExampleEvent.Status, amount: String): ExampleEvent {
        val event = ExampleEvent(type, payload, status, BigDecimal(amount))
        event.score = amount.toDouble()
        event.eventDate = when (type) {
            "created" -> LocalDate.of(2024, 1, 1)
            "updated" -> LocalDate.of(2024, 1, 2)
            else -> LocalDate.of(2024, 1, 3)
        }
        return event
    }
}
