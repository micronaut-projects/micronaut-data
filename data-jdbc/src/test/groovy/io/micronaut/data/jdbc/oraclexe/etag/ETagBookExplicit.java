package io.micronaut.data.jdbc.oraclexe.etag;

import io.micronaut.data.annotation.*;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;

@MappedEntity("etag_book_explicit")
public record ETagBookExplicit(
    @Id
    @GeneratedValue
    @ETagValue
    Long id,

    @ETagValue
    String title,

    String notes,

    @Relation(Relation.Kind.EMBEDDED)
    BookDetails bookDetails,

    @GeneratedETag
    String etag
) {
    @Embeddable
    public record BookDetails(
        @ETagValue
        int pages,
        @ETagValue
        int chapters
    ) { }
}
