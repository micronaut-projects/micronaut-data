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
    var id: String? = null

    // tag::property-index[]
    @Index(columns = "title")
    var title: String? = null
    // end::property-index[]

    var pages: Int = 0

    @FullTextIndex
    var description: String? = null

    @SpatialIndex
    var location: String? = null

    constructor()

    constructor(title: String?, pages: Int) {
        this.title = title
        this.pages = pages
    }
}
