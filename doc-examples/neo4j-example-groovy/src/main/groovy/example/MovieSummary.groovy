package example

import io.micronaut.core.annotation.Introspected

// tag::dto[]
@Introspected
class MovieSummary {
    String title
    Integer released
}
// end::dto[]
