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
import io.micronaut.data.tck.repositories.CategoryRepository
import io.micronaut.data.tck.repositories.ProductDtoRepository
import io.micronaut.data.tck.repositories.ProductRepository
import io.micronaut.data.tck.tests.AbstractJoinSpec
import spock.lang.IgnoreIf

class OracleXEJoinSpec extends AbstractJoinSpec implements OracleXETestPropertyProvider {

    @Memoized
    @Override
    CategoryRepository getCategoryRepository() {
        return context.getBean(OracleXECategoryRepository)
    }

    @Memoized
    @Override
    ProductRepository getProductRepository() {
        return context.getBean(OracleXEProductRepository)
    }

    @Memoized
    @Override
    ProductDtoRepository getProductDtoRepository() {
        return context.getBean(OracleXEProductDtoRepository)
    }
}
