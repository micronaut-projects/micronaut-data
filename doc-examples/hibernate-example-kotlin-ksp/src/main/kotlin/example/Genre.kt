package example

import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
data class Genre(
    @field:Id
    val id: Long,
    val name: String
)
