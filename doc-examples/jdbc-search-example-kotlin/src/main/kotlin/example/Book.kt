package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.search.SearchField
import io.micronaut.data.annotation.search.SearchWeight
import io.micronaut.data.annotation.search.Searchable
import io.micronaut.data.model.vector.Vector
import jakarta.persistence.Column

// tag::entity[]
@MappedEntity
@Searchable(language = "english") // <1>
data class Book(
    @field:Id
    @field:GeneratedValue
    val id: Long? = null,

    @field:SearchField(weight = SearchWeight.HIGH) // <2>
    val title: String,

    @field:SearchField // <3>
    val description: String,

    val author: String,

    @field:Column(length = 3)
    val embedding: Vector? = null
)
// end::entity[]
