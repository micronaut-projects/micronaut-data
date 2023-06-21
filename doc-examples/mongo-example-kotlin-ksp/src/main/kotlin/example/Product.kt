package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import org.bson.types.ObjectId

@MappedEntity
data class Product(@field:Id @GeneratedValue
                   var id: ObjectId?,
                   var name: String,
                   @Relation(Relation.Kind.MANY_TO_ONE)
                   var manufacturer: Manufacturer?) {

    constructor(name: String, manufacturer: Manufacturer?) : this(null, name, manufacturer)

}
