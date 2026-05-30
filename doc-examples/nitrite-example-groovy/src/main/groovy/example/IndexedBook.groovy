package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Index
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.nitrite.annotation.FullTextIndex
import io.micronaut.data.nitrite.annotation.SpatialIndex
import org.locationtech.jts.geom.Geometry

@MappedEntity
// tag::compound-index[]
@Index(name = "book_title_pages", columns = ["title", "pages"])
// end::compound-index[]
class IndexedBook {
    @Id
    @GeneratedValue
    String id

    // tag::property-index[]
    @Index(columns = "title")
    // end::property-index[]
    String title

    int pages

    @FullTextIndex
    String description

    // tag::spatial-index[]
    @SpatialIndex
    // end::spatial-index[]
    Geometry location

    IndexedBook() {
    }

    IndexedBook(String title, int pages) {
        this.title = title
        this.pages = pages
    }
}
