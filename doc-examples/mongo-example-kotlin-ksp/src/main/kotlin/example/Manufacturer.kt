package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import org.bson.types.ObjectId

@MappedEntity
data class Manufacturer(
        @field:Id
        @GeneratedValue
        var id: ObjectId?,
        val name: String
)
