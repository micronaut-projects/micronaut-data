package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.search.SearchField
import io.micronaut.data.annotation.search.SearchWeight
import io.micronaut.data.annotation.search.Searchable
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.mongodb.annotation.MongoSearchIndex
import jakarta.persistence.Column

// tag::entity[]
@MappedEntity
@Searchable(language = "english") // <1>
@MongoSearchIndex(name = "articles") // <2>
data class Article(
    @field:Id
    @field:GeneratedValue
    val id: String? = null,
    @field:SearchField(weight = SearchWeight.HIGH) // <3>
    val title: String,
    @field:SearchField
    val body: String,
    val author: String,
    @field:Column(length = 3) // <4>
    val embedding: Vector? = null
)
// end::entity[]
