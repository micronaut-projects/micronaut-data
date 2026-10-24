package io.micronaut.data.jdbc.postgres.search

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.query.builder.sql.TextSearchDialect
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
        statements.size() == 5
        statements[0].startsWith('CREATE TABLE "search_book"')
        statements[1] == "CREATE OR REPLACE FUNCTION mn_regconfig(cfg text) RETURNS regconfig AS 'SELECT cfg::regconfig' LANGUAGE sql IMMUTABLE;"
        statements[2] == 'CREATE INDEX "idx_search_book_search" ON "search_book" USING GIN ((setweight(to_tsvector(mn_regconfig(coalesce("language",\'english\')),coalesce("title",\'\')),\'A\') || setweight(to_tsvector(mn_regconfig(coalesce("language",\'english\')),coalesce("description",\'\')),\'C\')));'
        // the fuzzy title gets a pg_trgm index
        statements[3] == 'CREATE EXTENSION IF NOT EXISTS pg_trgm;'
        statements[4] == 'CREATE INDEX "idx_search_book_title_trgm" ON "search_book" USING GIN ("title" gin_trgm_ops);'

        when:
        def all = builder.buildCreateTableStatements([entity] as RuntimePersistentEntity[])

        then:
        all.last() == statements[4]
    }

    void "no search index is generated for dialects without text search"() {
        expect:
        new SqlQueryBuilder(Dialect.H2).buildCreateTableStatements(new RuntimePersistentEntity<>(Book)).size() == 1
    }

    void "the search index names and statements are exposed for the runtime index operations"() {
        given:
        def builder = new SqlQueryBuilder(Dialect.POSTGRES)
        def entity = new RuntimePersistentEntity<>(Book)

        expect:
        builder.searchIndexNames(entity) == ['"idx_search_book_search"', '"idx_search_book_title_trgm"']
        builder.buildCreateSearchIndexStatements(entity) == builder.buildCreateTableStatements(entity).toList().drop(1)
        TextSearchDialect.forDialect(Dialect.POSTGRES).buildDropSearchIndex('"idx_search_book_search"') == 'DROP INDEX IF EXISTS "idx_search_book_search";'
        TextSearchDialect.forDialect(Dialect.POSTGRES).catalogIndexName('"idx_search_book_search"') == 'idx_search_book_search'
        TextSearchDialect.forDialect(Dialect.POSTGRES).buildSyncSearchIndex(['"idx_search_book_search"']).isEmpty()
    }
}
