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
package io.micronaut.data.r2dbc.postgres

import io.micronaut.data.r2dbc.postgres.upsert.PostgresCustomerProfileRepository
import io.micronaut.data.r2dbc.postgres.upsert.PostgresProductReviewRepository
import io.micronaut.data.r2dbc.postgres.upsert.PostgresWarehouseInventoryRepository
import io.micronaut.data.tck.repositories.upsert.CustomerProfileRepository
import io.micronaut.data.tck.repositories.upsert.ProductReviewRepository
import io.micronaut.data.tck.repositories.upsert.WarehouseInventoryRepository
import io.micronaut.data.tck.tests.AbstractUpsertSpec

class PostgresUpsertSpec extends AbstractUpsertSpec implements PostgresTestPropertyProvider {

    @Override
    ProductReviewRepository getProductReviewRepository() {
        return context.getBean(PostgresProductReviewRepository)
    }

    @Override
    CustomerProfileRepository getCustomerProfileRepository() {
        return context.getBean(PostgresCustomerProfileRepository)
    }

    @Override
    WarehouseInventoryRepository getWarehouseInventoryRepository() {
        return context.getBean(PostgresWarehouseInventoryRepository)
    }
}
