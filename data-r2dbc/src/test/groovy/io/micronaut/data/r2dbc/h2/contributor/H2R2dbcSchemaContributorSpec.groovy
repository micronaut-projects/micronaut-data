package io.micronaut.data.r2dbc.h2.contributor

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.r2dbc.config.R2dbcSchemaContributor
import io.micronaut.data.r2dbc.h2.H2TestPropertyProvider
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.runtime.schema.SchemaContext
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * An R2DBC schema contributor adds a domain before the generated tables and a view over them.
 */
class H2R2dbcSchemaContributorSpec extends Specification implements H2TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties + ['spec.name': 'H2R2dbcSchemaContributorSpec'])

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.CREATE_DROP
    }

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    void "the contributed domain, table column and view are created in order"() {
        when:
        List<String> row = query("INSERT INTO contributed_contact(email) VALUES ('ged@roke.example')")
            .then(query("SELECT email FROM contributed_contact_emails").collectList())
            .block()

        then: "the column uses the domain created before the table, and the view runs after it"
        row == ['ged@roke.example']
        context.getBean(ViewContributor).contexts*.dataSourceName() == ['default']
    }

    private Flux<String> query(String sql) {
        return Flux.usingWhen(context.getBean(ConnectionFactory).create(), { Connection connection ->
            Flux.from(connection.createStatement(sql).execute()).flatMap { result ->
                sql.startsWith('SELECT') ? result.map { r, m -> r.get(0, String) } : Flux.from(result.getRowsUpdated()).thenMany(Flux.empty())
            }
        }, { Connection connection -> connection.close() })
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'H2R2dbcSchemaContributorSpec')
    static class ViewContributor implements R2dbcSchemaContributor {

        List<SchemaContext> contexts = []

        @Override
        List<String> buildBeforeTablesStatements(SchemaContext context, List<PersistentEntity> entities) {
            contexts << context
            return ["CREATE DOMAIN IF NOT EXISTS email_address AS VARCHAR(320)"]
        }

        @Override
        List<String> buildCreateStatements(SchemaContext context, List<PersistentEntity> entities) {
            return ["CREATE VIEW contributed_contact_emails AS SELECT email FROM contributed_contact"]
        }

        @Override
        List<String> buildDropStatements(SchemaContext context, List<PersistentEntity> entities) {
            return ["DROP VIEW IF EXISTS contributed_contact_emails"]
        }
    }
}

@MappedEntity("contributed_contact")
class ContributedContact {
    @Id
    @GeneratedValue
    Long id

    @MappedProperty(definition = "email_address")
    String email
}
