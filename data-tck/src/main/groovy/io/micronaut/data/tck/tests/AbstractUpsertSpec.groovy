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
import io.micronaut.data.tck.jdbc.entities.upsert.CustomerProfileUuid
import io.micronaut.data.tck.jdbc.entities.upsert.ProductReview
import io.micronaut.data.tck.jdbc.entities.upsert.WarehouseInventory
import io.micronaut.data.tck.repositories.upsert.CustomerProfileRepository
import io.micronaut.data.tck.repositories.upsert.CustomerProfileUuidRepository
import io.micronaut.data.tck.repositories.upsert.ProductReviewRepository
import io.micronaut.data.tck.repositories.upsert.WarehouseInventoryRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static org.junit.jupiter.api.Assumptions.assumeTrue

abstract class AbstractUpsertSpec extends Specification {

    abstract ProductReviewRepository getProductReviewRepository()

    abstract CustomerProfileRepository getCustomerProfileRepository()

    abstract CustomerProfileUuidRepository getCustomerProfileUuidRepository()

    abstract WarehouseInventoryRepository getWarehouseInventoryRepository()

    abstract Map<String, String> getProperties()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    ApplicationContext getApplicationContext() {
        return context
    }

    void cleanup() {
        productReviewRepository.deleteAll()
        customerProfileRepository.deleteAll()
        customerProfileUuidRepository.deleteAll()
        warehouseInventoryRepository.deleteAll()
        cleanupAdditionalRepositories()
    }

    protected void cleanupAdditionalRepositories() {
    }

    void "#methodName inserts and updates product review by assigned ID"() {
        given:
        ProductReview pr = new ProductReview(1L, "title new", "content new")

        when:
        ProductReview inserted = upsertMethod(pr)

        then:
        inserted == pr

        when:
        ProductReview found = productReviewRepository.findById(pr.id).get()

        then:
        assertProductReview(found, pr)

        when:
        pr.setTitle("title modified")
        pr.setContent("content modified")
        ProductReview updated = upsertMethod(pr)

        then:
        updated == pr

        when:
        found = productReviewRepository.findById(pr.id).get()

        then:
        assertProductReview(found, pr)

        where:
        methodName | upsertMethod
        "upsert"   | { ProductReview review -> productReviewRepository.upsert(review) }
        "put"      | { ProductReview review -> productReviewRepository.put(review) }
    }

    void "#methodName inserts and updates product reviews by assigned ID"() {
        given:
        ProductReview pr1 = new ProductReview(1L, "title 1", "content 1")
        ProductReview pr2 = new ProductReview(2L, "title 2", "content 2")

        when:
        List<ProductReview> insertedList = upsertMethod([pr1, pr2])

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
        List<ProductReview> updatedList = upsertMethod([pr1, pr2])

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

        where:
        methodName  | upsertMethod
        "upsertAll" | { Iterable<ProductReview> reviews -> productReviewRepository.upsertAll(reviews) }
        "putAll"    | { Iterable<ProductReview> reviews -> productReviewRepository.putAll(reviews) }
    }

    void "#methodName by email conflict returns entity"() {
        given:
        CustomerProfile cp = new CustomerProfile("test@example.com", "test")

        when:
        CustomerProfile inserted = upsertMethod(cp)

        then:
        inserted.id != null
        inserted == cp

        when:
        CustomerProfile found = customerProfileRepository.findById(cp.id).get()

        then:
        assertCustomerProfile(found, cp)

        when:
        cp.setDisplayName("test modified")
        CustomerProfile updated = upsertMethod(cp)

        then:
        updated == cp

        when:
        found = customerProfileRepository.findById(cp.id).get()

        then:
        assertCustomerProfile(found, cp)

        where:
        methodName     | upsertMethod
        "upsert"       | { CustomerProfile profile -> customerProfileRepository.upsert(profile) }
        "upsertMono"   | { CustomerProfile profile -> customerProfileRepository.upsertMono(profile).block() }
        "upsertFuture" | { CustomerProfile profile -> customerProfileRepository.upsertFuture(profile).get() }
    }

    void "#methodName by email conflict does not return entity"() {
        given:
        CustomerProfile cp = new CustomerProfile("test@example.com", "test")

        when:
        upsertMethod(cp)

        then:
        cp.id != null

        when:
        CustomerProfile found = customerProfileRepository.findById(cp.id).get()

        then:
        assertCustomerProfile(found, cp)

        when:
        cp.setDisplayName("test modified")
        upsertMethod(cp)
        found = customerProfileRepository.findById(cp.id).get()

        then:
        assertCustomerProfile(found, cp)

        where:
        methodName             | upsertMethod
        "upsertNoResult"       | { CustomerProfile profile -> customerProfileRepository.upsertNoResult(profile) }
        "upsertMonoNoResult"   | { CustomerProfile profile -> customerProfileRepository.upsertMonoNoResult(profile).block() }
        "upsertFutureNoResult" | { CustomerProfile profile -> customerProfileRepository.upsertFutureNoResult(profile).get() }
    }

    void "#methodName by email conflict returns entities"() {
        given:
        CustomerProfile cp1 = new CustomerProfile("test1@example.com", "test 1")
        CustomerProfile cp2 = new CustomerProfile("test2@example.com", "test 2")

        when:
        List<CustomerProfile> inserted = upsertMethod([cp1, cp2])

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
        CustomerProfile cp3 = new CustomerProfile("test3@example.com", "test 3")
        CustomerProfile cp4 = new CustomerProfile("test4@example.com", "test 4")
        List<CustomerProfile> updated = upsertMethod([cp1, cp2, cp3, cp4])

        then:
        updated.size() == 4
        updated.get(0) == cp1
        updated.get(1) == cp2
        updated.get(2).id != null
        updated.get(3).id != null
        updated.get(2) == cp3
        updated.get(3) == cp4

        when:
        found1 = customerProfileRepository.findById(cp1.id).get()
        found2 = customerProfileRepository.findById(cp2.id).get()
        CustomerProfile found3 = customerProfileRepository.findById(cp3.id).get()
        CustomerProfile found4 = customerProfileRepository.findById(cp4.id).get()

        then:
        assertCustomerProfile(found1, cp1)
        assertCustomerProfile(found2, cp2)
        assertCustomerProfile(found3, cp3)
        assertCustomerProfile(found4, cp4)

        where:
        methodName        | upsertMethod
        "upsertAll"       | { Iterable<CustomerProfile> profiles -> customerProfileRepository.upsertAll(profiles) }
        "upsertAllFlux"   | { Iterable<CustomerProfile> profiles -> customerProfileRepository.upsertAllFlux(profiles).collectList().block() }
        "upsertAllFuture" | { Iterable<CustomerProfile> profiles -> customerProfileRepository.upsertAllFuture(profiles).get() }
    }

    void "#methodName by email conflict does not return entities"() {
        given:
        CustomerProfile cp1 = new CustomerProfile("test1@example.com", "test 1")
        CustomerProfile cp2 = new CustomerProfile("test2@example.com", "test 2")

        when:
        upsertMethod([cp1, cp2])

        then:
        cp1.id != null
        cp2.id != null

        when:
        CustomerProfile found1 = customerProfileRepository.findById(cp1.id).get()
        CustomerProfile found2 = customerProfileRepository.findById(cp2.id).get()

        then:
        assertCustomerProfile(found1, cp1)
        assertCustomerProfile(found2, cp2)

        when:
        cp1.setDisplayName("test 1 modified")
        cp2.setDisplayName("test 2 modified")
        CustomerProfile cp3 = new CustomerProfile("test3@example.com", "test 3")
        CustomerProfile cp4 = new CustomerProfile("test4@example.com", "test 4")
        upsertMethod([cp1, cp2, cp3, cp4])

        then:
        cp3.id != null
        cp4.id != null

        when:
        found1 = customerProfileRepository.findById(cp1.id).get()
        found2 = customerProfileRepository.findById(cp2.id).get()
        CustomerProfile found3 = customerProfileRepository.findById(cp3.id).get()
        CustomerProfile found4 = customerProfileRepository.findById(cp4.id).get()

        then:
        assertCustomerProfile(found1, cp1)
        assertCustomerProfile(found2, cp2)
        assertCustomerProfile(found3, cp3)
        assertCustomerProfile(found4, cp4)

        where:
        methodName                | upsertMethod
        "upsertAllNoResult"       | { Iterable<CustomerProfile> profiles -> customerProfileRepository.upsertAllNoResult(profiles) }
        "upsertAllFluxNoResult"   | { Iterable<CustomerProfile> profiles -> customerProfileRepository.upsertAllFluxNoResult(profiles).collectList().block() }
        "upsertAllFutureNoResult" | { Iterable<CustomerProfile> profiles -> customerProfileRepository.upsertAllFutureNoResult(profiles).get() }
    }

    void "upsert by email conflict returns entity when uuid is used"() {
        assumeTrue(supportsGeneratedUuidReturning())

        given:
        CustomerProfileUuid cp = new CustomerProfileUuid("test@example.com", "test")

        when:
        CustomerProfileUuid inserted = customerProfileUuidRepository.upsert(cp)

        then:
        inserted.id != null
        inserted == cp

        when:
        CustomerProfileUuid found = customerProfileUuidRepository.findById(cp.id).get()

        then:
        assertCustomerProfileUuid(cp, found)

        when:
        cp.setDisplayName("test modified")
        CustomerProfileUuid updated = customerProfileUuidRepository.upsert(cp)

        then:
        updated == cp

        when:
        found = customerProfileUuidRepository.findById(cp.id).get()

        then:
        assertCustomerProfileUuid(cp, found)
    }

    void "upsertAll by email conflict returns entities when uuid is used"() {
        assumeTrue(supportsGeneratedUuidReturning())

        given:
        CustomerProfileUuid cp1 = new CustomerProfileUuid("test1@example.com", "test 1")
        CustomerProfileUuid cp2 = new CustomerProfileUuid("test2@example.com", "test 2")

        when:
        List<CustomerProfileUuid> inserted = customerProfileUuidRepository.upsertAll([cp1, cp2])

        then:
        inserted.size() == 2
        inserted.get(0).id != null
        inserted.get(1).id != null
        inserted.get(0) == cp1
        inserted.get(1) == cp2

        when:
        CustomerProfileUuid found1 = customerProfileUuidRepository.findById(cp1.id).get()
        CustomerProfileUuid found2 = customerProfileUuidRepository.findById(cp2.id).get()

        then:
        assertCustomerProfileUuid(found1, cp1)
        assertCustomerProfileUuid(found2, cp2)

        when:
        cp1.setDisplayName("test 1 modified")
        cp2.setDisplayName("test 2 modified")
        CustomerProfileUuid cp3 = new CustomerProfileUuid("test3@example.com", "test 3")
        CustomerProfileUuid cp4 = new CustomerProfileUuid("test4@example.com", "test 4")
        List<CustomerProfileUuid> updated = customerProfileUuidRepository.upsertAll([cp1, cp2, cp3, cp4])

        then:
        updated.size() == 4
        updated.get(0) == cp1
        updated.get(1) == cp2
        updated.get(2).id != null
        updated.get(3).id != null
        updated.get(2) == cp3
        updated.get(3) == cp4

        when:
        found1 = customerProfileUuidRepository.findById(cp1.id).get()
        found2 = customerProfileUuidRepository.findById(cp2.id).get()
        CustomerProfileUuid found3 = customerProfileUuidRepository.findById(cp3.id).get()
        CustomerProfileUuid found4 = customerProfileUuidRepository.findById(cp4.id).get()

        then:
        assertCustomerProfileUuid(found1, cp1)
        assertCustomerProfileUuid(found2, cp2)
        assertCustomerProfileUuid(found3, cp3)
        assertCustomerProfileUuid(found4, cp4)
    }

    void "upsert by sku and warehouse conflict properties"() {
        given:
        WarehouseInventory wh = new WarehouseInventory("SKU-100", "Berlin", 12)

        when:
        WarehouseInventory inserted = warehouseInventoryRepository.upsert(wh)

        then:
        inserted.id != null
        inserted == wh

        when:
        WarehouseInventory found = warehouseInventoryRepository.findById(wh.id).get()

        then:
        assertWarehouseInventory(found, wh)

        when:
        wh.setQuantity(18)
        WarehouseInventory updated = warehouseInventoryRepository.upsert(wh)

        then:
        updated == wh

        when:
        found = warehouseInventoryRepository.findById(wh.id).get()

        then:
        assertWarehouseInventory(found, wh)
    }

    void "upsertAll by sku and warehouse conflict properties"() {
        given:
        WarehouseInventory wh1 = new WarehouseInventory("SKU-200", "Berlin", 5)
        WarehouseInventory wh2 = new WarehouseInventory("SKU-200", "Paris", 8)

        when:
        List<WarehouseInventory> inserted = warehouseInventoryRepository.upsertAll([wh1, wh2]).toList()

        then:
        inserted.size() == 2
        inserted.get(0).id != null
        inserted.get(1).id != null
        inserted.get(0) == wh1
        inserted.get(1) == wh2

        when:
        WarehouseInventory found1 = warehouseInventoryRepository.findById(wh1.id).get()
        WarehouseInventory found2 = warehouseInventoryRepository.findById(wh2.id).get()

        then:
        assertWarehouseInventory(found1, wh1)
        assertWarehouseInventory(found2, wh2)

        when:
        wh1.setQuantity(7)
        wh2.setQuantity(11)
        List<WarehouseInventory> updated = warehouseInventoryRepository.upsertAll([wh1, wh2]).toList()

        then:
        updated.size() == 2
        updated.get(0) == wh1
        updated.get(1) == wh2

        when:
        found1 = warehouseInventoryRepository.findById(wh1.id).get()
        found2 = warehouseInventoryRepository.findById(wh2.id).get()

        then:
        assertWarehouseInventory(found1, wh1)
        assertWarehouseInventory(found2, wh2)
    }

    protected boolean supportsGeneratedUuidReturning() {
        return true
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

    private static void assertCustomerProfileUuid(CustomerProfileUuid customerProfile1, CustomerProfileUuid customerProfile2) {
        assert customerProfile1.email == customerProfile2.email
        assert customerProfile1.displayName == customerProfile2.displayName
    }

    private static void assertWarehouseInventory(WarehouseInventory warehouseInventory1, WarehouseInventory warehouseInventory2) {
        assert warehouseInventory1.sku == warehouseInventory2.sku
        assert warehouseInventory1.warehouse == warehouseInventory2.warehouse
        assert warehouseInventory1.quantity == warehouseInventory2.quantity
    }
}
