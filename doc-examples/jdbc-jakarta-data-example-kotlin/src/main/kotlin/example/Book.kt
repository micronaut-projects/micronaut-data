package example

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id

@Entity
data class Book(
    @field:Id
    @field:GeneratedValue
    val id: Long? = null,
    val title: String,
    val pages: Int
)
