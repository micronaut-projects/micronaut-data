package example

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne

@Entity
data class Child(
        val name: String,
        @ManyToOne
        val parent: Parent? = null,
        @field:Id @GeneratedValue val id: Int? = null
) {

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
        result = 31 * result + (id ?: 0)
        return result
    }

    override fun toString(): String {
        return "Child(name='$name', parent=${parent?.id}, id=$id)"
    }


}

