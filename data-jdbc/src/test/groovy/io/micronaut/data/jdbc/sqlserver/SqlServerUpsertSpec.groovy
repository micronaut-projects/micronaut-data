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
package io.micronaut.data.jdbc.sqlserver

import io.micronaut.data.jdbc.sqlserver.upsert.CustomerProfileSequence
import io.micronaut.data.jdbc.sqlserver.upsert.MSCustomerProfileRepository
import io.micronaut.data.jdbc.sqlserver.upsert.MSCustomerProfileSequenceRepository
import io.micronaut.data.jdbc.sqlserver.upsert.MSCustomerProfileUuidRepository
import io.micronaut.data.jdbc.sqlserver.upsert.MSProductReviewRepository
import io.micronaut.data.jdbc.sqlserver.upsert.MSWarehouseInventoryRepository
import io.micronaut.data.tck.repositories.upsert.CustomerProfileRepository
import io.micronaut.data.tck.repositories.upsert.CustomerProfileUuidRepository
import io.micronaut.data.tck.repositories.upsert.ProductReviewRepository
import io.micronaut.data.tck.repositories.upsert.WarehouseInventoryRepository
import io.micronaut.data.tck.tests.AbstractUpsertSpec

class SqlServerUpsertSpec extends AbstractUpsertSpec implements MSSQLTestPropertyProvider {

    @Override
    ProductReviewRepository getProductReviewRepository() {
        return context.getBean(MSProductReviewRepository)
    }

    @Override
    CustomerProfileRepository getCustomerProfileRepository() {
        return context.getBean(MSCustomerProfileRepository)
    }

    @Override
    CustomerProfileUuidRepository getCustomerProfileUuidRepository() {
        return context.getBean(MSCustomerProfileUuidRepository)
    }

    @Override
    WarehouseInventoryRepository getWarehouseInventoryRepository() {
        return context.getBean(MSWarehouseInventoryRepository)
    }

    MSCustomerProfileSequenceRepository getCustomerProfileSequenceRepository() {
        return context.getBean(MSCustomerProfileSequenceRepository)
    }

    @Override
    List<String> packages() {
        return Arrays.asList("io.micronaut.data.tck.jdbc.entities.upsert", "io.micronaut.data.jdbc.sqlserver.upsert")
    }

    void "upsert by email conflict returns entity when sequence id is used"() {
        given:
        CustomerProfileSequence cp = new CustomerProfileSequence("test@example.com", "test")

        when:
        CustomerProfileSequence inserted = customerProfileSequenceRepository.upsert(cp)

        then:
        inserted.id != null
        inserted == cp

        when:
        CustomerProfileSequence found = customerProfileSequenceRepository.findById(cp.id).get()

        then:
        assertCustomerProfileSequence(cp, found)

        when:
        cp.setDisplayName("test modified")
        CustomerProfileSequence updated = customerProfileSequenceRepository.upsert(cp)

        then:
        updated == cp

        when:
        found = customerProfileSequenceRepository.findById(cp.id).get()

        then:
        assertCustomerProfileSequence(cp, found)
    }

    void "upsertAll by email conflict returns entities when sequence id is used"() {
        given:
        CustomerProfileSequence cp1 = new CustomerProfileSequence("test1@example.com", "test 1")
        CustomerProfileSequence cp2 = new CustomerProfileSequence("test2@example.com", "test 2")

        when:
        List<CustomerProfileSequence> inserted = customerProfileSequenceRepository.upsertAll([cp1, cp2])

        then:
        inserted.size() == 2
        inserted.get(0).id != null
        inserted.get(1).id != null
        inserted.get(0) == cp1
        inserted.get(1) == cp2

        when:
        CustomerProfileSequence found1 = customerProfileSequenceRepository.findById(cp1.id).get()
        CustomerProfileSequence found2 = customerProfileSequenceRepository.findById(cp2.id).get()

        then:
        assertCustomerProfileSequence(found1, cp1)
        assertCustomerProfileSequence(found2, cp2)

        when:
        cp1.setDisplayName("test 1 modified")
        cp2.setDisplayName("test 2 modified")
        CustomerProfileSequence cp3 = new CustomerProfileSequence("test3@example.com", "test 3")
        CustomerProfileSequence cp4 = new CustomerProfileSequence("test4@example.com", "test 4")
        List<CustomerProfileSequence> updated = customerProfileSequenceRepository.upsertAll([cp1, cp2, cp3, cp4])

        then:
        updated.size() == 4
        updated.get(0) == cp1
        updated.get(1) == cp2
        updated.get(2).id != null
        updated.get(3).id != null
        updated.get(2) == cp3
        updated.get(3) == cp4

        when:
        found1 = customerProfileSequenceRepository.findById(cp1.id).get()
        found2 = customerProfileSequenceRepository.findById(cp2.id).get()
        CustomerProfileSequence found3 = customerProfileSequenceRepository.findById(cp3.id).get()
        CustomerProfileSequence found4 = customerProfileSequenceRepository.findById(cp4.id).get()

        then:
        assertCustomerProfileSequence(found1, cp1)
        assertCustomerProfileSequence(found2, cp2)
        assertCustomerProfileSequence(found3, cp3)
        assertCustomerProfileSequence(found4, cp4)
    }

    private static void assertCustomerProfileSequence(CustomerProfileSequence customerProfile1, CustomerProfileSequence customerProfile2) {
        assert customerProfile1.email == customerProfile2.email
        assert customerProfile1.displayName == customerProfile2.displayName
    }
}
