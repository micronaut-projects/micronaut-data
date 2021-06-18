/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.tck.tests

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.ApplicationContext
import io.micronaut.data.tck.entities.Sale
import io.micronaut.data.tck.entities.SaleItem
import io.micronaut.data.tck.repositories.SaleItemRepository
import io.micronaut.data.tck.repositories.SaleRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.TimeUnit

@Timeout(value = 20, unit = TimeUnit.SECONDS)
abstract class AbstractJSONSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    abstract SaleRepository getSaleRepository();
    abstract SaleItemRepository getSaleItemRepository();

    def cleanup() {
        saleRepository.deleteAll()
        saleItemRepository.deleteAll()
    }

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
        cleanup()
    }

    void "test read and write with updated"() {
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

        cleanup:
            cleanup()
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
        cleanup()
    }

    void "test read and write json with child rows"() {
        given:
        def sale = saleRepository.save(new Sale(name: "test 1"))
        def items = saleItemRepository.saveAll([
            new SaleItem(null, sale, "item 1", [count: "1"]),
            new SaleItem(null, sale, "item 2", [count: "2"]),
            new SaleItem(null, sale, "item 3", [count: "3"]),
        ])

        when:
        def saleById = saleRepository.findById(sale.id).orElse(null)

        then:
        saleById.name == 'test 1'
        saleById.items == items.toSet()

        cleanup:
        cleanup()
    }

    void "test read and write json with constructor args"() {
        given:
        def sale = saleRepository.save(new Sale(name: "test 1"))
        def item = saleItemRepository.save(new SaleItem(null, sale, "item 1", [count: "1"]))

        when:
        def itemById = saleItemRepository.findById(item.id).orElse(null)

        then:
        itemById.name == 'item 1'
        itemById.data == [count: "1"]
        itemById.sale.id == sale.id

        cleanup:
        cleanup()
    }
}
