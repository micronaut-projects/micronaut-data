package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Index
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.nitrite.annotation.FullTextIndex
import io.micronaut.data.nitrite.annotation.SpatialIndex

// tag::compound-index[]
@MappedEntity
@Index(name = "book_title_pages", columns = ["title", "pages"])
class IndexedBook {
// end::compound-index[]
    @Id
    @GeneratedValue
    private String id

    // tag::property-index[]
    @Index(columns = "title")
    private String title
    // end::property-index[]

    private int pages

    @FullTextIndex
    private String description

    // tag::spatial-index[]
    @SpatialIndex
    private String location
    // end::spatial-index[]

    IndexedBook() {}

    IndexedBook(String title, int pages) {
        this.title = title
        this.pages = pages
    }

    String getId() {
        return id
    }

    void setId(String id) {
        this.id = id
    }

    String getTitle() {
        return title
    }

    void setTitle(String title) {
        this.title = title
    }

    int getPages() {
        return pages
    }

    void setPages(int pages) {
        this.pages = pages
    }

    String getDescription() {
        return description
    }

    void setDescription(String description) {
        this.description = description
    }

    String getLocation() {
        return location
    }

    void setLocation(String location) {
        this.location = location
    }
}
