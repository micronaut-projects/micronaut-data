package io.micronaut.data.jdbc.sqlserver

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.entities.Sale
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.MSSQLServerContainer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
class SqlServerJSONSpec extends Specification implements TestPropertyProvider {
    @Shared @AutoCleanup MSSQLServerContainer sqlServer = new MSSQLServerContainer<>()

    @Inject MSSaleRepository saleRepository

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



    @Override
    Map<String, String> getProperties() {
        sqlServer.start()
        return ["datasources.default.url":sqlServer.getJdbcUrl(),
                "datasources.default.username":sqlServer.getUsername(),
                "datasources.default.password":sqlServer.getPassword(),
                "datasources.default.schema-generate": SchemaGenerate.CREATE,
                "datasources.default.dialect": Dialect.SQL_SERVER]
    }
}
