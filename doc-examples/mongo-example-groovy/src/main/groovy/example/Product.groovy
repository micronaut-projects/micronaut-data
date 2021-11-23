
package example

import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import org.bson.types.ObjectId

@MappedEntity
class Product {
    @Id
    @GeneratedValue
    ObjectId id
    private String name
    @Nullable
    @Relation(Relation.Kind.MANY_TO_ONE)
    private Manufacturer manufacturer

    Product(String name, Manufacturer manufacturer) {
        this.name = name
        this.manufacturer = manufacturer
    }

    String getName() {
        return name
    }

    Manufacturer getManufacturer() {
        return manufacturer
    }
}
