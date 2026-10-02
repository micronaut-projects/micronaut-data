package example

import io.micronaut.core.annotation.Creator
import io.micronaut.serde.annotation.Serdeable

@Serdeable
class BookDto {
    final String id
    final String title
    final int pages

    BookDto(Book book) {
        this(book.id.toString(), book.title, book.pages)
    }

    @Creator
    BookDto(String id, String title, int pages) {
        this.id = id
        this.title = title
        this.pages = pages
    }
}
