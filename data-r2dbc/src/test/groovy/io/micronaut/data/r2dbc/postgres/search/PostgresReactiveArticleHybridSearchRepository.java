package io.micronaut.data.r2dbc.postgres.search;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactorSearchRepository;
import io.micronaut.data.search.tck.entities.Article;

import java.util.UUID;

@R2dbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresReactiveArticleHybridSearchRepository extends ReactorSearchRepository<Article, UUID> {
}
