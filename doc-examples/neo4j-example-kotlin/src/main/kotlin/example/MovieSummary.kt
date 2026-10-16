package example

import io.micronaut.serde.annotation.Serdeable

// tag::dto[]
@Serdeable
data class MovieSummary(val title: String, val released: Int?)
// end::dto[]
