package example

import io.micronaut.core.annotation.Introspected

// tag::dto-projection[]
@Introspected
class EventSummary {
    final String type
    final ExampleEvent.Status status
    final Double score

    EventSummary(String type, ExampleEvent.Status status, Double score) {
        this.type = type
        this.status = status
        this.score = score
    }
}
// end::dto-projection[]
