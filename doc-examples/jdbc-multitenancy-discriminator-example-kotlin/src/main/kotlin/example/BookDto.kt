package example

import io.micronaut.core.annotation.Creator
import io.micronaut.serde.annotation.Serdeable

@Serdeable
data class BookDto @Creator constructor(
    val id: String,
    val title: String,
    val pages: Int
) {
    constructor(book: Book) : this(book.id.toString(), book.title, book.pages)
}
