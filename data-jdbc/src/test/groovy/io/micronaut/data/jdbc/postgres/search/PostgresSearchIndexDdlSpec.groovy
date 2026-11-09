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
        statements[1] == "CREATE OR REPLACE FUNCTION mn_regconfig(cfg text) RETURNS regconfig AS 'SELECT cfg::regconfig' LANGUAGE sql IMMUTABLE SET search_path = pg_catalog, pg_temp;"
        statements[2] == 'CREATE INDEX IF NOT EXISTS "idx_search_book_search" ON "search_book" USING GIN ((setweight(to_tsvector(mn_regconfig(coalesce("language",\'english\')),coalesce("title",\'\')),\'A\') || setweight(to_tsvector(mn_regconfig(coalesce("language",\'english\')),coalesce("description",\'\')),\'C\')));'
        // the fuzzy title gets a pg_trgm index
        statements[3] == 'CREATE EXTENSION IF NOT EXISTS pg_trgm;'
        statements[4] == 'CREATE INDEX IF NOT EXISTS "idx_search_book_title_trgm" ON "search_book" USING GIN ("title" gin_trgm_ops);'

        when:
        def all = builder.buildCreateTableStatements([entity] as RuntimePersistentEntity[])

        then:
        all.last() == statements[4]
    }

    void "a trigram index name longer than PostgreSQL keeps is shortened with a hash"() {
        when:
        def statements = new SqlQueryBuilder(Dialect.POSTGRES).buildCreateTableStatements(new RuntimePersistentEntity<>(LongNamedRecipe))
        def trigram = statements.find { it.contains("gin_trgm_ops") }
        def name = (trigram =~ /CREATE INDEX IF NOT EXISTS "([^"]+)"/)[0][1] as String

        then:
        name.length() == 63
        name.startsWith("idx_seasonal_recipes_from_the_regional_kitchen")
        name ==~ /.*_[0-9a-f]{16}/
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
        TextSearchDialect.forDialect(Dialect.POSTGRES).buildDropSearchIndex('"idx_search_book_search"', null) == 'DROP INDEX IF EXISTS "idx_search_book_search";'
        TextSearchDialect.forDialect(Dialect.POSTGRES).catalogIndexName('"idx_search_book_search"') == 'idx_search_book_search'
        TextSearchDialect.forDialect(Dialect.POSTGRES).buildSyncSearchIndex(['"idx_search_book_search"']).isEmpty()
    }

    void "the index lookup and drop of an entity in its own schema are qualified with the schema"() {
        given:
        def builder = new SqlQueryBuilder(Dialect.POSTGRES)
        def dialect = TextSearchDialect.forDialect(Dialect.POSTGRES)
        def schema = builder.searchIndexSchema(new RuntimePersistentEntity<>(CatalogRecipe))

        expect:
        schema == '"search_catalog"'
        builder.searchIndexSchema(new RuntimePersistentEntity<>(Book)) == null
        builder.searchIndexSchema(new RuntimePersistentEntity<>(MixedCaseRecipe)) == null
        dialect.buildDropSearchIndex('"idx_catalog_recipe_search"', schema) == 'DROP INDEX IF EXISTS "search_catalog"."idx_catalog_recipe_search";'
        dialect.searchIndexExistsQuery(schema) == "SELECT count(*) FROM pg_indexes WHERE schemaname = 'search_catalog' AND indexname = ?"
        dialect.searchIndexExistsQuery(null) == "SELECT count(*) FROM pg_indexes WHERE schemaname = current_schema() AND indexname = ?"
        dialect.searchIndexExistsQuery("Odd'Schema") == "SELECT count(*) FROM pg_indexes WHERE schemaname = 'odd''schema' AND indexname = ?"
    }

    void "an unquoted index name is looked up in the lower case PostgreSQL folds it to"() {
        given:
        def builder = new SqlQueryBuilder(Dialect.POSTGRES)
        def dialect = TextSearchDialect.forDialect(Dialect.POSTGRES)
        def names = builder.searchIndexNames(new RuntimePersistentEntity<>(MixedCaseRecipe))

        expect:
        names == ['IdxMixedCaseRecipe']
        dialect.catalogIndexName(names[0]) == 'idxmixedcaserecipe'
        dialect.catalogIndexName('"IdxQuoted"') == 'IdxQuoted'
    }
}
