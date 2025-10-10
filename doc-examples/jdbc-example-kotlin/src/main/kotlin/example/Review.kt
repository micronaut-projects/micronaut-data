
package example

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne

@Entity
data class Review(@Id
                  @GeneratedValue
                  val id: Long?,
                  val reviewer: String,
                  val content: String,
                  @ManyToOne
                  val book: Book?) {
    constructor(reviewer: String, content: String) : this(null, reviewer, content, null) {
    }
}
