package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import org.bson.types.ObjectId

@MappedEntity
data class Child(
        val name: String,
        @Relation(Relation.Kind.MANY_TO_ONE)
        val parent: Parent? = null,
        @field:Id @GeneratedValue val id: ObjectId? = null) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Child) return false

        if (name != other.name) return false
        if (parent?.id != other.parent?.id) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + parent?.id.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }

    override fun toString(): String {
        return "Child(name='$name', parent=${parent?.id}, id=$id)"
    }


}

