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
package io.micronaut.data.r2dbc.oraclexe

import io.micronaut.data.r2dbc.oraclexe.upsert.OracleXECustomerProfileRepository
import io.micronaut.data.r2dbc.oraclexe.upsert.OracleXECustomerProfileUuidRepository
import io.micronaut.data.r2dbc.oraclexe.upsert.OracleXEProductReviewRepository
import io.micronaut.data.r2dbc.oraclexe.upsert.OracleXEWarehouseInventoryRepository
import io.micronaut.data.tck.repositories.upsert.CustomerProfileRepository
import io.micronaut.data.tck.repositories.upsert.CustomerProfileUuidRepository
import io.micronaut.data.tck.repositories.upsert.ProductReviewRepository
import io.micronaut.data.tck.repositories.upsert.WarehouseInventoryRepository
import io.micronaut.data.tck.tests.AbstractUpsertSpec

class OracleXEUpsertSpec extends AbstractUpsertSpec implements OracleXETestPropertyProvider {

    @Override
    ProductReviewRepository getProductReviewRepository() {
        return context.getBean(OracleXEProductReviewRepository)
    }

    @Override
    CustomerProfileRepository getCustomerProfileRepository() {
        return context.getBean(OracleXECustomerProfileRepository)
    }

    @Override
    CustomerProfileUuidRepository getCustomerProfileUuidRepository() {
        return context.getBean(OracleXECustomerProfileUuidRepository)
    }

    @Override
    WarehouseInventoryRepository getWarehouseInventoryRepository() {
        return context.getBean(OracleXEWarehouseInventoryRepository)
    }

    @Override
    List<String> packages() {
        return Arrays.asList("io.micronaut.data.tck.jdbc.entities.upsert")
    }
}
