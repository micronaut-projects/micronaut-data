package example

import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.sql.ETagValue
import io.micronaut.data.annotation.sql.ETaggable
import io.micronaut.data.annotation.sql.GeneratedETag

// tag::generated-etag[]
@MappedEntity("etag_book")
@ETaggable
class ETagBook {

    @Id
    @GeneratedValue
    Long id

    String title

    @Relation(Relation.Kind.EMBEDDED)
    BookDetails bookDetails

    @GeneratedETag
    String etag

    @Embeddable
    static class BookDetails {

        int pages

        @ETagValue(exclude = true)
        int chapters
    }
}
// end::generated-etag[]
