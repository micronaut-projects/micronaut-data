package example

import io.micronaut.core.annotation.Introspected

// tag::dto-projection[]
@Introspected
data class EventSummary(val type: String, val status: ExampleEvent.Status, val score: Double?)
// end::dto-projection[]
