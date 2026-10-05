package example

import io.micronaut.serde.annotation.Serdeable

@Serdeable
class BookSummary {
    String title
    int pages
}
