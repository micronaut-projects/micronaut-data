package example;

import io.micronaut.data.neo4j.annotation.Neo4jRepository;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@Neo4jRepository
public interface ArticleRepository extends CrudRepository<Article, String> {

    List<Article> findByAuthor(String author);
}
