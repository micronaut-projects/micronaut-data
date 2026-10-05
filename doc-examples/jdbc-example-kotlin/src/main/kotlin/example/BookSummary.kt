package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class BookSummary(val title: String, val pages: Int)
