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
package io.micronaut.data.jdbc.mariadb

import io.micronaut.data.jdbc.mysql.upsert.MySqlAutoPopulatedUpsertRepository
import io.micronaut.data.jdbc.mysql.upsert.MySqlCustomerProfileRepository
import io.micronaut.data.jdbc.mysql.upsert.MySqlCustomerProfileUuidRepository
import io.micronaut.data.jdbc.mysql.upsert.MySqlProductReviewRepository
import io.micronaut.data.jdbc.mysql.upsert.MySqlWarehouseInventoryRepository
import io.micronaut.data.tck.repositories.upsert.AutoPopulatedUpsertRepository
import io.micronaut.data.tck.repositories.upsert.CustomerProfileRepository
import io.micronaut.data.tck.repositories.upsert.CustomerProfileUuidRepository
import io.micronaut.data.tck.repositories.upsert.ProductReviewRepository
import io.micronaut.data.jdbc.mysql.upsert.MySqlClinicRepository
import io.micronaut.data.jdbc.mysql.upsert.MySqlClinicServiceOfferingRepository
import io.micronaut.data.jdbc.mysql.upsert.MySqlCompositeClinicRepository
import io.micronaut.data.jdbc.mysql.upsert.MySqlCompositeClinicOfferingRepository
import io.micronaut.data.jdbc.mysql.upsert.MySqlEmbeddedConflictEntityRepository
import io.micronaut.data.tck.repositories.upsert.ClinicRepository
import io.micronaut.data.tck.repositories.upsert.ClinicServiceOfferingRepository
import io.micronaut.data.tck.repositories.upsert.CompositeClinicRepository
import io.micronaut.data.tck.repositories.upsert.CompositeClinicOfferingRepository
import io.micronaut.data.tck.repositories.upsert.EmbeddedConflictEntityRepository
import io.micronaut.data.tck.repositories.upsert.WarehouseInventoryRepository
import io.micronaut.data.tck.tests.AbstractUpsertSpec

class MariaUpsertSpec extends AbstractUpsertSpec implements MariaTestPropertyProvider {

    @Override
    ProductReviewRepository getProductReviewRepository() {
        return context.getBean(MySqlProductReviewRepository)
    }

    @Override
    ClinicRepository getClinicRepository() {
        return context.getBean(MySqlClinicRepository)
    }

    @Override
    ClinicServiceOfferingRepository getClinicServiceOfferingRepository() {
        return context.getBean(MySqlClinicServiceOfferingRepository)
    }

    @Override
    CompositeClinicRepository getCompositeClinicRepository() {
        return context.getBean(MySqlCompositeClinicRepository)
    }

    @Override
    CompositeClinicOfferingRepository getCompositeClinicOfferingRepository() {
        return context.getBean(MySqlCompositeClinicOfferingRepository)
    }

    @Override
    EmbeddedConflictEntityRepository getEmbeddedConflictEntityRepository() {
        return context.getBean(MySqlEmbeddedConflictEntityRepository)
    }

    @Override
    CustomerProfileRepository getCustomerProfileRepository() {
        return context.getBean(MySqlCustomerProfileRepository)
    }

    @Override
    CustomerProfileUuidRepository getCustomerProfileUuidRepository() {
        return context.getBean(MySqlCustomerProfileUuidRepository)
    }

    @Override
    WarehouseInventoryRepository getWarehouseInventoryRepository() {
        return context.getBean(MySqlWarehouseInventoryRepository)
    }

    @Override
    AutoPopulatedUpsertRepository getAutoPopulatedUpsertRepository() {
        return context.getBean(MySqlAutoPopulatedUpsertRepository)
    }

    @Override
    List<String> packages() {
        return Arrays.asList("io.micronaut.data.tck.jdbc.entities.upsert")
    }

    @Override
    protected boolean supportsGeneratedUuidReturning() {
        return false
    }
}
