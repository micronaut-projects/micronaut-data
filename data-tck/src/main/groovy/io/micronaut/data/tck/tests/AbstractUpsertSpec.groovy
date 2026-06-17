/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.context.ApplicationContext
import io.micronaut.data.tck.jdbc.entities.upsert.CustomerProfile
import io.micronaut.data.tck.jdbc.entities.upsert.ProductReview
import io.micronaut.data.tck.jdbc.entities.upsert.WarehouseInventory
import io.micronaut.data.tck.repositories.upsert.CustomerProfileRepository
import io.micronaut.data.tck.repositories.upsert.ProductReviewRepository
import io.micronaut.data.tck.repositories.upsert.WarehouseInventoryRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractUpsertSpec extends Specification {

    abstract ProductReviewRepository getProductReviewRepository()

    abstract CustomerProfileRepository getCustomerProfileRepository()

    abstract WarehouseInventoryRepository getWarehouseInventoryRepository()

    abstract Map<String, String> getProperties()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    ApplicationContext getApplicationContext() {
        return context
    }

    void setup() {
        warehouseInventoryRepository.deleteAll()
        customerProfileRepository.deleteAll()
        productReviewRepository.deleteAll()
    }

    void cleanup() {
        warehouseInventoryRepository.deleteAll()
        customerProfileRepository.deleteAll()
        productReviewRepository.deleteAll()
    }

    void "upsert method inserts and updates product review by assigned ID"() {
        given:
        ProductReview pr = new ProductReview(1L, "title new", "content new")

        when:
        ProductReview inserted = productReviewRepository.upsert(pr)

        then:
        inserted == pr

        when:
        ProductReview found = productReviewRepository.findById(pr.id).get()

        then:
        assertProductReview(found, pr)

        when:
        pr.setTitle("title modified")
        pr.setContent("content modified")
        ProductReview updated = productReviewRepository.upsert(pr)

        then:
        updated == pr

        when:
        found = productReviewRepository.findById(pr.id).get()

        then:
        assertProductReview(found, pr)
    }

    void "upsertAll method inserts and updates product reviews by assigned ID"() {
        given:
        ProductReview pr1 = new ProductReview(1L, "title 1", "content 1")
        ProductReview pr2 = new ProductReview(1L, "title 2", "content 2")

        when:
        List<ProductReview> insertedList = productReviewRepository.upsertAll([pr1, pr2]).toList()

        then:
        insertedList.size() == 2
        insertedList.get(0) == pr1
        insertedList.get(1) == pr2

        when:
        ProductReview found1 = productReviewRepository.findById(1L).get()
        ProductReview found2 = productReviewRepository.findById(2L).get()

        then:
        assertProductReview(found1, pr1)
        assertProductReview(found2, pr2)

        when:
        pr1.setTitle("title 1 modified")
        pr1.setContent("content 1 modified")
        pr2.setTitle("title 2 modified")
        pr2.setContent("content 2 modified")
        List<ProductReview> updatedList = productReviewRepository.upsertAll([pr1, pr2]).toList()

        then:
        updatedList.size() == 2
        updatedList.get(0) == pr1
        updatedList.get(1) == pr2

        when:
        found1 = productReviewRepository.findById(1L).get()
        found2 = productReviewRepository.findById(2L).get()

        then:
        assertProductReview(found1, pr1)
        assertProductReview(found2, pr2)
    }

    void "upsert annotation inserts and updates product review by assigned ID"() {
        given:
        ProductReview pr = new ProductReview(1L, "title new", "content new")

        when:
        ProductReview inserted = productReviewRepository.put(pr)

        then:
        inserted == pr

        when:
        ProductReview found = productReviewRepository.findById(pr.id).get()

        then:
        assertProductReview(found, pr)

        when:
        pr.setTitle("title modified")
        pr.setContent("content modified")
        ProductReview updated = productReviewRepository.put(pr)

        then:
        updated == pr

        when:
        found = productReviewRepository.findById(pr.id).get()

        then:
        assertProductReview(found, pr)
    }

    void "upsert annotation inserts and updates product reviews by assigned ID"() {
        given:
        ProductReview pr1 = new ProductReview(1L, "title 1", "content 1")
        ProductReview pr2 = new ProductReview(1L, "title 2", "content 2")

        when:
        List<ProductReview> insertedList = productReviewRepository.putAll([pr1, pr2]).toList()

        then:
        insertedList.size() == 2
        insertedList.get(0) == pr1
        insertedList.get(1) == pr2

        when:
        ProductReview found1 = productReviewRepository.findById(1L).get()
        ProductReview found2 = productReviewRepository.findById(2L).get()

        then:
        assertProductReview(found1, pr1)
        assertProductReview(found2, pr2)

        when:
        pr1.setTitle("title 1 modified")
        pr1.setContent("content 1 modified")
        pr2.setTitle("title 2 modified")
        pr2.setContent("content 2 modified")
        List<ProductReview> updatedList = productReviewRepository.putAll([pr1, pr2]).toList()

        then:
        updatedList.size() == 2
        updatedList.get(0) == pr1
        updatedList.get(1) == pr2

        when:
        found1 = productReviewRepository.findById(1L).get()
        found2 = productReviewRepository.findById(2L).get()

        then:
        assertProductReview(found1, pr1)
        assertProductReview(found2, pr2)
    }

    void "upsert annotation inserts and updates customer profile by email conflict property"() {
        given:
        CustomerProfile cp = new CustomerProfile("test@example.com", "test")

        when:
        CustomerProfile inserted = customerProfileRepository.upsert(cp)

        then:
        inserted.id != null
        inserted == cp

        when:
        CustomerProfile found = customerProfileRepository.findById(cp.id).get()

        then:
        assertCustomerProfile(cp, found)

        when:
        cp.setDisplayName("test modified")
        CustomerProfile updated = customerProfileRepository.upsert(cp)

        then:
        updated == cp

        when:
        found = customerProfileRepository.findById(cp.id).get()

        then:
        assertCustomerProfile(cp, found)
    }

    void "upsertAll annotation inserts and updates customer profiles by email conflict property"() {
        given:
        CustomerProfile cp1 = new CustomerProfile("test1@example.com", "test 1")
        CustomerProfile cp2 = new CustomerProfile("test2@example.com", "test 2")

        when:
        List<CustomerProfile> inserted = customerProfileRepository.upsertAll([cp1, cp2]).toList()

        then:
        inserted.size() == 2
        inserted.get(0).id != null
        inserted.get(1).id != null
        inserted.get(0) == cp1
        inserted.get(1) == cp2

        when:
        CustomerProfile found1 = customerProfileRepository.findById(cp1.id).get()
        CustomerProfile found2 = customerProfileRepository.findById(cp2.id).get()

        then:
        assertCustomerProfile(found1, cp1)
        assertCustomerProfile(found2, cp2)

        when:
        cp1.setDisplayName("test 1 modified")
        cp2.setDisplayName("test 2 modified")
        List<CustomerProfile> updated = customerProfileRepository.upsertAll([cp1, cp2]).toList()

        then:
        updated.size() == 2
        updated.get(0) == cp1
        updated.get(1) == cp2

        when:
        found1 = customerProfileRepository.findById(cp1.id).get()
        found2 = customerProfileRepository.findById(cp2.id).get()

        then:
        assertCustomerProfile(found1, cp1)
        assertCustomerProfile(found2, cp2)
    }

    void "upsert annotation inserts and updates warehouse inventory by sku and warehouse conflict properties"() {
        given:
        WarehouseInventory wh1 = new WarehouseInventory("SKU-100", "Berlin", 12)

        when:
        WarehouseInventory inserted = warehouseInventoryRepository.upsert(wh1)
        List<WarehouseInventory> inventories = warehouseInventoryRepository.findAll().toList()

        then:
        if (inserted.id != null) {
            assert inserted.id == inventories[0].id
        }
        inserted.sku == "SKU-100"
        inserted.warehouse == "Berlin"
        inserted.quantity == 12
        inventories.size() == 1
        inventories[0].id != null
        inventories[0].sku == "SKU-100"
        inventories[0].warehouse == "Berlin"
        inventories[0].quantity == 12

        when:
        Long inventoryId = inventories[0].id
        WarehouseInventory updated = warehouseInventoryRepository.upsert(new WarehouseInventory("SKU-100", "Berlin", 18))
        inventories = warehouseInventoryRepository.findAll().toList()

        then:
        if (updated.id != null) {
            assert updated.id == inventoryId
        }
        updated.sku == "SKU-100"
        updated.warehouse == "Berlin"
        updated.quantity == 18
        inventories.size() == 1
        inventories[0].id == inventoryId
        inventories[0].sku == "SKU-100"
        inventories[0].warehouse == "Berlin"
        inventories[0].quantity == 18
    }

    void "upsertAll annotation inserts and updates warehouse inventory by sku and warehouse conflict properties"() {
        when:
        List<WarehouseInventory> inserted = warehouseInventoryRepository.upsertAll([
                new WarehouseInventory("SKU-200", "Berlin", 5),
                new WarehouseInventory("SKU-200", "Paris", 8)
        ]).toList()
        List<WarehouseInventory> inventories = warehouseInventoryRepository.findAll().toList()

        then:
        inserted.collect { it.sku } as Set == ["SKU-200"] as Set
        inserted.collect { it.warehouse } as Set == ["Berlin", "Paris"] as Set
        inserted.collect { it.quantity } as Set == [5, 8] as Set
        inventories.size() == 2
        inventories.find { it.sku == "SKU-200" && it.warehouse == "Berlin" }.id != null
        inventories.find { it.sku == "SKU-200" && it.warehouse == "Berlin" }.quantity == 5
        inventories.find { it.sku == "SKU-200" && it.warehouse == "Paris" }.id != null
        inventories.find { it.sku == "SKU-200" && it.warehouse == "Paris" }.quantity == 8
        assertReturnedWarehouseInventoryIdsIfPresent(inserted, inventories)

        when:
        Long berlinId = inventories.find { it.sku == "SKU-200" && it.warehouse == "Berlin" }.id
        Long parisId = inventories.find { it.sku == "SKU-200" && it.warehouse == "Paris" }.id
        List<WarehouseInventory> updated = warehouseInventoryRepository.upsertAll([
                new WarehouseInventory("SKU-200", "Berlin", 7),
                new WarehouseInventory("SKU-200", "Paris", 11)
        ]).toList()
        inventories = warehouseInventoryRepository.findAll().toList()

        then:
        assertReturnedWarehouseInventoryIdsIfPresent(updated, inventories)
        updated.collect { it.sku } as Set == ["SKU-200"] as Set
        updated.collect { it.warehouse } as Set == ["Berlin", "Paris"] as Set
        updated.collect { it.quantity } as Set == [7, 11] as Set
        inventories.size() == 2
        inventories.find { it.sku == "SKU-200" && it.warehouse == "Berlin" }.id == berlinId
        inventories.find { it.sku == "SKU-200" && it.warehouse == "Berlin" }.quantity == 7
        inventories.find { it.sku == "SKU-200" && it.warehouse == "Paris" }.id == parisId
        inventories.find { it.sku == "SKU-200" && it.warehouse == "Paris" }.quantity == 11
    }

    private static void assertProductReview(ProductReview productReview1, ProductReview productReview2) {
        assert productReview1.id == productReview2.id
        assert productReview1.title == productReview2.title
        assert productReview1.content == productReview2.content
    }

    private static void assertCustomerProfile(CustomerProfile customerProfile1, CustomerProfile customerProfile2) {
        assert customerProfile1.email == customerProfile2.email
        assert customerProfile1.displayName == customerProfile2.displayName
    }

    private static void assertReturnedWarehouseInventoryIdsIfPresent(List<WarehouseInventory> returned, List<WarehouseInventory> persisted) {
        returned.each { WarehouseInventory warehouseInventory ->
            if (warehouseInventory.id != null) {
                WarehouseInventory persistedWarehouseInventory = persisted.find {
                    it.sku == warehouseInventory.sku && it.warehouse == warehouseInventory.warehouse
                }
                assert persistedWarehouseInventory != null
                assert warehouseInventory.id == persistedWarehouseInventory.id
            }
        }
    }
}
