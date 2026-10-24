package io.micronaut.data.jdbc.postgres.search

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.search.tck.entities.Book
import spock.lang.Specification

class PostgresSearchIndexDdlSpec extends Specification {

    void "a searchable entity gets a GIN index over its weighted tsvector on PostgreSQL"() {
        given:
        def builder = new SqlQueryBuilder(Dialect.POSTGRES)
        def entity = new RuntimePersistentEntity<>(Book)

        when:
        def statements = builder.buildCreateTableStatements(entity)

        then:
        statements.size() == 4
        statements[0].startsWith('CREATE TABLE "search_book"')
        statements[1] == 'CREATE INDEX "idx_search_book_search" ON "search_book" USING GIN ((setweight(to_tsvector(coalesce("language",\'english\')::regconfig,coalesce("title",\'\')),\'A\') || setweight(to_tsvector(coalesce("language",\'english\')::regconfig,coalesce("description",\'\')),\'C\')));'
        // the fuzzy title gets a pg_trgm index
        statements[2] == 'CREATE EXTENSION IF NOT EXISTS pg_trgm;'
        statements[3] == 'CREATE INDEX "idx_search_book_title_trgm" ON "search_book" USING GIN ("title" gin_trgm_ops);'

        when:
        def all = builder.buildCreateTableStatements([entity] as RuntimePersistentEntity[])

        then:
        all.last() == statements[3]
    }

    void "no search index is generated for dialects without text search"() {
        expect:
        new SqlQueryBuilder(Dialect.H2).buildCreateTableStatements(new RuntimePersistentEntity<>(Book)).size() == 1
    }
}
