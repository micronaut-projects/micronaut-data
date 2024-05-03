
package example

import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.DateCreated
import io.micronaut.data.annotation.MappedEntity
import jakarta.persistence.*

@MappedEntity // <1>
class Book {
    @Id @GeneratedValue Long id // <2>
    @DateCreated @Nullable Date dateCreated

    private String title
    private int pages

    Book(String title, int pages) {
        this.title = title
        this.pages = pages
    }

    String getTitle() {
        return title
    }

    int getPages() {
        return pages
    }
}
