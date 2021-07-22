package example

import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation

@MappedEntity
data class CartItem(
        @field:Id @GeneratedValue
        val id: Long?,
        val name: String,
        @Nullable
        @Relation(value = Relation.Kind.MANY_TO_ONE)
        val cart: Cart?
) {
    constructor(name: String) : this(null, name, null)
}
