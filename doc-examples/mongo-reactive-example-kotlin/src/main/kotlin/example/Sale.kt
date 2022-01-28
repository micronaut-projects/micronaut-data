
package example

import io.micronaut.data.annotation.*
import org.bson.types.ObjectId

@MappedEntity
data class Sale(
    @field:Id
    @GeneratedValue
    var id: ObjectId?,
    @Relation(Relation.Kind.MANY_TO_ONE)
    val product: Product,
    @MappedProperty(converter = QuantityAttributeConverter::class)
    val quantity: Quantity
)