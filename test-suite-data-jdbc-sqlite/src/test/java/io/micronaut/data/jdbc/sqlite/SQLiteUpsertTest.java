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
package io.micronaut.data.jdbc.sqlite;

import io.micronaut.context.annotation.Property;
import io.micronaut.data.tck.jdbc.entities.upsert.AutoPopulatedUpsertEntity;
import io.micronaut.data.tck.jdbc.entities.upsert.CustomerProfile;
import io.micronaut.data.tck.jdbc.entities.upsert.CustomerProfileUuid;
import io.micronaut.data.tck.jdbc.entities.upsert.ProductReview;
import io.micronaut.data.tck.jdbc.entities.upsert.WarehouseInventory;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@Property(name = "test.sqlite.upsert.tenant.enabled", value = "true")
@SQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite,io.micronaut.data.tck.jdbc.entities.upsert")
class SQLiteUpsertTest {

    @Inject
    SQLiteProductReviewRepository productReviewRepository;

    @Inject
    SQLiteCustomerProfileRepository customerProfileRepository;

    @Inject
    SQLiteCustomerProfileUuidRepository customerProfileUuidRepository;

    @Inject
    SQLiteWarehouseInventoryRepository warehouseInventoryRepository;

    @Inject
    SQLiteAutoPopulatedUpsertRepository autoPopulatedUpsertRepository;

    @AfterEach
    void cleanup() {
        autoPopulatedUpsertRepository.deleteAll();
        autoPopulatedUpsertRepository.deleteByTenantId("another-tenant");
        warehouseInventoryRepository.deleteAll();
        customerProfileRepository.deleteAll();
        customerProfileUuidRepository.deleteAll();
        productReviewRepository.deleteAll();
    }

    @Test
    void upsertPreparesAutoPopulatedPropertiesCascadesUpdatesAndInvokesUpdateLifecycle() {
        ProductReview review = productReviewRepository.save(new ProductReview(100L, "initial title", "initial content"));
        review.setTitle("updated title");
        review.setContent("updated content");
        AutoPopulatedUpsertEntity entity = new AutoPopulatedUpsertEntity(1L, "initial");
        entity.setReview(review);

        autoPopulatedUpsertRepository.upsert(entity);
        AutoPopulatedUpsertEntity persisted = autoPopulatedUpsertRepository.findById(1L).orElseThrow();
        ProductReview cascadedReview = productReviewRepository.findById(100L).orElseThrow();

        assertNotNull(entity.getCreated());
        assertNotNull(entity.getUpdated());
        assertEquals("upsert-tenant", entity.getTenantId());
        assertNotNull(entity.getRequestId());
        assertEquals(0, entity.getPrePersistCalls());
        assertEquals(1, entity.getPreUpdateCalls());
        assertEquals(0, entity.getPostPersistCalls());
        assertEquals(1, entity.getPostUpdateCalls());
        assertEquals(100L, persisted.getReview().getId());
        assertEquals("upsert-tenant", persisted.getTenantId());
        assertEquals(entity.getRequestId(), persisted.getRequestId());
        assertProductReview(new ProductReview(100L, "updated title", "updated content"), cascadedReview);
    }

    @Test
    void upsertAllPreparesAutoPopulatedTimestampsAndInvokesUpdateLifecycle() {
        AutoPopulatedUpsertEntity first = new AutoPopulatedUpsertEntity(1L, "first");
        AutoPopulatedUpsertEntity second = new AutoPopulatedUpsertEntity(2L, "second");

        autoPopulatedUpsertRepository.upsertAll(List.of(first, second));

        for (AutoPopulatedUpsertEntity entity : List.of(first, second)) {
            assertNotNull(entity.getCreated());
            assertNotNull(entity.getUpdated());
            assertEquals("upsert-tenant", entity.getTenantId());
            assertNotNull(entity.getRequestId());
            assertEquals(0, entity.getPrePersistCalls());
            assertEquals(1, entity.getPreUpdateCalls());
            assertEquals(0, entity.getPostPersistCalls());
            assertEquals(1, entity.getPostUpdateCalls());
        }
        for (AutoPopulatedUpsertEntity persisted : autoPopulatedUpsertRepository.findAll()) {
            assertEquals("upsert-tenant", persisted.getTenantId());
            assertNotNull(persisted.getRequestId());
        }
    }

    @Test
    void upsertPreservesPersistedDateCreatedOnUpdate() {
        autoPopulatedUpsertRepository.save(new AutoPopulatedUpsertEntity(1L, "initial"));
        LocalDateTime created = autoPopulatedUpsertRepository.findById(1L).orElseThrow().getCreated();
        UUID requestId = autoPopulatedUpsertRepository.findById(1L).orElseThrow().getRequestId();
        AutoPopulatedUpsertEntity replacement = new AutoPopulatedUpsertEntity(1L, "modified");

        autoPopulatedUpsertRepository.upsert(replacement);
        AutoPopulatedUpsertEntity found = autoPopulatedUpsertRepository.findById(1L).orElseThrow();

        assertNotNull(created);
        assertEquals(created, found.getCreated());
        assertNotNull(found.getUpdated());
        assertEquals("upsert-tenant", found.getTenantId());
        assertNotNull(found.getRequestId());
        assertNotEquals(requestId, found.getRequestId());
    }

    @Test
    void upsertPreservesExistingAutoPopulatedUuidOnUpdate() {
        autoPopulatedUpsertRepository.save(new AutoPopulatedUpsertEntity(1L, "initial"));
        AutoPopulatedUpsertEntity loaded = autoPopulatedUpsertRepository.findById(1L).orElseThrow();
        UUID requestId = loaded.getRequestId();
        loaded.setName("modified");

        autoPopulatedUpsertRepository.upsert(loaded);
        AutoPopulatedUpsertEntity found = autoPopulatedUpsertRepository.findById(1L).orElseThrow();

        assertNotNull(requestId);
        assertEquals(requestId, loaded.getRequestId());
        assertEquals(requestId, found.getRequestId());
        assertEquals("modified", found.getName());
    }

    @Test
    void upsertUpdatesTenantIdWhenSupplied() {
        autoPopulatedUpsertRepository.save(new AutoPopulatedUpsertEntity(1L, "initial"));
        AutoPopulatedUpsertEntity replacement = new AutoPopulatedUpsertEntity(1L, "modified");
        replacement.setTenantId("another-tenant");

        autoPopulatedUpsertRepository.upsert(replacement);

        assertTrue(autoPopulatedUpsertRepository.findById(1L).isEmpty());
        AutoPopulatedUpsertEntity moved = autoPopulatedUpsertRepository
            .findByIdAndTenantId(1L, "another-tenant")
            .orElseThrow();
        assertEquals("another-tenant", moved.getTenantId());
        assertEquals("modified", moved.getName());
    }

    @Test
    void numericUpsertReturnReportsAffectedRowCount() {
        CustomerProfile profile = new CustomerProfile("count@example.com", "initial");

        assertEquals(1, customerProfileRepository.upsertCount(profile));
        assertEquals(1, customerProfileRepository.count());

        profile.setDisplayName("updated");

        assertEquals(1, customerProfileRepository.upsertCount(profile));
        assertEquals(1, customerProfileRepository.count());
        assertEquals("updated", customerProfileRepository.findByEmail("count@example.com").orElseThrow().getDisplayName());
    }

    @Test
    void upsertInsertsAndUpdatesProductReviewByAssignedId() {
        ProductReview review = new ProductReview(1L, "title new", "content new");

        ProductReview inserted = productReviewRepository.upsert(review);

        assertProductReview(review, inserted);
        assertProductReview(review, productReviewRepository.findById(1L).orElseThrow());

        review.setTitle("title modified");
        review.setContent("content modified");
        ProductReview updated = productReviewRepository.upsert(review);

        assertProductReview(review, updated);
        assertProductReview(review, productReviewRepository.findById(1L).orElseThrow());
    }

    @Test
    void upsertAllInsertsAndUpdatesProductReviewsByAssignedId() {
        ProductReview review1 = new ProductReview(1L, "title 1", "content 1");
        ProductReview review2 = new ProductReview(2L, "title 2", "content 2");

        List<ProductReview> inserted = productReviewRepository.upsertAll(List.of(review1, review2));

        assertEquals(2, inserted.size());
        assertProductReview(review1, inserted.get(0));
        assertProductReview(review2, inserted.get(1));
        assertProductReview(review1, productReviewRepository.findById(1L).orElseThrow());
        assertProductReview(review2, productReviewRepository.findById(2L).orElseThrow());

        review1.setTitle("title 1 modified");
        review1.setContent("content 1 modified");
        review2.setTitle("title 2 modified");
        review2.setContent("content 2 modified");
        List<ProductReview> updated = productReviewRepository.upsertAll(List.of(review1, review2));

        assertEquals(2, updated.size());
        assertProductReview(review1, updated.get(0));
        assertProductReview(review2, updated.get(1));
        assertProductReview(review1, productReviewRepository.findById(1L).orElseThrow());
        assertProductReview(review2, productReviewRepository.findById(2L).orElseThrow());
    }

    @Test
    void annotationBasedPutMethodsInsertAndUpdateProductReviews() {
        ProductReview review = new ProductReview(1L, "initial", "content");

        assertProductReview(review, productReviewRepository.put(review));
        review.setTitle("updated");
        assertProductReview(review, productReviewRepository.put(review));

        ProductReview first = new ProductReview(2L, "first", "content");
        ProductReview second = new ProductReview(3L, "second", "content");
        List<ProductReview> inserted = productReviewRepository.putAll(List.of(first, second));

        assertEquals(2, inserted.size());
        assertProductReview(first, inserted.get(0));
        assertProductReview(second, inserted.get(1));

        first.setTitle("first updated");
        second.setTitle("second updated");
        List<ProductReview> updated = productReviewRepository.putAll(List.of(first, second));

        assertProductReview(first, updated.get(0));
        assertProductReview(second, updated.get(1));
    }

    @Test
    void upsertByEmailConflictInsertsAndUpdatesCustomerProfile() {
        CustomerProfile profile = new CustomerProfile("test@example.com", "test");

        CustomerProfile inserted = customerProfileRepository.upsert(profile);

        assertNotNull(inserted.getId());
        assertCustomerProfile(profile, inserted);
        assertCustomerProfile(profile, customerProfileRepository.findById(inserted.getId()).orElseThrow());

        profile.setDisplayName("test modified");
        CustomerProfile updated = customerProfileRepository.upsert(profile);

        assertCustomerProfile(profile, updated);
        assertCustomerProfile(profile, customerProfileRepository.findById(inserted.getId()).orElseThrow());
    }

    @Test
    void upsertAllByEmailConflictInsertsAndUpdatesCustomerProfiles() {
        CustomerProfile profile1 = new CustomerProfile("test1@example.com", "test 1");
        CustomerProfile profile2 = new CustomerProfile("test2@example.com", "test 2");

        List<CustomerProfile> inserted = customerProfileRepository.upsertAll(List.of(profile1, profile2));

        assertEquals(2, inserted.size());
        assertCustomerProfileContent(profile1, inserted.get(0));
        assertCustomerProfileContent(profile2, inserted.get(1));
        CustomerProfile found1 = customerProfileRepository.findByEmail(profile1.getEmail()).orElseThrow();
        CustomerProfile found2 = customerProfileRepository.findByEmail(profile2.getEmail()).orElseThrow();
        assertNotNull(found1.getId());
        assertNotNull(found2.getId());
        assertCustomerProfileContent(profile1, found1);
        assertCustomerProfileContent(profile2, found2);

        profile1.setDisplayName("test 1 modified");
        profile2.setDisplayName("test 2 modified");
        CustomerProfile profile3 = new CustomerProfile("test3@example.com", "test 3");
        CustomerProfile profile4 = new CustomerProfile("test4@example.com", "test 4");
        List<CustomerProfile> updated = customerProfileRepository.upsertAll(List.of(profile1, profile2, profile3, profile4));

        assertEquals(4, updated.size());
        assertCustomerProfileContent(profile1, updated.get(0));
        assertCustomerProfileContent(profile2, updated.get(1));
        assertNotNull(updated.get(2).getId());
        assertNotNull(updated.get(3).getId());
        assertCustomerProfileContent(profile1, customerProfileRepository.findByEmail(profile1.getEmail()).orElseThrow());
        assertCustomerProfileContent(profile2, customerProfileRepository.findByEmail(profile2.getEmail()).orElseThrow());
        assertCustomerProfileContent(profile3, customerProfileRepository.findByEmail(profile3.getEmail()).orElseThrow());
        assertCustomerProfileContent(profile4, customerProfileRepository.findByEmail(profile4.getEmail()).orElseThrow());
    }

    @Test
    void upsertMethodsWithVoidReactiveAndFutureReturnTypesPersistCustomerProfiles() throws Exception {
        CustomerProfile noResult = new CustomerProfile("no-result@example.com", "initial");
        customerProfileRepository.upsertNoResult(noResult);
        noResult.setDisplayName("updated");
        customerProfileRepository.upsertNoResult(noResult);

        CustomerProfile mono = customerProfileRepository
            .upsertMono(new CustomerProfile("mono@example.com", "initial"))
            .block();
        CustomerProfile future = customerProfileRepository
            .upsertFuture(new CustomerProfile("future@example.com", "initial"))
            .get();
        customerProfileRepository
            .upsertMonoNoResult(new CustomerProfile("mono-no-result@example.com", "initial"))
            .block();
        customerProfileRepository
            .upsertFutureNoResult(new CustomerProfile("future-no-result@example.com", "initial"))
            .get();

        List<CustomerProfile> flux = customerProfileRepository
            .upsertAllFlux(List.of(
                new CustomerProfile("flux-1@example.com", "first"),
                new CustomerProfile("flux-2@example.com", "second")
            ))
            .collectList()
            .block();
        List<CustomerProfile> futureBatch = customerProfileRepository
            .upsertAllFuture(List.of(
                new CustomerProfile("future-1@example.com", "first"),
                new CustomerProfile("future-2@example.com", "second")
            ))
            .get();
        customerProfileRepository.upsertAllNoResult(List.of(
            new CustomerProfile("no-result-1@example.com", "first"),
            new CustomerProfile("no-result-2@example.com", "second")
        ));
        customerProfileRepository.upsertAllFluxNoResult(List.of(
            new CustomerProfile("flux-no-result-1@example.com", "first"),
            new CustomerProfile("flux-no-result-2@example.com", "second")
        )).blockLast();
        customerProfileRepository.upsertAllFutureNoResult(List.of(
            new CustomerProfile("future-no-result-1@example.com", "first"),
            new CustomerProfile("future-no-result-2@example.com", "second")
        )).get();

        assertNotNull(mono);
        assertNotNull(mono.getId());
        assertNotNull(future);
        assertNotNull(future.getId());
        assertEquals(2, flux.size());
        assertEquals(2, futureBatch.size());
        assertEquals(15, customerProfileRepository.count());
        assertEquals("updated", customerProfileRepository.findByEmail("no-result@example.com").orElseThrow().getDisplayName());
    }

    @Test
    void upsertWithUuidIdentityPersistsSingleAndBatchProfiles() {
        CustomerProfileUuid profile = new CustomerProfileUuid("uuid@example.com", "initial");
        customerProfileUuidRepository.upsert(profile);
        CustomerProfileUuid inserted = customerProfileUuidRepository.findByEmail(profile.getEmail()).orElseThrow();

        assertNotNull(inserted.getId());
        assertCustomerProfileUuidContent(profile, inserted);

        CustomerProfileUuid replacement = new CustomerProfileUuid("uuid@example.com", "updated");
        customerProfileUuidRepository.upsert(replacement);
        CustomerProfileUuid updated = customerProfileUuidRepository.findByEmail(profile.getEmail()).orElseThrow();

        assertEquals(inserted.getId(), updated.getId());
        assertCustomerProfileUuidContent(replacement, updated);

        CustomerProfileUuid first = new CustomerProfileUuid("uuid-1@example.com", "first");
        CustomerProfileUuid second = new CustomerProfileUuid("uuid-2@example.com", "second");
        customerProfileUuidRepository.upsertAll(List.of(first, second));

        CustomerProfileUuid firstInserted = customerProfileUuidRepository.findByEmail(first.getEmail()).orElseThrow();
        CustomerProfileUuid secondInserted = customerProfileUuidRepository.findByEmail(second.getEmail()).orElseThrow();
        assertNotNull(firstInserted.getId());
        assertNotNull(secondInserted.getId());

        CustomerProfileUuid firstReplacement = new CustomerProfileUuid(first.getEmail(), "first updated");
        CustomerProfileUuid secondReplacement = new CustomerProfileUuid(second.getEmail(), "second updated");
        CustomerProfileUuid third = new CustomerProfileUuid("uuid-3@example.com", "third");
        CustomerProfileUuid fourth = new CustomerProfileUuid("uuid-4@example.com", "fourth");
        customerProfileUuidRepository.upsertAll(List.of(firstReplacement, secondReplacement, third, fourth));

        assertCustomerProfileUuidContent(firstReplacement, customerProfileUuidRepository.findByEmail(first.getEmail()).orElseThrow());
        assertCustomerProfileUuidContent(secondReplacement, customerProfileUuidRepository.findByEmail(second.getEmail()).orElseThrow());
        assertEquals(firstInserted.getId(), customerProfileUuidRepository.findByEmail(first.getEmail()).orElseThrow().getId());
        assertEquals(secondInserted.getId(), customerProfileUuidRepository.findByEmail(second.getEmail()).orElseThrow().getId());
        assertNotNull(customerProfileUuidRepository.findByEmail(third.getEmail()).orElseThrow().getId());
        assertNotNull(customerProfileUuidRepository.findByEmail(fourth.getEmail()).orElseThrow().getId());
    }

    @Test
    void upsertBySkuAndWarehouseConflictInsertsAndUpdatesWarehouseInventory() {
        WarehouseInventory inventory = new WarehouseInventory("SKU-100", "Berlin", 12);

        WarehouseInventory inserted = warehouseInventoryRepository.upsert(inventory);

        assertNotNull(inserted.getId());
        assertWarehouseInventory(inventory, inserted);
        assertWarehouseInventory(inventory, warehouseInventoryRepository.findById(inserted.getId()).orElseThrow());

        inventory.setQuantity(18);
        WarehouseInventory updated = warehouseInventoryRepository.upsert(inventory);

        assertWarehouseInventory(inventory, updated);
        assertWarehouseInventory(inventory, warehouseInventoryRepository.findById(inserted.getId()).orElseThrow());
    }

    @Test
    void upsertAllBySkuAndWarehouseConflictInsertsAndUpdatesWarehouseInventory() {
        WarehouseInventory inventory1 = new WarehouseInventory("SKU-200", "Berlin", 5);
        WarehouseInventory inventory2 = new WarehouseInventory("SKU-200", "Paris", 8);

        List<WarehouseInventory> inserted = warehouseInventoryRepository.upsertAll(List.of(inventory1, inventory2));

        assertEquals(2, inserted.size());
        assertWarehouseInventoryContent(inventory1, inserted.get(0));
        assertWarehouseInventoryContent(inventory2, inserted.get(1));
        WarehouseInventory found1 = warehouseInventoryRepository.findBySkuAndWarehouse(inventory1.getSku(), inventory1.getWarehouse()).orElseThrow();
        WarehouseInventory found2 = warehouseInventoryRepository.findBySkuAndWarehouse(inventory2.getSku(), inventory2.getWarehouse()).orElseThrow();
        assertNotNull(found1.getId());
        assertNotNull(found2.getId());
        assertWarehouseInventoryContent(inventory1, found1);
        assertWarehouseInventoryContent(inventory2, found2);

        inventory1.setQuantity(7);
        inventory2.setQuantity(11);
        List<WarehouseInventory> updated = warehouseInventoryRepository.upsertAll(List.of(inventory1, inventory2));

        assertEquals(2, updated.size());
        assertWarehouseInventoryContent(inventory1, updated.get(0));
        assertWarehouseInventoryContent(inventory2, updated.get(1));
        assertWarehouseInventoryContent(inventory1, warehouseInventoryRepository.findBySkuAndWarehouse(inventory1.getSku(), inventory1.getWarehouse()).orElseThrow());
        assertWarehouseInventoryContent(inventory2, warehouseInventoryRepository.findBySkuAndWarehouse(inventory2.getSku(), inventory2.getWarehouse()).orElseThrow());
    }

    private static void assertProductReview(ProductReview expected, ProductReview actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getTitle(), actual.getTitle());
        assertEquals(expected.getContent(), actual.getContent());
    }

    private static void assertCustomerProfile(CustomerProfile expected, CustomerProfile actual) {
        assertEquals(expected.getId(), actual.getId());
        assertCustomerProfileContent(expected, actual);
    }

    private static void assertCustomerProfileContent(CustomerProfile expected, CustomerProfile actual) {
        assertEquals(expected.getEmail(), actual.getEmail());
        assertEquals(expected.getDisplayName(), actual.getDisplayName());
    }

    private static void assertCustomerProfileUuidContent(CustomerProfileUuid expected, CustomerProfileUuid actual) {
        assertEquals(expected.getEmail(), actual.getEmail());
        assertEquals(expected.getDisplayName(), actual.getDisplayName());
    }

    private static void assertWarehouseInventory(WarehouseInventory expected, WarehouseInventory actual) {
        assertEquals(expected.getId(), actual.getId());
        assertWarehouseInventoryContent(expected, actual);
    }

    private static void assertWarehouseInventoryContent(WarehouseInventory expected, WarehouseInventory actual) {
        assertEquals(expected.getSku(), actual.getSku());
        assertEquals(expected.getWarehouse(), actual.getWarehouse());
        assertEquals(expected.getQuantity(), actual.getQuantity());
    }
}
