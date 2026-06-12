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
import io.micronaut.data.tck.jdbc.entities.upsert.ProductReview
import io.micronaut.data.tck.repositories.upsert.ProductReviewRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractUpsertSpec extends Specification {

    abstract ProductReviewRepository getProductReviewRepository()

    abstract Map<String, String> getProperties()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    ApplicationContext getApplicationContext() {
        return context
    }

    void setup() {
        productReviewRepository.deleteAll()
    }

    void cleanup() {
        productReviewRepository.deleteAll()
    }

    void "upsert inserts and updates assigned ID entity"() {
        when:
        ProductReview inserted = productReviewRepository.upsert(new ProductReview(1L, "First", "Initial value"))

        then:
        inserted == new ProductReview(1L, "First", "Initial value")
        productReviewRepository.findById(1L).get() == inserted

        when:
        ProductReview updated = productReviewRepository.upsert(new ProductReview(1L, "Second", "Updated value"))

        then:
        updated == new ProductReview(1L, "Second", "Updated value")
        productReviewRepository.findById(1L).get() == updated
    }

    void "upsertAll inserts and updates assigned ID entities"() {
        when:
        List<ProductReview> inserted = productReviewRepository.upsertAll([
                new ProductReview(2L, "Batch first", "Initial first"),
                new ProductReview(3L, "Batch second", "Initial second")
        ]).toList()

        then:
        inserted as Set == [
                new ProductReview(2L, "Batch first", "Initial first"),
                new ProductReview(3L, "Batch second", "Initial second")
        ] as Set
        productReviewRepository.findById(2L).get() == new ProductReview(2L, "Batch first", "Initial first")
        productReviewRepository.findById(3L).get() == new ProductReview(3L, "Batch second", "Initial second")

        when:
        List<ProductReview> updated = productReviewRepository.upsertAll([
                new ProductReview(2L, "Batch first", "Updated first"),
                new ProductReview(3L, "Batch second", "Updated second")
        ]).toList()

        then:
        updated as Set == [
                new ProductReview(2L, "Batch first", "Updated first"),
                new ProductReview(3L, "Batch second", "Updated second")
        ] as Set
        productReviewRepository.findById(2L).get() == new ProductReview(2L, "Batch first", "Updated first")
        productReviewRepository.findById(3L).get() == new ProductReview(3L, "Batch second", "Updated second")
    }

    void "upsert annotation inserts and updates assigned ID entity"() {
        when:
        ProductReview inserted = productReviewRepository.put(new ProductReview(4L, "Annotated first", "Initial value"))

        then:
        inserted == new ProductReview(4L, "Annotated first", "Initial value")
        productReviewRepository.findById(4L).get() == inserted

        when:
        ProductReview updated = productReviewRepository.put(new ProductReview(4L, "Annotated second", "Updated value"))

        then:
        updated == new ProductReview(4L, "Annotated second", "Updated value")
        productReviewRepository.findById(4L).get() == updated
    }

    void "upsert annotation inserts and updates assigned ID entities"() {
        when:
        List<ProductReview> inserted = productReviewRepository.putAll([
                new ProductReview(5L, "Annotated batch first", "Initial first"),
                new ProductReview(6L, "Annotated batch second", "Initial second")
        ]).toList()

        then:
        inserted as Set == [
                new ProductReview(5L, "Annotated batch first", "Initial first"),
                new ProductReview(6L, "Annotated batch second", "Initial second")
        ] as Set
        productReviewRepository.findById(5L).get() == new ProductReview(5L, "Annotated batch first", "Initial first")
        productReviewRepository.findById(6L).get() == new ProductReview(6L, "Annotated batch second", "Initial second")

        when:
        List<ProductReview> updated = productReviewRepository.putAll([
                new ProductReview(5L, "Annotated batch first", "Updated first"),
                new ProductReview(6L, "Annotated batch second", "Updated second")
        ]).toList()

        then:
        updated as Set == [
                new ProductReview(5L, "Annotated batch first", "Updated first"),
                new ProductReview(6L, "Annotated batch second", "Updated second")
        ] as Set
        productReviewRepository.findById(5L).get() == new ProductReview(5L, "Annotated batch first", "Updated first")
        productReviewRepository.findById(6L).get() == new ProductReview(6L, "Annotated batch second", "Updated second")
    }
}
