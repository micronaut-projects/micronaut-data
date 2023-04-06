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
import io.micronaut.data.tck.entities.Discount
import io.micronaut.data.tck.entities.JsonEntity
import io.micronaut.data.tck.entities.Sale
import io.micronaut.data.tck.entities.SaleItem
import io.micronaut.data.tck.entities.SampleData
import io.micronaut.data.tck.repositories.JsonEntityRepository
import io.micronaut.data.tck.repositories.SaleItemRepository
import io.micronaut.data.tck.repositories.SaleRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.Charset
import java.time.Duration
import java.time.LocalDateTime

abstract class AbstractJSONSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    abstract SaleRepository getSaleRepository();
    abstract SaleItemRepository getSaleItemRepository();
    abstract JsonEntityRepository getJsonEntityRepository();

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
            sale.dataList = ['abc1', 'abc2']
            saleRepository.save(sale)
            sale = saleRepository.findById(sale.id).orElse(null)

        then:
            sale.name == 'test 1'
            sale.data == [foo:'bar']
            sale.dataList == ['abc1', 'abc2']

        when:
            saleRepository.updateData(sale.id, [foo:'changed'], ['changed1', 'changed2', 'changed3'])
            sale = saleRepository.findById(sale.id).orElse(null)

        then:
            sale.name == 'test 1'
            sale.data == [foo:'changed']
            sale.dataList == ['changed1', 'changed2', 'changed3']

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

    void "test read DTO from JSON string field"() {
        def objectMapper = new ObjectMapper()
        def discount = new Discount()
        discount.amount = 12
        discount.numberOfDays = 5
        discount.note = "Valid since April 1st"
        given:
        def sale = new Sale(name: "sale")
        def extraData = objectMapper.writeValueAsString(discount)
        sale.setExtraData(extraData)

        when:
        sale = saleRepository.save(sale)
        def optSale = saleRepository.findById(sale.id)
        def optLoadedDiscount = saleRepository.getDiscountById(sale.id)

        then:
        optSale.present
        optLoadedDiscount.present
        def loadedDiscount = optLoadedDiscount.get()
        loadedDiscount.amount == discount.amount
        loadedDiscount.note == discount.note
        loadedDiscount.numberOfDays == discount.numberOfDays

        cleanup:
        cleanup()
    }

    void "test read entity from JSON string field"() {
        def objectMapper = new ObjectMapper()
        given:
        def sale1 = new Sale(name: "sale1")
        sale1.data = ["sale1_field1": "value1"]
        sale1.dataList = ["sale1_data1", "sale2_data2"]
        sale1.quantities = ["sale1_item1": 3, "sale1_item2": 2]
        sale1 = saleRepository.save(sale1)
        saleItemRepository.save(new SaleItem(null, sale1, "sale1 item 1", [count: "1"]))
        saleItemRepository.save(new SaleItem(null, sale1, "sale1 item 2", [count: "2"]))

        sale1 = saleRepository.findById(sale1.id).get()
        def sale1Str = objectMapper.writeValueAsString(sale1)
        sale1.extraData = sale1Str
        saleRepository.update(sale1)

        when:
        def loadedSales = saleRepository.findAllByNameFromJson(sale1.name)

        then:
        loadedSales.size() == 1
        verifySale(sale1, loadedSales[0])

        when:
        def optLoadedSale = saleRepository.findByNameFromJson(sale1.name)

        then:
        optLoadedSale.present
        verifySale(sale1, optLoadedSale.get())

        when:"Test tabular query result"
        optLoadedSale = saleRepository.findByName(sale1.name)
        then:
        optLoadedSale.present
        verifySale(sale1, optLoadedSale.get())

        cleanup:
        cleanup()
    }

    void "test JSON fields retrieval"() {
        def jsonEntity = new JsonEntity()
        jsonEntity.id = 1L
        def sampleData = new SampleData()
        sampleData.etag = UUID.randomUUID().toString()
        sampleData.memo = "memo".getBytes(Charset.defaultCharset())
        sampleData.uuid = UUID.randomUUID()
        sampleData.duration = Duration.ofHours(15)
        sampleData.localDateTime = LocalDateTime.now()
        sampleData.description = "sample description"
        sampleData.grade = 1
        sampleData.rating = 9.75d
        jsonEntity.jsonDefault = sampleData
        jsonEntity.jsonBlob = sampleData
        jsonEntity.jsonString = sampleData
        jsonEntityRepository.save(jsonEntity)
        when:"Entities loaded from appropriate JSON fields"
        def optSampleDataFromJsonDefault = jsonEntityRepository.findJsonDefaultById(jsonEntity.id)
        def optSampleDataFromJsonString = jsonEntityRepository.findJsonStringById(jsonEntity.id)
        def optSampleDataFromJsonBlob = jsonEntityRepository.findJsonBlobById(jsonEntity.id)
        then:"Entities are retrieved and properly deserialized"
        optSampleDataFromJsonDefault.present && optSampleDataFromJsonDefault.get() == sampleData
        optSampleDataFromJsonString.present && optSampleDataFromJsonString.get() == sampleData
        optSampleDataFromJsonBlob.present && optSampleDataFromJsonBlob.get() == sampleData
        when:"JsonEntity loaded from the DB"
        def jsonEntityOpt = jsonEntityRepository.findById(jsonEntity.id)
        then:"all JSON fields are retrieved"
        jsonEntityOpt.present
        def loadedJsonEntity = jsonEntityOpt.get()
        loadedJsonEntity.id == jsonEntity.id
        loadedJsonEntity.jsonString == jsonEntity.jsonString
        loadedJsonEntity.jsonBlob == jsonEntity.jsonBlob
        loadedJsonEntity.jsonDefault == jsonEntity.jsonDefault
        when:"Update JSON field as parameter"
        jsonEntity.jsonString.description = "Updated via param"
        jsonEntity.jsonBlob.grade = 15
        jsonEntityRepository.updateJsonStringById(jsonEntity.id, jsonEntity.jsonString)
        jsonEntityRepository.updateJsonBlobById(jsonEntity.id, jsonEntity.jsonBlob)
        optSampleDataFromJsonString = jsonEntityRepository.findJsonStringById(jsonEntity.id)
        optSampleDataFromJsonBlob = jsonEntityRepository.findJsonBlobById(jsonEntity.id)
        then:"Value should be updated"
        optSampleDataFromJsonString.present
        optSampleDataFromJsonString.get().description == "Updated via param"
        optSampleDataFromJsonBlob.present
        optSampleDataFromJsonBlob.get().grade == 15
    }

    void verifySale(Sale actualSale, Sale expectedSale) {
        verifyAll {
            expectedSale.id == actualSale.id
            expectedSale.name == actualSale.name
            expectedSale.dataList == actualSale.dataList
            expectedSale.quantities == actualSale.quantities
            expectedSale.items.size() == actualSale.items.size()
        }
    }
}
