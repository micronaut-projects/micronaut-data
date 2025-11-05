package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation

@MappedEntity
data class SampleEntity(
    @field:Id
    val id: Long,

    val name: String? = null,

    @GeneratedValue
    val example: String? = null,

    @Relation(value = Relation.Kind.EMBEDDED)
    val part: Part = Part()
)
