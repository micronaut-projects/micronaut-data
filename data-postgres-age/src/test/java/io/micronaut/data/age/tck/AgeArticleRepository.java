package io.micronaut.data.age.tck;

import io.micronaut.data.age.annotation.AgeRepository;
import io.micronaut.data.graph.tck.repositories.ArticleRepository;

@AgeRepository
public interface AgeArticleRepository extends ArticleRepository {
}
