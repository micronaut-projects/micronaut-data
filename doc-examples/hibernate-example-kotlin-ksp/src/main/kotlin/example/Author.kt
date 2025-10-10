package example

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany

@Entity
data class Author(
    @field:Id
    val id: Long,
    val name: String,
    @JoinTable(
        name = "author_genre",
        joinColumns = [JoinColumn(name = "author_id")],
        inverseJoinColumns = [JoinColumn(name = "genre_id")]
    )
    @field:ManyToMany
    val genres: List<Genre>
)
