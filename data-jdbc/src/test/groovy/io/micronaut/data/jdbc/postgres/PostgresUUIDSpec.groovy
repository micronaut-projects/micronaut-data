package io.micronaut.data.jdbc.postgres

import groovy.sql.Sql
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.core.order.Ordered
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.annotation.MockBean
import spock.lang.Shared

import javax.inject.Inject
import javax.inject.Singleton
import javax.sql.DataSource

@MicronautTest
class PostgresUUIDSpec extends AbstractPostgresSpec {

    @Inject
    @Shared
    DataSource dataSource

    @Inject PostgresUuidRepository repository

    void 'test insert with UUID'() {
        when:
        def test = repository.save(new UuidTest("Fred"))

        def uuid = test.uuid
        then:
        uuid != null

        when:
        test = repository.findById(test.uuid).orElse(null)

        then:
        test.uuid != null
        test.uuid == uuid
        test.name == 'Fred'

    }

    // sets up UUID extension support for Postgres
    @MockBean
    @Singleton
    static class ExtensionSetup implements BeanCreatedEventListener<DataSource>, Ordered {

        @Override
        DataSource onCreated(BeanCreatedEvent<DataSource> event) {
            def dataSource = event.getBean()
            new Sql(dataSource).execute('CREATE EXTENSION "uuid-ossp";')
            return dataSource
        }

        @Override
        int getOrder() {
            return HIGHEST_PRECEDENCE
        }
    }
}
