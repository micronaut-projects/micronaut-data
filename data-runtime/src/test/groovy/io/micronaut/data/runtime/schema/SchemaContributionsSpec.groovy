package io.micronaut.data.runtime.schema

import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import spock.lang.Specification

class SchemaContributionsSpec extends Specification {

    PersistentEntity book = Stub() { getName() >> 'Book' }
    PersistentEntity author = Stub() { getName() >> 'Author' }
    PersistentEntity vertex = Stub() { getName() >> 'Vertex' }

    void "selected entities are left out of the generated tables, in contributor order"() {
        given:
        def first = contributor([vertex], ['DROP GRAPH g'], ['CREATE DOMAIN d'], ['CREATE GRAPH g'])
        def second = new SchemaContributor() {
            @Override
            List<PersistentEntity> selectEntities(SchemaContext context, List<PersistentEntity> entities) {
                assert !entities.contains(vertex): 'an entity selected by an earlier contributor is not offered again'
                return [author]
            }
        }

        when:
        def contributions = SchemaContributions.of([first, second], context(SchemaGenerate.CREATE_DROP))

        then:
        contributions.tableEntities() as List == [book]
        contributions.dropStatements() == ['DROP GRAPH g']
        contributions.beforeTablesStatements() == ['CREATE DOMAIN d']
        contributions.createStatements() == ['CREATE GRAPH g']
        !contributions.isEmpty()
    }

    void "a contributor without entities still contributes statements"() {
        when:
        def contributions = SchemaContributions.of([contributor([], [], [], ['CREATE VIEW v'])], context(SchemaGenerate.CREATE))

        then:
        contributions.tableEntities() as List == [book, author, vertex]
        contributions.createStatements() == ['CREATE VIEW v']
    }

    void "drop statements only run with CREATE_DROP"() {
        expect:
        SchemaContributions.of([contributor([], ['DROP VIEW v'], [], [])], context(SchemaGenerate.CREATE)).dropStatements().isEmpty()
    }

    void "validation excludes the selected entities and runs no statements"() {
        when:
        def contributions = SchemaContributions.of([contributor([vertex], ['DROP'], ['BEFORE'], ['CREATE'])], context(SchemaGenerate.VALIDATE))

        then:
        contributions.tableEntities() as List == [book, author]
        contributions.dropStatements().isEmpty()
        contributions.beforeTablesStatements().isEmpty()
        contributions.createStatements().isEmpty()
    }

    void "no entities and no statements is empty"() {
        expect:
        SchemaContributions.of([], new SchemaContext('default', Dialect.H2, null, SchemaGenerate.CREATE, [])).isEmpty()
    }

    void "the context describes the data source and keeps a copy of its entities"() {
        given:
        def entities = [book]
        def context = new SchemaContext('orders', Dialect.ORACLE, '23.26', SchemaGenerate.CREATE, entities)

        when:
        entities << author

        then:
        context.dataSourceName() == 'orders'
        context.dialect() == Dialect.ORACLE
        context.dialectVersion() == '23.26'
        context.entities() == [book]
    }

    void "contributions are equal when their tables and statements are"() {
        given:
        def contributions = new SchemaContributions([book] as PersistentEntity[], ['DROP'], ['BEFORE'], ['CREATE'])
        def same = new SchemaContributions([book] as PersistentEntity[], ['DROP'], ['BEFORE'], ['CREATE'])

        expect:
        contributions == same
        contributions.hashCode() == same.hashCode()
        contributions != new SchemaContributions([author] as PersistentEntity[], ['DROP'], ['BEFORE'], ['CREATE'])
        contributions != new SchemaContributions([book] as PersistentEntity[], [], ['BEFORE'], ['CREATE'])
        contributions != new SchemaContributions([book] as PersistentEntity[], ['DROP'], [], ['CREATE'])
        contributions != new SchemaContributions([book] as PersistentEntity[], ['DROP'], ['BEFORE'], [])
        !contributions.equals('CREATE')
        contributions.toString() == 'SchemaContributions{tables=1, drop=[DROP], beforeTables=[BEFORE], create=[CREATE]}'
    }

    void "contributions without tables or statements are empty"() {
        expect:
        new SchemaContributions(new PersistentEntity[0], [], [], []).isEmpty()
        !new SchemaContributions(new PersistentEntity[0], ['DROP'], [], []).isEmpty()
        !new SchemaContributions(new PersistentEntity[0], [], ['BEFORE'], []).isEmpty()
        !new SchemaContributions(new PersistentEntity[0], [], [], ['CREATE']).isEmpty()
    }

    private SchemaContext context(SchemaGenerate schemaGenerate) {
        return new SchemaContext('default', Dialect.H2, null, schemaGenerate, [book, author, vertex])
    }

    private static SchemaContributor contributor(List<PersistentEntity> selected, List<String> drop, List<String> before, List<String> create) {
        return new SchemaContributor() {
            @Override
            List<PersistentEntity> selectEntities(SchemaContext context, List<PersistentEntity> entities) {
                return selected
            }

            @Override
            List<String> buildBeforeTablesStatements(SchemaContext context, List<PersistentEntity> entities) {
                return before
            }

            @Override
            List<String> buildCreateStatements(SchemaContext context, List<PersistentEntity> entities) {
                return create
            }

            @Override
            List<String> buildDropStatements(SchemaContext context, List<PersistentEntity> entities) {
                return drop
            }
        }
    }
}
