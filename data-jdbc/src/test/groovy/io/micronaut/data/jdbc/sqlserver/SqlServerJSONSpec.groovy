package io.micronaut.data.jdbc.sqlserver


import io.micronaut.data.tck.entities.Sale
import io.micronaut.test.annotation.MicronautTest
import javax.inject.Inject

@MicronautTest
class SqlServerJSONSpec extends AbstractSqlServerSpec {
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

}
