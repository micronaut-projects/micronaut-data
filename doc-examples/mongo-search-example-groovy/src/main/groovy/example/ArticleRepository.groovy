package example

import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.vector.search.Score
import io.micronaut.data.model.vector.search.SearchResults
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.SearchRepository

// tag::repository[]
@MongoRepository
interface ArticleRepository extends CrudRepository<Article, String>, SearchRepository<Article, String> {
    List<Article> findByTitleSearch(String query) // <1>
    List<Article> findByTitleSearchPrefix(String query) // <2>
    SearchResults<Article> searchByBodySearchFuzzy(String query) // <3>
    SearchResults<Article> searchBySearch(String query, Score minScore) // <4>
    List<Article> findBySearchAndAuthor(String query, String author) // <5>
    SearchResults<Article> searchByEmbeddingNear(Vector embedding, Score maxDistance) // <6>
}
// end::repository[]
