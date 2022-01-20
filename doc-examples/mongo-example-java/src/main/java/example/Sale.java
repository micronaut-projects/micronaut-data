
package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import org.bson.types.ObjectId;

@MappedEntity
public class Sale {

    @Relation(Relation.Kind.MANY_TO_ONE)
    private final Product product;
    @MappedProperty(converter = QuantityAttributeConverter.class)
    private final Quantity quantity;

    @Id
    @GeneratedValue
    private ObjectId id;

    public Sale(Product product, Quantity quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public Product getProduct() {
        return product;
    }

    public Quantity getQuantity() {
        return quantity;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }
}
