package io.micronaut.data.jdbc.oraclexe

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.ApplicationContext
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.tck.entities.Sale
import io.micronaut.data.tck.entities.SaleDTO
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class OracleJSONSpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    OracleSaleRepository saleRepository = applicationContext.getBean(OracleSaleRepository)

    void "test read and write json"() {
        when:
        Sale sale = new Sale()
        sale.setName("test 1")
        sale.data = [foo: 'bar']
        sale.quantities = [foo: 10]
        saleRepository.save(sale)
        sale = saleRepository.findById(sale.id).orElse(null)

        then:
        sale.name == 'test 1'
        sale.data == [foo: 'bar']
        sale.quantities == [foo: 10]

        when:
        sale.data.put('foo2', 'bar2')
        saleRepository.update(sale)
        sale = saleRepository.findById(sale.id).orElse(null)
        then:
        sale.data.containsKey('foo2')

        when:
        sale.data = [foo: 'changed']
        saleRepository.update(sale)
        sale = saleRepository.findById(sale.id).orElse(null)

        then:
        sale.name == 'test 1'
        sale.data == [foo: 'changed']
        sale.quantities == [foo: 10]

        when: "retrieving the data via DTO"
        def dto = saleRepository.getById(sale.id)

        then: "the data is correct"
        dto.name == 'test 1'
        dto.data == [foo: 'changed']

        cleanup:
        saleRepository.deleteAll()
    }

    void "test read write json with string field"() {
        def objectMapper = new ObjectMapper()

        given:
        def sale = new Sale(name: "sale")
        def extraData = "{\"color\":\"blue\"}"
        sale.setExtraData(extraData)

        when:
        saleRepository.save(sale)

        then:
        objectMapper.readTree(saleRepository.findById(sale.id).get().extraData) == objectMapper.readTree(extraData)

        cleanup:
        saleRepository.deleteAll()
    }


    @JdbcRepository(dialect = Dialect.ORACLE)
    static interface OracleSaleRepository extends CrudRepository<Sale, Long> {

        SaleDTO getById(Long id);
    }

}
