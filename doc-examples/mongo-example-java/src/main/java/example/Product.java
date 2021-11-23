
package example;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import org.bson.types.ObjectId;

@MappedEntity
public class Product {

    @Id
    @GeneratedValue
    private ObjectId id;
    private String name;
    @Nullable
    @Relation(Relation.Kind.MANY_TO_ONE)
    private Manufacturer manufacturer;

    public Product(String name, @Nullable Manufacturer manufacturer) {
        this.name = name;
        this.manufacturer = manufacturer;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public Manufacturer getManufacturer() {
        return manufacturer;
    }
}
