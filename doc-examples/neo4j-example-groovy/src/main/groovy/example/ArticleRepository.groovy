package example

import io.micronaut.data.neo4j.annotation.Neo4jRepository
import io.micronaut.data.repository.CrudRepository

@Neo4jRepository
interface ArticleRepository extends CrudRepository<Article, String> {

    List<Article> findByAuthor(String author)
}
