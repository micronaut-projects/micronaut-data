
package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Version;
import org.bson.types.ObjectId;

// tag::student[]
@MappedEntity
public record Student(
        @Id
        @GeneratedValue ObjectId id,
        @Version Long version,
        String name) {
}
// end::student[]
