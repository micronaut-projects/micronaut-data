package io.micronaut.data.jdbc.oraclexe.etag;

import io.micronaut.data.annotation.*;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.GeneratedETag;
import io.micronaut.data.annotation.sql.ETaggable;

@ETaggable
@MappedEntity("etag_book")
public record ETagBook(
    @Id
    @GeneratedValue
    Long id,
    String title,

    @Relation(Relation.Kind.EMBEDDED)
    BookDetails bookDetails,

    @GeneratedETag
    String etag) {

    @Embeddable
    public record BookDetails(
        int pages,
        @ETagValue(exclude = true)
        int chapters
    ) {
    }
}
