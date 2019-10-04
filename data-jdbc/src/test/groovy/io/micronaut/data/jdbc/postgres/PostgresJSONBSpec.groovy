package io.micronaut.data.jdbc.postgres

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.entities.Sale
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.PostgreSQLContainer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
class PostgresJSONBSpec extends Specification implements TestPropertyProvider {
    @Shared @AutoCleanup PostgreSQLContainer postgres = new PostgreSQLContainer<>("postgres:10")
            .withDatabaseName("test-database")
            .withUsername("test")
            .withPassword("test")


    @Override
    Map<String, String> getProperties() {
        postgres.start()

        return [
            "datasources.default.url":postgres.getJdbcUrl(),
            "datasources.default.username":postgres.getUsername(),
            "datasources.default.password":postgres.getPassword(),
            "datasources.default.schema-generate": SchemaGenerate.CREATE.name(),
            "datasources.default.dialect": Dialect.POSTGRES.name()
        ]
    }

    @Inject PostgresSaleRepository saleRepository

    void "test read and write json"() {
        when:
        Sale sale = new Sale()
        sale.setName("test 1")
        sale.data = [foo:'bar']
        saleRepository.save(sale)
        sale = saleRepository.findById(sale.id).orElse(null)

        then:
        sale.name == 'test 1'
        sale.data == [foo:'bar']

        when:
        saleRepository.updateData(sale.id,[foo:'changed'] )
        sale = saleRepository.findById(sale.id).orElse(null)

        then:
        sale.name == 'test 1'
        sale.data == [foo:'changed']
    }
}
