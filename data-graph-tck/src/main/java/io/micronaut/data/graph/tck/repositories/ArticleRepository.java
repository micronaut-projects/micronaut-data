package io.micronaut.data.graph.tck.repositories;

import io.micronaut.data.graph.tck.entities.Article;
import io.micronaut.data.repository.CrudRepository;

public interface ArticleRepository extends CrudRepository<Article, String> {
}
