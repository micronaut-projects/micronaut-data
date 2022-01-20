
package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import org.bson.types.ObjectId

@MappedEntity
class Sale {
    @Id
    @GeneratedValue
    ObjectId id
    @Relation(Relation.Kind.MANY_TO_ONE)
    final Product product
    final Quantity quantity

    Sale(Product product, Quantity quantity) {
        this.product = product
        this.quantity = quantity
    }
}
