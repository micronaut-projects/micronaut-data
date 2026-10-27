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
class Article {
    @Id
    @GeneratedValue
    String id
    @SearchField(weight = SearchWeight.HIGH) // <3>
    String title
    @SearchField
    String body
    String author
    @Column(length = 3) // <4>
    Vector embedding

    Article(String title, String body, String author, Vector embedding) {
        this.title = title
        this.body = body
        this.author = author
        this.embedding = embedding
    }
}
// end::entity[]
