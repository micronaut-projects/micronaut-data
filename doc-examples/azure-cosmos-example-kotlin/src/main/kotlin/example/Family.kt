package example

import io.micronaut.data.annotation.*
import io.micronaut.data.annotation.Transient
import io.micronaut.data.cosmos.annotation.ETag
import io.micronaut.data.cosmos.annotation.PartitionKey
import java.util.*

@MappedEntity
class Family {
    @Id
    var id: String? = null

    @PartitionKey
    var lastName: String? = null

    @Relation(value = Relation.Kind.EMBEDDED)
    var address: Address? = null

    @Relation(value = Relation.Kind.ONE_TO_MANY)
    var children: List<Child> = ArrayList()
    var registered = false
    var registeredDate: Date? = null
    var tags: Array<String>? = null

    @ETag
    var documentVersion: String? = null

    @Transient
    var comment: String? = null
}
