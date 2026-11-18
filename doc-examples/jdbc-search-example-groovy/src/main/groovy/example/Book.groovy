package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.search.SearchField
import io.micronaut.data.annotation.search.SearchWeight
import io.micronaut.data.annotation.search.Searchable
import io.micronaut.data.model.vector.Vector
import jakarta.persistence.Column
import org.jspecify.annotations.Nullable

// tag::entity[]
@MappedEntity
@Searchable(language = "english") // <1>
class Book {
    @Id
    @GeneratedValue
    Long id

    @SearchField(weight = SearchWeight.HIGH, fuzzy = true) // <2>
    String title

    @SearchField // <3>
    String description

    String author

    @Nullable
    @Column(length = 3)
    Vector embedding

    Book(String title, String description, String author) {
        this(title, description, author, null)
    }

    Book(String title, String description, String author, Vector embedding) {
        this.title = title
        this.description = description
        this.author = author
        this.embedding = embedding
    }
}
// end::entity[]
