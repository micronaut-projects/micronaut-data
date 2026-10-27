package io.micronaut.data.jdbc.oraclexe.search

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.query.builder.sql.TextSearchDialect
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.search.tck.entities.Book
import spock.lang.Specification

class OracleSearchIndexDdlSpec extends Specification {

    void "a searchable entity gets a CONTEXT index per search field on Oracle"() {
        when:
        def statements = new SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(new RuntimePersistentEntity<>(Book))

        then:
        statements.size() == 4
        statements[0].startsWith('CREATE TABLE "SEARCH_BOOK"')
        statements[1] == 'CREATE INDEX "IDX_SEARCH_BOOK_SEARCH_TITLE" ON "SEARCH_BOOK" ("TITLE") INDEXTYPE IS CTXSYS.CONTEXT PARAMETERS (\'SYNC (ON COMMIT)\');'
        statements[2] == 'CREATE INDEX "IDX_SEARCH_BOOK_SEARCH_DESCRIPTION" ON "SEARCH_BOOK" ("DESCRIPTION") INDEXTYPE IS CTXSYS.CONTEXT PARAMETERS (\'SYNC (ON COMMIT)\');'
        statements[3] == "BEGIN CTX_DDL.CREATE_POLICY('MN_SEARCH_POLICY'); EXCEPTION WHEN OTHERS THEN IF SQLCODE != -20000 THEN RAISE; END IF; END;"
    }

    void "a JSON entity gets one JSON search index over its column on Oracle"() {
        when:
        def statements = new SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(new RuntimePersistentEntity<>(OracleJsonBook))

        then:
        statements.size() == 3
        statements[0] == 'CREATE TABLE "JSON_BOOK" ("DATA" JSON NOT NULL);'
        statements[1] == 'CREATE SEARCH INDEX "IDX_JSON_BOOK_SEARCH" ON "JSON_BOOK" ("DATA") FOR JSON PARAMETERS (\'SYNC (ON COMMIT)\');'
        statements[2] == "BEGIN CTX_DDL.CREATE_POLICY('MN_SEARCH_POLICY'); EXCEPTION WHEN OTHERS THEN IF SQLCODE != -20000 THEN RAISE; END IF; END;"
    }

    void "the search index names and statements are exposed for the runtime index operations"() {
        given:
        def builder = new SqlQueryBuilder(Dialect.ORACLE)
        def dialect = TextSearchDialect.forDialect(Dialect.ORACLE)

        expect:
        builder.searchIndexNames(new RuntimePersistentEntity<>(Book)) == ['"IDX_SEARCH_BOOK_SEARCH_TITLE"', '"IDX_SEARCH_BOOK_SEARCH_DESCRIPTION"']
        builder.searchIndexNames(new RuntimePersistentEntity<>(OracleJsonBook)) == ['"IDX_JSON_BOOK_SEARCH"']
        builder.buildCreateSearchIndexStatements(new RuntimePersistentEntity<>(Book)) == builder.buildCreateTableStatements(new RuntimePersistentEntity<>(Book)).toList().drop(1)
        builder.buildCreateSearchIndexStatements(new RuntimePersistentEntity<>(OracleJsonBook)) == builder.buildCreateTableStatements(new RuntimePersistentEntity<>(OracleJsonBook)).toList().drop(1)
        dialect.buildDropSearchIndex('"IDX_JSON_BOOK_SEARCH"') == 'DROP INDEX "IDX_JSON_BOOK_SEARCH"'
        dialect.catalogIndexName('"IDX_JSON_BOOK_SEARCH"') == 'IDX_JSON_BOOK_SEARCH'
        dialect.catalogIndexName('idx_plain') == 'IDX_PLAIN'
        dialect.buildSyncSearchIndex(['"IDX_JSON_BOOK_SEARCH"']) == ["BEGIN CTX_DDL.SYNC_INDEX('IDX_JSON_BOOK_SEARCH'); END;"]
    }

    void "@OracleTextIndex sets the parameters of the CONTEXT indexes"() {
        when:
        def statements = new SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(new RuntimePersistentEntity<>(OracleTunedBook))

        then:
        statements.find { it.startsWith('CREATE INDEX') } == 'CREATE INDEX "IDX_TUNED_BOOK_SEARCH_TITLE" ON "TUNED_BOOK" ("TITLE") INDEXTYPE IS CTXSYS.CONTEXT PARAMETERS (\'SYNC (EVERY "FREQ=MINUTELY") MEMORY 50M\');'
    }
}
