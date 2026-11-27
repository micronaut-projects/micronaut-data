package io.micronaut.data.r2dbc.contributor.placeholder

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.r2dbc.config.R2dbcSchemaContributor
import io.micronaut.data.r2dbc.h2.H2TestPropertyProvider
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.runtime.schema.SchemaContext
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import spock.lang.Specification

/**
 * The contributed statements of R2DBC schema generation resolve property placeholders, as with JDBC.
 */
class H2R2dbcSchemaContributorPlaceholderSpec extends Specification implements H2TestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.CREATE_DROP
    }

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    void "a placeholder in a contributed statement is resolved"() {
        given:
        ApplicationContext context = ApplicationContext.run(properties + [
            'spec.name'        : 'H2R2dbcSchemaContributorPlaceholderSpec',
            'contributed.table': 'placeholder_contributed'
        ])

        when:
        Long rows = Flux.usingWhen(context.getBean(ConnectionFactory).create(), { Connection connection ->
            Flux.from(connection.createStatement("SELECT COUNT(*) FROM placeholder_contributed").execute())
                .flatMap { result -> result.map { r, m -> r.get(0, Long) } }
        }, { Connection connection -> connection.close() }).blockFirst()

        then:
        rows == 0

        cleanup:
        context?.close()
    }

    void "a missing property fails the generation in a #phase statement"() {
        when:
        ApplicationContext.run(properties + [
            'spec.name'             : 'H2R2dbcSchemaContributorPlaceholderSpec',
            'contributed.table'     : 'placeholder_missing',
            'contributed.missing-in': phase
        ])

        then:
        Throwable failure = thrown()
        causes(failure).any { it instanceof ConfigurationException && it.message.contains('contributed.missing') }

        where:
        phase << ['drop', 'before-tables-drop', 'before-tables', 'create']
    }

    private static List<Throwable> causes(Throwable failure) {
        List<Throwable> causes = []
        for (Throwable t = failure; t != null; t = t.cause) {
            causes << t
        }
        return causes
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'H2R2dbcSchemaContributorPlaceholderSpec')
    static class PlaceholderContributor implements R2dbcSchemaContributor {

        private final ApplicationContext applicationContext

        PlaceholderContributor(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext
        }

        @Override
        List<String> buildDropStatements(SchemaContext context, List<PersistentEntity> entities) {
            return ['DROP TABLE IF EXISTS ${contributed.table}'] + extra('drop')
        }

        @Override
        List<String> buildBeforeTablesDropStatements(SchemaContext context, List<PersistentEntity> entities) {
            return extra('before-tables-drop')
        }

        @Override
        List<String> buildBeforeTablesStatements(SchemaContext context, List<PersistentEntity> entities) {
            return extra('before-tables')
        }

        @Override
        List<String> buildCreateStatements(SchemaContext context, List<PersistentEntity> entities) {
            return ['CREATE TABLE ${contributed.table}(id INT)'] + extra('create')
        }

        private List<String> extra(String phase) {
            return applicationContext.getProperty('contributed.missing-in', String).orElse(null) == phase
                ? ['SELECT \'${contributed.missing}\'']
                : []
        }
    }
}
