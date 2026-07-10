package example

import io.micronaut.data.annotation.Query
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository
import java.time.LocalDate
import java.util.Optional

@NitriteRepository
interface ExampleEventRepository : CrudRepository<ExampleEvent, String> {

    // tag::json-query-operators[]
    @Query("{\"type\": {\"\$eq\": :type}}")
    fun findByTypeJson(type: String): Optional<ExampleEvent>

    @Query("{\"type\": {\"\$ne\": :type}}")
    fun findByTypeNotEqualJson(type: String): List<ExampleEvent>

    @Query("{\"payload\": {\"\$regex\": :pattern}}")
    fun findByPayloadRegex(pattern: String): List<ExampleEvent>

    @Query("{\"priority\": {\"\$exists\": :exists}}")
    fun findByPriorityExists(exists: Boolean): List<ExampleEvent>

    @Query("{\"priority\": {\"\$in\": :priorities}}")
    fun findByPriorityInJson(priorities: List<Int>): List<ExampleEvent>

    @Query("{\"priority\": {\"\$nin\": :priorities}}")
    fun findByPriorityNotInJson(priorities: List<Int>): List<ExampleEvent>

    @Query("{\"payload\": {\"\$like\": :pattern}}")
    fun findByPayloadLike(pattern: String): List<ExampleEvent>

    @Query("{\"priority\": {\"\$not\": {\"\$eq\": :priority}}}")
    fun findByPriorityNot(priority: Int): List<ExampleEvent>

    @Query("{\"payload\": {\"\$empty\": :empty}}")
    fun findByPayloadEmpty(empty: Boolean): List<ExampleEvent>

    @Query("{\"tags\": {\"\$all\": :tags}}")
    fun findByTagsAll(tags: List<String>): List<ExampleEvent>
    // end::json-query-operators[]

    // tag::json-project[]
    @Query("{\"\$project\": \"type\", \"status\": {\"\$eq\": :status}}")
    fun findTypesByStatus(status: ExampleEvent.Status): List<String>
    // end::json-project[]

    // tag::dto-projection-repository[]
    fun findByStatus(status: ExampleEvent.Status): List<EventSummary>
    // end::dto-projection-repository[]

    // tag::aggregations[]
    fun findMaxScoreByStatus(status: ExampleEvent.Status): Optional<Double>

    fun findMinScoreByStatus(status: ExampleEvent.Status): Optional<Double>

    fun findSumScoreByStatus(status: ExampleEvent.Status): Optional<Double>

    fun findAvgScoreByStatus(status: ExampleEvent.Status): Optional<Double>

    fun findMaxEventDateByStatus(status: ExampleEvent.Status): Optional<LocalDate>

    fun findMinEventDateByStatus(status: ExampleEvent.Status): Optional<LocalDate>

    fun countDistinctType(): Long
    // end::aggregations[]
}
