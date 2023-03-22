/*
 * Copyright 2017-2020 original authors
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

import groovy.transform.Memoized
import io.micronaut.data.tck.repositories.SaleItemRepository
import io.micronaut.data.tck.repositories.SaleRepository
import io.micronaut.data.tck.tests.AbstractJSONSpec
import spock.lang.IgnoreIf

class OracleXEJsonSpec extends AbstractJSONSpec implements OracleXETestPropertyProvider {
    @Override
    SaleRepository getSaleRepository() {
        return applicationContext.getBean(OracleXESaleRepository)
    }

    @Memoized
    @Override
    SaleItemRepository getSaleItemRepository() {
        return applicationContext.getBean(OracleXESaleItemRepository)
    }
}
