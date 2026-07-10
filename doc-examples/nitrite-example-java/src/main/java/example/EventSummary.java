package example;

import io.micronaut.core.annotation.Introspected;

// tag::dto-projection[]
@Introspected
public record EventSummary(String type, ExampleEvent.Status status, Double score) {
}
// end::dto-projection[]
