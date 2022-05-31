
package example;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import org.bson.types.ObjectId;

import java.util.Date;

// tag::book[]
@MappedEntity
record Book(
        @Id @GeneratedValue ObjectId id,
        @DateCreated @Nullable Date dateCreated,
        String title,
        int pages) {

    Book(String title, int paged) {
        this(null, null, title, paged);
    }

}
// end::book[]