package io.micronaut.data.r2dbc.contributor.restart

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.r2dbc.config.R2dbcSchemaContributor
import io.micronaut.data.runtime.schema.SchemaContext
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import spock.lang.Specification

/**
 * With create-drop, a restart replaces the objects of R2DBC contributors: a domain the tables use, and a table and a
 * view of two contributors where the later one's view depends on the earlier one's table.
 */
class H2R2dbcSchemaContributorRestartSpec extends Specification {

    void "a restart with create-drop recreates the domains the tables use and the objects of several contributors"() {
        given:
        String url = "r2dbc:h2:mem:///contributorRestart${System.nanoTime()};DB_CLOSE_DELAY=-1"

        when: "the first start creates the domain with 5 characters and a note"
        ApplicationContext first = start(url, 5)
        query(first, "INSERT INTO restart_note(text) VALUES ('before the restart')").blockLast()
        first.close()

        and: "the second start asks for 320 characters"
        ApplicationContext second = start(url, 320)
        query(second, "INSERT INTO restart_contact(email) VALUES ('ged@roke.example')").blockLast()

        then: "the domain and its column were recreated, and the note table under the view was dropped"
        query(second, "SELECT CAST(COUNT(*) AS VARCHAR) FROM restart_contact").blockFirst() == '1'
        query(second, "SELECT CAST(COUNT(*) AS VARCHAR) FROM restart_note_texts").blockFirst() == '0'

        cleanup:
        second?.close()
    }

    private static ApplicationContext start(String url, int emailSize) {
        return ApplicationContext.run([
            'spec.name'                                : 'H2R2dbcSchemaContributorRestartSpec',
            'spec.email-size'                          : emailSize,
            'r2dbc.datasources.default.url'            : url,
            'r2dbc.datasources.default.username'       : '',
            'r2dbc.datasources.default.password'       : '',
            'r2dbc.datasources.default.dialect'        : 'h2',
            'r2dbc.datasources.default.schema-generate': 'CREATE_DROP',
            'r2dbc.datasources.default.packages'       : 'io.micronaut.data.r2dbc.contributor.restart'
        ])
    }

    private static Flux<String> query(ApplicationContext context, String sql) {
        return Flux.usingWhen(context.getBean(ConnectionFactory).create(), { Connection connection ->
            Flux.from(connection.createStatement(sql).execute()).flatMap { result ->
                sql.startsWith('SELECT') ? result.map { r, m -> r.get(0, String) } : Flux.from(result.getRowsUpdated()).thenMany(Flux.empty())
            }
        }, { Connection connection -> connection.close() })
    }

    /**
     * The domain of the email column, whose size changes between starts.
     */
    @Singleton
    @Requires(property = 'spec.name', value = 'H2R2dbcSchemaContributorRestartSpec')
    static class DomainContributor implements R2dbcSchemaContributor {

        final int size

        DomainContributor(@Property(name = 'spec.email-size') int size) {
            this.size = size
        }

        @Override
        List<String> buildBeforeTablesDropStatements(SchemaContext context, List<PersistentEntity> entities) {
            return ["DROP DOMAIN IF EXISTS restart_email"]
        }

        @Override
        List<String> buildBeforeTablesStatements(SchemaContext context, List<PersistentEntity> entities) {
            return ["CREATE DOMAIN restart_email AS VARCHAR(${size})".toString()]
        }
    }

    /**
     * A table of its own, which a later contributor's view depends on.
     */
    @Singleton
    @Requires(property = 'spec.name', value = 'H2R2dbcSchemaContributorRestartSpec')
    static class NoteTableContributor implements R2dbcSchemaContributor {

        @Override
        int getOrder() {
            return 0
        }

        @Override
        List<String> buildCreateStatements(SchemaContext context, List<PersistentEntity> entities) {
            return ["CREATE TABLE restart_note (id BIGINT AUTO_INCREMENT PRIMARY KEY, text VARCHAR(100))"]
        }

        @Override
        List<String> buildDropStatements(SchemaContext context, List<PersistentEntity> entities) {
            return ["DROP TABLE IF EXISTS restart_note"]
        }
    }

    /**
     * A view over the note table of the earlier contributor.
     */
    @Singleton
    @Requires(property = 'spec.name', value = 'H2R2dbcSchemaContributorRestartSpec')
    static class NoteViewContributor implements R2dbcSchemaContributor {

        @Override
        int getOrder() {
            return 10
        }

        @Override
        List<String> buildCreateStatements(SchemaContext context, List<PersistentEntity> entities) {
            return ["CREATE VIEW restart_note_texts AS SELECT text FROM restart_note"]
        }

        @Override
        List<String> buildDropStatements(SchemaContext context, List<PersistentEntity> entities) {
            return ["DROP VIEW IF EXISTS restart_note_texts"]
        }
    }
}

@MappedEntity("restart_contact")
class RestartContact {
    @Id
    @GeneratedValue
    Long id

    @MappedProperty(definition = "restart_email")
    String email
}
