
package example;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import org.bson.types.ObjectId;

@MappedEntity
public record Sale(
        @Id
        @GeneratedValue ObjectId id,
        @Relation(Relation.Kind.MANY_TO_ONE) Product product,
        @MappedProperty(converter = QuantityAttributeConverter.class) Quantity quantity) {

    public Sale(Product product, Quantity quantity) {
        this(null, product, quantity);
    }
}
