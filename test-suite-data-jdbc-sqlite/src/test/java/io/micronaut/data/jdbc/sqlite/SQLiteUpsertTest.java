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

import io.micronaut.data.tck.jdbc.entities.upsert.CustomerProfile;
import io.micronaut.data.tck.jdbc.entities.upsert.ProductReview;
import io.micronaut.data.tck.jdbc.entities.upsert.WarehouseInventory;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
@SQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite,io.micronaut.data.tck.jdbc.entities.upsert")
class SQLiteUpsertTest {

    @Inject
    SQLiteProductReviewRepository productReviewRepository;

    @Inject
    SQLiteCustomerProfileRepository customerProfileRepository;

    @Inject
    SQLiteWarehouseInventoryRepository warehouseInventoryRepository;

    @AfterEach
    void cleanup() {
        warehouseInventoryRepository.deleteAll();
        customerProfileRepository.deleteAll();
        productReviewRepository.deleteAll();
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
        List<CustomerProfile> updated = customerProfileRepository.upsertAll(List.of(profile1, profile2));

        assertEquals(2, updated.size());
        assertCustomerProfileContent(profile1, updated.get(0));
        assertCustomerProfileContent(profile2, updated.get(1));
        assertCustomerProfileContent(profile1, customerProfileRepository.findByEmail(profile1.getEmail()).orElseThrow());
        assertCustomerProfileContent(profile2, customerProfileRepository.findByEmail(profile2.getEmail()).orElseThrow());
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
