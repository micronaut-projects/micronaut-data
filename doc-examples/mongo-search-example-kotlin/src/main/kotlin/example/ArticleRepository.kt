package example

import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.vector.search.Score
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.SearchRepository

// tag::repository[]
@MongoRepository
interface ArticleRepository : CrudRepository<Article, String>, SearchRepository<Article, String> {
    fun findByTitleSearch(query: String): List<Article> // <1>
    fun findByTitleSearchPrefix(query: String): List<Article> // <2>
    fun searchByBodySearchFuzzy(query: String): SearchResults<Article> // <3>
    fun searchBySearch(query: String, minScore: Score): SearchResults<Article> // <4>
    fun findBySearchAndAuthor(query: String, author: String): List<Article> // <5>
    fun searchByEmbeddingNear(embedding: Vector, maxDistance: Score): SearchResults<Article> // <6>
}
// end::repository[]
