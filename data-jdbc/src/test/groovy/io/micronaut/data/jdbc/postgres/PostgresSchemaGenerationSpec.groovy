package io.micronaut.data.jdbc.postgres

import groovy.sql.Sql
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.core.order.Ordered
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.annotation.MockBean
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton
import javax.sql.DataSource

@MicronautTest
class PostgresSchemaGenerationSpec extends Specification implements PostgresTestPropertyProvider {

    @Inject
    private PostgresOrganizationRepository repository

    void "test uuid generated value"() {
        expect:
        repository.count() == 0
    }

    // sets up UUID extension support for Postgres
    @MockBean
    @Singleton
    static class ExtensionSetup implements BeanCreatedEventListener<DataSource>, Ordered {

        @Override
        DataSource onCreated(BeanCreatedEvent<DataSource> event) {
            def dataSource = event.getBean()
            try {
                new Sql(dataSource).execute('CREATE EXTENSION "uuid-ossp";')
            } catch (e) {
                // ignore, probably already exists
            }
            return dataSource
        }

        @Override
        int getOrder() {
            return HIGHEST_PRECEDENCE
        }
    }
}
