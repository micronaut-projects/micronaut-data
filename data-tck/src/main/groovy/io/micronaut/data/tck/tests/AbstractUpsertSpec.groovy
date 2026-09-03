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
import io.micronaut.data.tck.jdbc.entities.upsert.AutoPopulatedUpsertEntity
import io.micronaut.data.tck.jdbc.entities.upsert.CustomerProfile
import io.micronaut.data.tck.jdbc.entities.upsert.CustomerProfileUuid
import io.micronaut.data.tck.jdbc.entities.upsert.ProductReview
import io.micronaut.data.tck.jdbc.entities.upsert.WarehouseInventory
import io.micronaut.data.tck.repositories.upsert.AutoPopulatedUpsertRepository
import io.micronaut.data.tck.repositories.upsert.CustomerProfileRepository
import io.micronaut.data.tck.repositories.upsert.CustomerProfileUuidRepository
import io.micronaut.data.tck.repositories.upsert.ProductReviewRepository
import io.micronaut.data.tck.repositories.upsert.WarehouseInventoryRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

import static org.junit.jupiter.api.Assumptions.assumeTrue

abstract class AbstractUpsertSpec extends Specification {

    abstract ProductReviewRepository getProductReviewRepository()

    abstract CustomerProfileRepository getCustomerProfileRepository()

    abstract CustomerProfileUuidRepository getCustomerProfileUuidRepository()

    abstract WarehouseInventoryRepository getWarehouseInventoryRepository()

    abstract AutoPopulatedUpsertRepository getAutoPopulatedUpsertRepository()

    abstract Map<String, String> getProperties()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties + ['test.upsert.tenant.enabled': 'true'])

    ApplicationContext getApplicationContext() {
        return context
    }

    void cleanup() {
        context.getBean(MockedDateTimeProvider).setValue(null)
        autoPopulatedUpsertRepository.deleteAll()
        autoPopulatedUpsertRepository.deleteByTenantId("another-tenant")
        productReviewRepository.deleteAll()
        customerProfileRepository.deleteAll()
        customerProfileUuidRepository.deleteAll()
        warehouseInventoryRepository.deleteAll()
        cleanupAdditionalRepositories()
    }

    protected void cleanupAdditionalRepositories() {
    }

    void "upsert prepares auto-populated properties, cascades updates, and invokes update lifecycle"() {
        given:
        ProductReview review = productReviewRepository.save(new ProductReview(100L, "initial title", "initial content"))
        review.setTitle("updated title")
        review.setContent("updated content")
        AutoPopulatedUpsertEntity entity = new AutoPopulatedUpsertEntity(1L, "initial")
        entity.setReview(review)

        when:
        autoPopulatedUpsertRepository.upsert(entity)
        AutoPopulatedUpsertEntity persistedEntity = autoPopulatedUpsertRepository.findById(1L).get()
        ProductReview cascadedReview = productReviewRepository.findById(100L).get()

        then:
        entity.created != null
        entity.updated != null
        entity.tenantId == "upsert-tenant"
        entity.requestId != null
        entity.prePersistCalls == 0
        entity.preUpdateCalls == 1
        entity.postPersistCalls == 0
        entity.postUpdateCalls == 1
        persistedEntity.review.id == 100L
        persistedEntity.tenantId == "upsert-tenant"
        persistedEntity.requestId == entity.requestId
        assertProductReview(cascadedReview, new ProductReview(100L, "updated title", "updated content"))
    }

    void "numeric upsert return reports affected row count"() {
        given:
        CustomerProfile profile = new CustomerProfile("count@example.com", "initial")

        when:
        long inserted = customerProfileRepository.upsertCount(profile)

        then:
        inserted == 1
        customerProfileRepository.count() == 1
        CustomerProfile found = customerProfileRepository.findAll().first()
        found.id != null
        found.email == "count@example.com"
        found.displayName == "initial"

        when:
        profile.setDisplayName("updated")
        long updated = customerProfileRepository.upsertCount(profile)

        then:
        updated == 1
        customerProfileRepository.count() == 1
        CustomerProfile updatedProfile = customerProfileRepository.findAll().first()
        updatedProfile.id == found.id
        updatedProfile.email == "count@example.com"
        updatedProfile.displayName == "updated"
    }

    void "upsertAll prepares auto-populated timestamps and invokes update lifecycle"() {
        given:
        AutoPopulatedUpsertEntity first = new AutoPopulatedUpsertEntity(1L, "first")
        AutoPopulatedUpsertEntity second = new AutoPopulatedUpsertEntity(2L, "second")

        when:
        autoPopulatedUpsertRepository.upsertAll([first, second])

        then:
        [first, second].every {
            it.created != null && it.updated != null &&
                it.prePersistCalls == 0 && it.preUpdateCalls == 1 &&
                it.postPersistCalls == 0 && it.postUpdateCalls == 1
        }
    }

    void "upsert preserves persisted date created on update"() {
        given:
        MockedDateTimeProvider dateTimeProvider = context.getBean(MockedDateTimeProvider)
        dateTimeProvider.setValue(OffsetDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC))
        autoPopulatedUpsertRepository.save(new AutoPopulatedUpsertEntity(1L, "initial"))
        LocalDateTime created = autoPopulatedUpsertRepository.findById(1L).get().created
        UUID requestId = autoPopulatedUpsertRepository.findById(1L).get().requestId
        AutoPopulatedUpsertEntity replacement = new AutoPopulatedUpsertEntity(1L, "modified")
        dateTimeProvider.setValue(OffsetDateTime.of(2026, 1, 2, 12, 0, 0, 0, ZoneOffset.UTC))

        when:
        autoPopulatedUpsertRepository.upsert(replacement)
        AutoPopulatedUpsertEntity found = autoPopulatedUpsertRepository.findById(1L).get()

        then:
        created != null
        found.created == created
        replacement.created != created
        replacement.created != found.created
        found.updated != null
        found.tenantId == "upsert-tenant"
        found.requestId != null
        found.requestId != requestId
    }

    void "upsert updates tenant ID when supplied"() {
        given:
        autoPopulatedUpsertRepository.save(new AutoPopulatedUpsertEntity(1L, "initial"))
        AutoPopulatedUpsertEntity replacement = new AutoPopulatedUpsertEntity(1L, "modified")
        replacement.tenantId = "another-tenant"

        when:
        autoPopulatedUpsertRepository.upsert(replacement)

        then:
        autoPopulatedUpsertRepository.findById(1L).empty
        AutoPopulatedUpsertEntity moved = autoPopulatedUpsertRepository
            .findByIdAndTenantId(1L, "another-tenant")
            .get()
        moved.tenantId == "another-tenant"
        moved.name == "modified"
    }

    void "#methodName inserts and updates product review by assigned ID"() {
        given:
        ProductReview pr = new ProductReview(1L, "title new", "content new")

        when:
        ProductReview inserted = upsertMethod(pr)

        then:
        assertProductReview(inserted, new ProductReview(1L, "title new", "content new"))

        when:
        ProductReview found = productReviewRepository.findById(pr.id).get()

        then:
        assertProductReview(found, inserted)

        when:
        pr.setTitle("title modified")
        pr.setContent("content modified")
        ProductReview updated = upsertMethod(pr)

        then:
        assertProductReview(updated, new ProductReview(1L, "title modified", "content modified"))

        when:
        found = productReviewRepository.findById(pr.id).get()

        then:
        assertProductReview(found, updated)

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
        assertProductReview(insertedList.get(0), new ProductReview(1L, "title 1", "content 1"))
        assertProductReview(insertedList.get(1), new ProductReview(2L, "title 2", "content 2"))

        when:
        ProductReview found1 = productReviewRepository.findById(1L).get()
        ProductReview found2 = productReviewRepository.findById(2L).get()

        then:
        assertProductReview(found1, insertedList.get(0))
        assertProductReview(found2, insertedList.get(1))

        when:
        pr1.setTitle("title 1 modified")
        pr1.setContent("content 1 modified")
        pr2.setTitle("title 2 modified")
        pr2.setContent("content 2 modified")
        List<ProductReview> updatedList = upsertMethod([pr1, pr2])

        then:
        updatedList.size() == 2
        assertProductReview(updatedList.get(0), new ProductReview(1L, "title 1 modified", "content 1 modified"))
        assertProductReview(updatedList.get(1), new ProductReview(2L, "title 2 modified", "content 2 modified"))

        when:
        found1 = productReviewRepository.findById(1L).get()
        found2 = productReviewRepository.findById(2L).get()

        then:
        assertProductReview(found1, updatedList.get(0))
        assertProductReview(found2, updatedList.get(1))

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
        inserted.email == "test@example.com"
        inserted.displayName == "test"

        when:
        Long insertedId = inserted.id
        List<CustomerProfile> foundProfiles = customerProfileRepository.findAll()

        then:
        foundProfiles.size() == 1
        assertCustomerProfile(foundProfiles.getFirst(), inserted)

        when:
        cp.setDisplayName("test modified")
        CustomerProfile updated = upsertMethod(cp)

        then:
        updated.id == insertedId
        updated.email == "test@example.com"
        updated.displayName == "test modified"

        when:
        foundProfiles = customerProfileRepository.findAll()

        then:
        foundProfiles.size() == 1
        assertCustomerProfile(foundProfiles.getFirst(), updated)

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
        List<CustomerProfile> foundProfiles = customerProfileRepository.findAll()

        then:
        foundProfiles.size() == 1
        assertCustomerProfile(foundProfiles.getFirst(), cp)

        when:
        cp.setDisplayName("test modified")
        upsertMethod(cp)
        foundProfiles = customerProfileRepository.findAll()

        then:
        foundProfiles.size() == 1
        assertCustomerProfile(foundProfiles.getFirst(), cp)

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
        inserted.get(0).email == "test1@example.com"
        inserted.get(0).displayName == "test 1"
        inserted.get(1).id != null
        inserted.get(1).email == "test2@example.com"
        inserted.get(1).displayName == "test 2"

        when:
        Long firstId = inserted.get(0).id
        Long secondId = inserted.get(1).id
        Map<Long, CustomerProfile> foundById = customerProfileRepository.findAll().collectEntries { [(it.id): it] }

        then:
        foundById.size() == 2
        assertCustomerProfile(foundById[firstId], inserted.get(0))
        assertCustomerProfile(foundById[secondId], inserted.get(1))

        when:
        cp1.setDisplayName("test 1 modified")
        cp2.setDisplayName("test 2 modified")
        CustomerProfile cp3 = new CustomerProfile("test3@example.com", "test 3")
        CustomerProfile cp4 = new CustomerProfile("test4@example.com", "test 4")
        List<CustomerProfile> updated = upsertMethod([cp1, cp2, cp3, cp4])

        then:
        updated.size() == 4
        updated.get(0).id == firstId
        updated.get(0).displayName == "test 1 modified"
        updated.get(1).id == secondId
        updated.get(1).displayName == "test 2 modified"
        updated.get(2).id != null
        updated.get(2).email == "test3@example.com"
        updated.get(2).displayName == "test 3"
        updated.get(3).id != null
        updated.get(3).email == "test4@example.com"
        updated.get(3).displayName == "test 4"

        when:
        foundById = customerProfileRepository.findAll().collectEntries { [(it.id): it] }

        then:
        foundById.size() == 4
        assertCustomerProfile(foundById[firstId], updated.get(0))
        assertCustomerProfile(foundById[secondId], updated.get(1))
        assertCustomerProfile(foundById[updated.get(2).id], updated.get(2))
        assertCustomerProfile(foundById[updated.get(3).id], updated.get(3))

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
        Map<Long, CustomerProfile> foundById = customerProfileRepository.findAll().collectEntries { [(it.id): it] }

        then:
        foundById.size() == 2
        assertCustomerProfile(foundById[cp1.id], cp1)
        assertCustomerProfile(foundById[cp2.id], cp2)

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
        foundById = customerProfileRepository.findAll().collectEntries { [(it.id): it] }

        then:
        foundById.size() == 4
        assertCustomerProfile(foundById[cp1.id], cp1)
        assertCustomerProfile(foundById[cp2.id], cp2)
        assertCustomerProfile(foundById[cp3.id], cp3)
        assertCustomerProfile(foundById[cp4.id], cp4)

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
        inserted.email == "test@example.com"
        inserted.displayName == "test"

        when:
        String insertedId = inserted.id
        List<CustomerProfileUuid> foundProfiles = customerProfileUuidRepository.findAll()

        then:
        foundProfiles.size() == 1
        assertCustomerProfileUuid(foundProfiles.getFirst(), inserted)

        when:
        cp.setDisplayName("test modified")
        CustomerProfileUuid updated = customerProfileUuidRepository.upsert(cp)

        then:
        updated.id == insertedId
        updated.email == "test@example.com"
        updated.displayName == "test modified"

        when:
        foundProfiles = customerProfileUuidRepository.findAll()

        then:
        foundProfiles.size() == 1
        assertCustomerProfileUuid(foundProfiles.getFirst(), updated)
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
        inserted.get(0).email == "test1@example.com"
        inserted.get(0).displayName == "test 1"
        inserted.get(1).id != null
        inserted.get(1).email == "test2@example.com"
        inserted.get(1).displayName == "test 2"

        when:
        String firstId = inserted.get(0).id
        String secondId = inserted.get(1).id
        Map<String, CustomerProfileUuid> foundById = customerProfileUuidRepository.findAll().collectEntries { [(it.id): it] }

        then:
        foundById.size() == 2
        assertCustomerProfileUuid(foundById[firstId], inserted.get(0))
        assertCustomerProfileUuid(foundById[secondId], inserted.get(1))

        when:
        cp1.setDisplayName("test 1 modified")
        cp2.setDisplayName("test 2 modified")
        CustomerProfileUuid cp3 = new CustomerProfileUuid("test3@example.com", "test 3")
        CustomerProfileUuid cp4 = new CustomerProfileUuid("test4@example.com", "test 4")
        List<CustomerProfileUuid> updated = customerProfileUuidRepository.upsertAll([cp1, cp2, cp3, cp4])

        then:
        updated.size() == 4
        updated.get(0).id == firstId
        updated.get(0).displayName == "test 1 modified"
        updated.get(1).id == secondId
        updated.get(1).displayName == "test 2 modified"
        updated.get(2).id != null
        updated.get(2).email == "test3@example.com"
        updated.get(2).displayName == "test 3"
        updated.get(3).id != null
        updated.get(3).email == "test4@example.com"
        updated.get(3).displayName == "test 4"

        when:
        foundById = customerProfileUuidRepository.findAll().collectEntries { [(it.id): it] }

        then:
        foundById.size() == 4
        assertCustomerProfileUuid(foundById[firstId], updated.get(0))
        assertCustomerProfileUuid(foundById[secondId], updated.get(1))
        assertCustomerProfileUuid(foundById[updated.get(2).id], updated.get(2))
        assertCustomerProfileUuid(foundById[updated.get(3).id], updated.get(3))
    }

    void "upsert by sku and warehouse conflict properties"() {
        given:
        WarehouseInventory wh = new WarehouseInventory("SKU-100", "Berlin", 12)

        when:
        WarehouseInventory inserted = warehouseInventoryRepository.upsert(wh)

        then:
        inserted.id != null
        inserted.sku == "SKU-100"
        inserted.warehouse == "Berlin"
        inserted.quantity == 12

        when:
        Long insertedId = inserted.id
        List<WarehouseInventory> foundInventory = warehouseInventoryRepository.findAll()

        then:
        foundInventory.size() == 1
        assertWarehouseInventory(foundInventory.getFirst(), inserted)

        when:
        wh.setQuantity(18)
        WarehouseInventory updated = warehouseInventoryRepository.upsert(wh)

        then:
        updated.id == insertedId
        updated.sku == "SKU-100"
        updated.warehouse == "Berlin"
        updated.quantity == 18

        when:
        foundInventory = warehouseInventoryRepository.findAll()

        then:
        foundInventory.size() == 1
        assertWarehouseInventory(foundInventory.getFirst(), updated)
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
        inserted.get(0).warehouse == "Berlin"
        inserted.get(0).quantity == 5
        inserted.get(1).id != null
        inserted.get(1).warehouse == "Paris"
        inserted.get(1).quantity == 8

        when:
        Long firstId = inserted.get(0).id
        Long secondId = inserted.get(1).id
        Map<Long, WarehouseInventory> foundById = warehouseInventoryRepository.findAll().collectEntries { [(it.id): it] }

        then:
        foundById.size() == 2
        assertWarehouseInventory(foundById[firstId], inserted.get(0))
        assertWarehouseInventory(foundById[secondId], inserted.get(1))

        when:
        wh1.setQuantity(7)
        wh2.setQuantity(11)
        List<WarehouseInventory> updated = warehouseInventoryRepository.upsertAll([wh1, wh2]).toList()

        then:
        updated.size() == 2
        updated.get(0).id == firstId
        updated.get(0).quantity == 7
        updated.get(1).id == secondId
        updated.get(1).quantity == 11

        when:
        foundById = warehouseInventoryRepository.findAll().collectEntries { [(it.id): it] }

        then:
        foundById.size() == 2
        assertWarehouseInventory(foundById[firstId], updated.get(0))
        assertWarehouseInventory(foundById[secondId], updated.get(1))
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
        assert customerProfile1.id == customerProfile2.id
        assert customerProfile1.email == customerProfile2.email
        assert customerProfile1.displayName == customerProfile2.displayName
    }

    private static void assertCustomerProfileUuid(CustomerProfileUuid customerProfile1, CustomerProfileUuid customerProfile2) {
        assert customerProfile1.id == customerProfile2.id
        assert customerProfile1.email == customerProfile2.email
        assert customerProfile1.displayName == customerProfile2.displayName
    }

    private static void assertWarehouseInventory(WarehouseInventory warehouseInventory1, WarehouseInventory warehouseInventory2) {
        assert warehouseInventory1.id == warehouseInventory2.id
        assert warehouseInventory1.sku == warehouseInventory2.sku
        assert warehouseInventory1.warehouse == warehouseInventory2.warehouse
        assert warehouseInventory1.quantity == warehouseInventory2.quantity
    }
}
