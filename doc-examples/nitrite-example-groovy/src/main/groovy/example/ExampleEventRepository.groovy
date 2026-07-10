package example

import io.micronaut.data.annotation.Query
import io.micronaut.data.nitrite.annotation.NitriteRepository
import io.micronaut.data.repository.CrudRepository

import java.time.LocalDate

@NitriteRepository
interface ExampleEventRepository extends CrudRepository<ExampleEvent, String> {

    // tag::json-query-operators[]
    @Query('{"type": {"$eq": :type}}')
    Optional<ExampleEvent> findByTypeJson(String type)

    @Query('{"type": {"$ne": :type}}')
    List<ExampleEvent> findByTypeNotEqualJson(String type)

    @Query('{"payload": {"$regex": :pattern}}')
    List<ExampleEvent> findByPayloadRegex(String pattern)

    @Query('{"priority": {"$exists": :exists}}')
    List<ExampleEvent> findByPriorityExists(Boolean exists)

    @Query('{"priority": {"$in": :priorities}}')
    List<ExampleEvent> findByPriorityInJson(List<Integer> priorities)

    @Query('{"priority": {"$nin": :priorities}}')
    List<ExampleEvent> findByPriorityNotInJson(List<Integer> priorities)

    @Query('{"payload": {"$like": :pattern}}')
    List<ExampleEvent> findByPayloadLike(String pattern)

    @Query('{"priority": {"$not": {"$eq": :priority}}}')
    List<ExampleEvent> findByPriorityNot(Integer priority)

    @Query('{"payload": {"$empty": :empty}}')
    List<ExampleEvent> findByPayloadEmpty(Boolean empty)

    @Query('{"tags": {"$all": :tags}}')
    List<ExampleEvent> findByTagsAll(List<String> tags)
    // end::json-query-operators[]

    // tag::json-project[]
    @Query('{"$project": "type", "status": {"$eq": :status}}')
    List<String> findTypesByStatus(ExampleEvent.Status status)
    // end::json-project[]

    // tag::dto-projection-repository[]
    List<EventSummary> findByStatus(ExampleEvent.Status status)
    // end::dto-projection-repository[]

    // tag::aggregations[]
    Optional<Double> findMaxScoreByStatus(ExampleEvent.Status status)

    Optional<Double> findMinScoreByStatus(ExampleEvent.Status status)

    Optional<Double> findSumScoreByStatus(ExampleEvent.Status status)

    Optional<Double> findAvgScoreByStatus(ExampleEvent.Status status)

    Optional<LocalDate> findMaxEventDateByStatus(ExampleEvent.Status status)

    Optional<LocalDate> findMinEventDateByStatus(ExampleEvent.Status status)

    long countDistinctType()
    // end::aggregations[]
}
