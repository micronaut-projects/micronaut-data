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

    void "upsert inserts and updates product review by assigned ID"() {
        given:
        ProductReview pr1 = new ProductReview(1L, "title new", "content new")
        ProductReview pr2 = new ProductReview(1L, "title modified", "content modified")

        when:
        ProductReview inserted = productReviewRepository.upsert(pr1)

        then:
        assertProductReview(pr1, inserted)

        when:
        ProductReview found = productReviewRepository.findById(1L).get()

        then:
        assertProductReview(pr1, found)

        when:
        ProductReview updated = productReviewRepository.upsert(pr2)

        then:
        assertProductReview(pr2, updated)

        when:
        found = productReviewRepository.findById(1L).get()

        then:
        assertProductReview(pr2, found)
    }

    void "upsertAll inserts and updates product reviews by assigned ID"() {
        given:
        ProductReview pr1 = new ProductReview(2L, "title 1", "content 1")
        ProductReview pr2 = new ProductReview(3L, "title 2", "content 2")
        ProductReview pr3 = new ProductReview(2L, "title 1 modified", "content 1 modified")
        ProductReview pr4 = new ProductReview(3L, "title 2 modified", "content 2 modified")

        when:
        List<ProductReview> insertedList = productReviewRepository.upsertAll([pr1, pr2]).toList()

        then:
        assertProductReview(pr1, insertedList.get(0))
        assertProductReview(pr2, insertedList.get(1))

        when:
        ProductReview found1 = productReviewRepository.findById(2L).get()
        ProductReview found2 = productReviewRepository.findById(3L).get()

        then:
        assertProductReview(pr1, found1)
        assertProductReview(pr2, found2)

        when:
        List<ProductReview> updatedList = productReviewRepository.upsertAll([pr3, pr4]).toList()

        then:
        assertProductReview(pr3, updatedList.get(0))
        assertProductReview(pr4, updatedList.get(1))

        when:
        ProductReview found3 = productReviewRepository.findById(2L).get()
        ProductReview found4 = productReviewRepository.findById(3L).get()

        then:
        assertProductReview(pr3, found3)
        assertProductReview(pr4, found4)
    }

    void "upsert annotation inserts and updates product review by assigned ID"() {
        given:
        ProductReview pr1 = new ProductReview(4L, "title new", "content new")
        ProductReview pr2 = new ProductReview(4L, "title modified", "content modified")

        when:
        ProductReview inserted = productReviewRepository.put(pr1)

        then:
        assertProductReview(pr1, inserted)

        when:
        ProductReview found = productReviewRepository.findById(4L).get()

        then:
        assertProductReview(pr1, found)

        when:
        ProductReview updated = productReviewRepository.put(pr2)

        then:
        assertProductReview(pr2, updated)

        when:
        found = productReviewRepository.findById(4L).get()

        then:
        assertProductReview(pr2, found)
    }

    void "upsert annotation inserts and updates product reviews by assigned ID"() {
        given:
        ProductReview pr1 = new ProductReview(2L, "title 1", "content 1")
        ProductReview pr2 = new ProductReview(3L, "title 2", "content 2")
        ProductReview pr3 = new ProductReview(2L, "title 1 modified", "content 1 modified")
        ProductReview pr4 = new ProductReview(3L, "title 2 modified", "content 2 modified")

        when:
        List<ProductReview> insertedList = productReviewRepository.putAll([pr1, pr2]).toList()

        then:
        assertProductReview(pr1, insertedList.get(0))
        assertProductReview(pr2, insertedList.get(1))

        when:
        ProductReview found1 = productReviewRepository.findById(2L).get()
        ProductReview found2 = productReviewRepository.findById(3L).get()

        then:
        assertProductReview(pr1, found1)
        assertProductReview(pr2, found2)

        when:
        List<ProductReview> updatedList = productReviewRepository.putAll([pr3, pr4]).toList()

        then:
        assertProductReview(pr3, updatedList.get(0))
        assertProductReview(pr4, updatedList.get(1))

        when:
        ProductReview found3 = productReviewRepository.findById(2L).get()
        ProductReview found4 = productReviewRepository.findById(3L).get()

        then:
        assertProductReview(pr3, found3)
        assertProductReview(pr4, found4)
    }

    void "upsert annotation inserts and updates customer profile by email conflict property"() {
        given:
        CustomerProfile cp1 = new CustomerProfile("test@example.com", "test")
        CustomerProfile cp2 = new CustomerProfile("test@example.com", "test modified")

        when:
        CustomerProfile inserted = customerProfileRepository.upsert(cp1)

        then:
        assertCustomerProfile(inserted, cp1)

        when:
        List<CustomerProfile> found = customerProfileRepository.findAll().toList()

        then:
        found.size() == 1
        found[0].id() != null
        assertCustomerProfile(found[0], cp1)

        when:
        Long profileId = found[0].id()
        CustomerProfile updated = customerProfileRepository.upsert(cp2)

        then:
        updated.id() == profileId
        assertCustomerProfile(updated, cp2)

        when:
        found = customerProfileRepository.findAll().toList()

        then:
        found.size() == 1
        found[0].id() == profileId
        assertCustomerProfile(found[0], cp2)
    }

    void "upsertAll annotation inserts and updates customer profiles by email conflict property"() {
        given:
        CustomerProfile cp1 = new CustomerProfile("test1@example.com", "test 1")
        CustomerProfile cp2 = new CustomerProfile("test2@example.com", "test 2")
        CustomerProfile cp3 = new CustomerProfile("test1@example.com", "test 1 modified")
        CustomerProfile cp4 = new CustomerProfile("test2@example.com", "test 2 modified")

        when:
        List<CustomerProfile> inserted = customerProfileRepository.upsertAll([cp1, cp2]).toList()

        then:
        inserted.size() == 2
        assertCustomerProfile(inserted.get(0), cp1)
        assertCustomerProfile(inserted.get(1), cp2)

        when:
        List<CustomerProfile> found = customerProfileRepository.findAll().toList()

        then:
        found.size() == 2
        found.get(0).id() != null
        found.get(1).id() != null
        assertCustomerProfile(found.get(0), cp1)
        assertCustomerProfile(found.get(1), cp2)

        when:
        Long id1 = found.get(0).id()
        Long id2 = found.get(1).id()
        List<CustomerProfile> updated = customerProfileRepository.upsertAll([cp3, cp4]).toList()

        then:
        updated.size() == 2
        updated.get(0).id() == id1
        updated.get(1).id() == id2
        assertCustomerProfile(updated.get(0), cp3)
        assertCustomerProfile(updated.get(1), cp4)

        when:
        found = customerProfileRepository.findAll().toList()

        then:
        found.size() == 2
        found.get(0).id() == id1
        found.get(1).id() == id2
        assertCustomerProfile(found.get(0), cp3)
        assertCustomerProfile(found.get(1), cp4)
    }

    void "upsert annotation inserts and updates warehouse inventory by sku and warehouse conflict properties"() {
        given:
        WarehouseInventory wh1 = new WarehouseInventory("SKU-100", "Berlin", 12)
        
        when:
        WarehouseInventory inserted = warehouseInventoryRepository.upsert(wh1)



        List<WarehouseInventory> inventories = warehouseInventoryRepository.findAll().toList()

        then:
        inserted.sku() == "SKU-100"
        inserted.warehouse() == "Berlin"
        inserted.quantity() == 12
        inventories.size() == 1
        inventories[0].id() != null
        inventories[0].sku() == "SKU-100"
        inventories[0].warehouse() == "Berlin"
        inventories[0].quantity() == 12

        when:
        Long inventoryId = inventories[0].id()
        WarehouseInventory updated = warehouseInventoryRepository.upsert(new WarehouseInventory("SKU-100", "Berlin", 18))
        inventories = warehouseInventoryRepository.findAll().toList()

        then:
        updated.sku() == "SKU-100"
        updated.warehouse() == "Berlin"
        updated.quantity() == 18
        inventories.size() == 1
        inventories[0].id() == inventoryId
        inventories[0].sku() == "SKU-100"
        inventories[0].warehouse() == "Berlin"
        inventories[0].quantity() == 18
    }

    void "upsertAll annotation inserts and updates warehouse inventory by sku and warehouse conflict properties"() {
        when:
        List<WarehouseInventory> inserted = warehouseInventoryRepository.upsertAll([
                new WarehouseInventory("SKU-200", "Berlin", 5),
                new WarehouseInventory("SKU-200", "Paris", 8)
        ]).toList()
        List<WarehouseInventory> inventories = warehouseInventoryRepository.findAll().toList()

        then:
        inserted.collect { it.sku() } as Set == ["SKU-200"] as Set
        inserted.collect { it.warehouse() } as Set == ["Berlin", "Paris"] as Set
        inserted.collect { it.quantity() } as Set == [5, 8] as Set
        inventories.size() == 2
        inventories.find { it.sku() == "SKU-200" && it.warehouse() == "Berlin" }.quantity() == 5
        inventories.find { it.sku() == "SKU-200" && it.warehouse() == "Paris" }.quantity() == 8

        when:
        Long berlinId = inventories.find { it.sku() == "SKU-200" && it.warehouse() == "Berlin" }.id()
        Long parisId = inventories.find { it.sku() == "SKU-200" && it.warehouse() == "Paris" }.id()
        List<WarehouseInventory> updated = warehouseInventoryRepository.upsertAll([
                new WarehouseInventory("SKU-200", "Berlin", 7),
                new WarehouseInventory("SKU-200", "Paris", 11)
        ]).toList()
        inventories = warehouseInventoryRepository.findAll().toList()

        then:
        updated.collect { it.sku() } as Set == ["SKU-200"] as Set
        updated.collect { it.warehouse() } as Set == ["Berlin", "Paris"] as Set
        updated.collect { it.quantity() } as Set == [7, 11] as Set
        inventories.size() == 2
        inventories.find { it.sku() == "SKU-200" && it.warehouse() == "Berlin" }.id() == berlinId
        inventories.find { it.sku() == "SKU-200" && it.warehouse() == "Berlin" }.quantity() == 7
        inventories.find { it.sku() == "SKU-200" && it.warehouse() == "Paris" }.id() == parisId
        inventories.find { it.sku() == "SKU-200" && it.warehouse() == "Paris" }.quantity() == 11
    }

    private static void assertProductReview(ProductReview productReview1, ProductReview productReview2) {
        assert productReview1.id() == productReview2.id()
        assert productReview1.title() == productReview2.title()
        assert productReview1.content() == productReview2.content()
    }

    private static void assertCustomerProfile(CustomerProfile customerProfile1, CustomerProfile customerProfile2) {
        assert customerProfile1.email() == customerProfile2.email()
        assert customerProfile1.displayName() == customerProfile2.displayName()
    }
}
