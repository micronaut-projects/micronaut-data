package example

import io.micronaut.data.annotation.*
import io.micronaut.data.annotation.Transient
import io.micronaut.data.cosmos.annotation.ETag
import io.micronaut.data.cosmos.annotation.PartitionKey
import java.util.*

// tag::relations[]
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
    // end::relations[]
    //...

    var registered = false
    var registeredDate: Date? = null
    var tags: Array<String>? = null

    // tag::locking[]
    @ETag
    var documentVersion: String? = null
    // end::locking[]

    @Transient
    var comment: String? = null
}
