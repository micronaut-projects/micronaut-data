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
        statements.size() == 2
        statements[0].startsWith('CREATE TABLE "search_book"')
        statements[1] == 'CREATE INDEX IF NOT EXISTS "idx_search_book_search" ON "search_book" USING GIN ((setweight(to_tsvector(\'english\',coalesce("title",\'\')),\'A\') || setweight(to_tsvector(\'english\',coalesce("description",\'\')),\'C\')));'

        when:
        def all = builder.buildCreateTableStatements([entity] as RuntimePersistentEntity[])

        then:
        all.last() == statements[1]
    }

    void "no search index is generated for dialects without text search"() {
        expect:
        new SqlQueryBuilder(Dialect.H2).buildCreateTableStatements(new RuntimePersistentEntity<>(Book)).size() == 1
    }
}
