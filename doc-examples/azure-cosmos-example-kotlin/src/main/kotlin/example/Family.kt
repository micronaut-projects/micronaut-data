package example

import io.micronaut.data.annotation.*
import io.micronaut.data.annotation.Transient
import io.micronaut.data.cosmos.annotation.ETag
import io.micronaut.data.cosmos.annotation.PartitionKey
import java.util.*

// tag::relations[]
@MappedEntity
data class Family(
    @field:Id
    val id: String,
    @PartitionKey
    var lastName: String,
    @Relation(value = Relation.Kind.EMBEDDED)
    var address: Address,
    @Relation(value = Relation.Kind.ONE_TO_MANY)
    var children: List<Child> = ArrayList(),
    // end::relations[]
    //...
    var tags: Array<String>? = null,
    var registered: Boolean,
    var registeredDate: Date? = null,
    // tag::locking[]
    @ETag
    var documentVersion: String? = null
    // end::locking[]
) {
    @Transient
    var comment: String? = null

    override fun toString(): String {
        return "Family(id='$id', lastName='$lastName', address=$address, children=$children, tags=${tags?.contentToString()}, registered=$registered, registeredDate=$registeredDate, documentVersion=$documentVersion), comment=$comment"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Family

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
