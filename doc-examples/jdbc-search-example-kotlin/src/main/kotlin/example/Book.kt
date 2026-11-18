package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.search.SearchField
import io.micronaut.data.annotation.search.SearchWeight
import io.micronaut.data.annotation.search.Searchable

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

    val author: String
)
// end::entity[]
