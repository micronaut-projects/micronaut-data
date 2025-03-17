
package example

import io.micronaut.data.annotation.Relation
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id

@Entity
data class Book(@Id
                @GeneratedValue
                val id: Long,
                val title: String,
                val pages: Int = 0,
                @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "book", cascade = [Relation.Cascade.ALL])
                val reviews: Set<Review>) {
    constructor(id: Long, title: String, pages: Int) : this(id, title, pages, setOf()) {
    }
}
