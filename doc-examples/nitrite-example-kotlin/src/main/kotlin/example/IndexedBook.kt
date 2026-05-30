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
class IndexedBook(
    // tag::property-index[]
    @Index(columns = ["title"])
    // end::property-index[]
    var title: String,
    var pages: Int
) {
    @Id
    @GeneratedValue
    var id: String? = null

    @FullTextIndex
    var description: String? = null

    // tag::spatial-index[]
    @SpatialIndex
    // end::spatial-index[]
    var location: Geometry? = null
}
