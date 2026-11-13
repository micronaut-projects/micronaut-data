/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.processor.sql

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.query.builder.sql.SqlSchemaNames
import io.micronaut.data.processor.visitors.AbstractDataSpec

class TextSearchIndexDdlSpec extends AbstractDataSpec {

    void "@Searchable(name) names the PostgreSQL search index"() {
        given:
        def entity = searchableEntity('book_text_idx')

        when:
        def statements = new SqlQueryBuilder(Dialect.POSTGRES).buildCreateTableStatements(entity)

        then:
        statements.last() == 'CREATE INDEX IF NOT EXISTS "book_text_idx" ON "book" USING GIN (to_tsvector(\'english\',"title"));'
    }

    void "@Searchable(name) prefixes the Oracle CONTEXT index of each search field"() {
        given:
        def entity = searchableEntity('book_text_idx')

        when:
        def statements = new SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(entity)

        then:
        statements.contains('CREATE INDEX "BOOK_TEXT_IDX_TITLE" ON "BOOK" ("TITLE") INDEXTYPE IS CTXSYS.CONTEXT PARAMETERS (\'SYNC (ON COMMIT)\');')
    }

    void "a @Searchable(name) that is not a plain identifier is rejected instead of changing the #dialect index DDL"() {
        given:
        def entity = searchableEntity(name)

        when:
        new SqlQueryBuilder(dialect).buildCreateTableStatements(entity)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('A search index name must be an identifier of letters, digits and underscores')

        where:
        [name, dialect] << [['idx\\"; DROP TABLE book; --', 'idx book', '1idx', 'idx-book'], [Dialect.POSTGRES, Dialect.ORACLE]].combinations()
    }

    void "a searchable JSON entity in a schema creates the schema before its table"() {
        given:
        def entity = buildEntity('test.JsonDoc', '''
import io.micronaut.data.annotation.search.*;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

@MappedEntity(value = "json_doc", schema = "docs")
@Searchable
@EntityRepresentation(type = EntityRepresentation.Type.COLUMN, columnType = EntityRepresentation.ColumnType.JSON, column = "DATA")
@Serdeable
class JsonDoc {
    @Id
    @JsonProperty("_id")
    private Long id;

    @SearchField
    private String title;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
''')
        def builder = new SqlQueryBuilder(Dialect.ORACLE)

        when:
        def single = builder.buildCreateTableStatements(entity)

        then: "the schema, an Oracle user, comes first, then the table and the unique index over the serialized id key"
        single[0] == SqlSchemaNames.oracleCreateSchemaStatement('"DOCS"', true)
        single[1] == 'CREATE TABLE "DOCS"."JSON_DOC" ("DATA" JSON NOT NULL);'
        single[2] == 'CREATE UNIQUE INDEX "IDX_JSON_DOC_ID" ON "DOCS"."JSON_DOC" (JSON_VALUE("DATA",\'$._id\' ERROR ON ERROR NULL ON EMPTY));'
        single[3].startsWith('CREATE SEARCH INDEX ')

        when:
        def several = builder.buildCreateTableStatements([entity] as io.micronaut.data.model.PersistentEntity[], Dialect.ORACLE)

        then:
        several[0] == single[0]
        several[1] == single[1]
        several[2] == single[2]
    }

    private def searchableEntity(String indexName) {
        return buildEntity('test.Book', """
import io.micronaut.data.annotation.search.*;

@MappedEntity("book")
@Searchable(name = "$indexName")
class Book {
    @Id
    @GeneratedValue
    private Long id;

    @SearchField
    private String title;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
""")
    }
}
