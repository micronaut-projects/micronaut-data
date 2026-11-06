package example

import io.micronaut.core.annotation.Introspected

// tag::dto[]
@Introspected
data class MovieSummary(val title: String, val released: Int?)
// end::dto[]
