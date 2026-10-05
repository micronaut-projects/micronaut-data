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
data class ETagBook(
    @field:Id
    @field:GeneratedValue
    val id: Long?,
    val title: String,

    @Relation(Relation.Kind.EMBEDDED)
    val bookDetails: BookDetails,

    @field:GeneratedETag
    val etag: String?
) {

    @Embeddable
    data class BookDetails(
        val pages: Int,
        @field:ETagValue(exclude = true)
        val chapters: Int
    )
}
// end::generated-etag[]
