
package example;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import org.bson.types.ObjectId;

@MappedEntity
public record Product(
        @Id
        @GeneratedValue ObjectId id,
        String name,
        @Nullable
        @Relation(Relation.Kind.MANY_TO_ONE) Manufacturer manufacturer) {

    public Product(String name, @Nullable Manufacturer manufacturer) {
        this(null, name, manufacturer);
    }

}
