package io.micronaut.data.jdbc.mysql


import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.entities.Sale
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.MySQLContainer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
class MySqlJsonSpec extends Specification implements TestPropertyProvider {
    @Shared @AutoCleanup MySQLContainer mysql = new MySQLContainer<>("mysql:8.0.17")

    @Override
    Map<String, String> getProperties() {
        mysql.start()

        return [
                "datasources.default.url":mysql.getJdbcUrl(),
                "datasources.default.username":mysql.getUsername(),
                "datasources.default.password":mysql.getPassword(),
                "datasources.default.schema-generate": SchemaGenerate.CREATE.name(),
                "datasources.default.dialect": Dialect.MYSQL.name()
        ]
    }

    @Inject MySqlSaleRepository saleRepository

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

//      TODO: updates not working due to https://bugs.mysql.com/bug.php?id=93052
//        when:
//        saleRepository.updateData(sale.id,[foo:'changed'] )
//        sale = saleRepository.findById(sale.id).orElse(null)
//
//        then:
//        sale.name == 'test 1'
//        sale.data == [foo:'changed']

    }
}
