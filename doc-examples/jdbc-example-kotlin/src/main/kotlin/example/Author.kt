package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation

@MappedEntity
data class Author(
    @field:Id
    val id: Long,
    val name: String,
    @Relation(value = Relation.Kind.MANY_TO_MANY)
    val genres: List<Genre>
)
