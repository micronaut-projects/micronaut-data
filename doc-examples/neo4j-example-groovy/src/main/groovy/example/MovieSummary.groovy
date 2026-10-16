package example

import io.micronaut.serde.annotation.Serdeable

// tag::dto[]
@Serdeable
class MovieSummary {
    String title
    Integer released
}
// end::dto[]
