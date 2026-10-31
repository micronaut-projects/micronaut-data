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
class Book {
    @Id
    @GeneratedValue
    Long id

    @SearchField(weight = SearchWeight.HIGH) // <2>
    String title

    @SearchField // <3>
    String description

    String author

    Book(String title, String description, String author) {
        this.title = title
        this.description = description
        this.author = author
    }
}
// end::entity[]
