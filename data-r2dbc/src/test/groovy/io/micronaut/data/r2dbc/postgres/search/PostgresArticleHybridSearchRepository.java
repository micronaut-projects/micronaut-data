package io.micronaut.data.r2dbc.postgres.search;

import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.search.tck.repositories.ArticleHybridSearchRepository;

@R2dbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresArticleHybridSearchRepository extends ArticleHybridSearchRepository {
}
