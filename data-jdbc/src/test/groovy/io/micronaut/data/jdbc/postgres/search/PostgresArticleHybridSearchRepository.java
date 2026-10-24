package io.micronaut.data.jdbc.postgres.search;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.search.tck.repositories.ArticleHybridSearchRepository;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresArticleHybridSearchRepository extends ArticleHybridSearchRepository {
}
