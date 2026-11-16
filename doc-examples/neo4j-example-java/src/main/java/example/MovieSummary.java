package example;

import io.micronaut.core.annotation.Introspected;

// tag::dto[]
@Introspected
public record MovieSummary(String title, Integer released) {
}
// end::dto[]
