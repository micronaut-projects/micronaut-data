package example;

import io.micronaut.serde.annotation.Serdeable;

// tag::dto[]
@Serdeable
public record MovieSummary(String title, Integer released) {
}
// end::dto[]
