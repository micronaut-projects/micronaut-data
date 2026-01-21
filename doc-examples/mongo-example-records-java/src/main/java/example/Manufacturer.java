
package example;

import org.jspecify.annotations.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import org.bson.types.ObjectId;

@MappedEntity
public record Manufacturer(
        @Id
        @GeneratedValue
        @Nullable
        ObjectId id,
        String name) {
}
