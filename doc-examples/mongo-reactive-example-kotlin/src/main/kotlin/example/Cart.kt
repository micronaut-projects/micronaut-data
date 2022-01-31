package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import org.bson.types.ObjectId

@MappedEntity
data class Cart(
        @field:Id @GeneratedValue
        val id: ObjectId?,
        @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "cart", cascade = [Relation.Cascade.ALL])
        val items: List<CartItem>?
) {

    constructor(items: List<CartItem>) : this(null, items)
}