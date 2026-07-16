package example;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.ETagValue;
import io.micronaut.data.annotation.sql.ETaggable;
import io.micronaut.data.annotation.sql.GeneratedETag;
import org.jspecify.annotations.Nullable;

// tag::generated-etag[]
@MappedEntity("etag_book")
@ETaggable
public record ETagBook(
        @Id
        @GeneratedValue
        @Nullable Long id,
        String title,

        @Relation(Relation.Kind.EMBEDDED)
        BookDetails bookDetails,

        @GeneratedETag
        @Nullable String etag) {

    @Embeddable
    public record BookDetails(
            int pages,
            @ETagValue(exclude = true)
            int chapters) {
    }
}
// end::generated-etag[]
