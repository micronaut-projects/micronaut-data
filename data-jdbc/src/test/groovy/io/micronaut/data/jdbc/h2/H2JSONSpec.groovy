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
package io.micronaut.data.jdbc.h2

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.Memoized
import io.micronaut.data.tck.entities.Sale
import io.micronaut.data.tck.repositories.SaleItemRepository
import io.micronaut.data.tck.repositories.SaleRepository
import io.micronaut.data.tck.tests.AbstractJSONSpec

class H2JSONSpec extends AbstractJSONSpec implements H2TestPropertyProvider {

    @Memoized
    @Override
    H2SaleRepository getSaleRepository() {
        return applicationContext.getBean(H2SaleRepository)
    }

    @Memoized
    @Override
    SaleItemRepository getSaleItemRepository() {
        return applicationContext.getBean(H2SaleItemRepository)
    }

    void "test read DTO from JSON string field"() {
        def objectMapper = new ObjectMapper()
        def discount = new Discount()
        discount.amount = 12
        discount.numberOfDays = 5
        discount.note = "Valid since April 1st"
        given:
        def sale = new Sale(name: "sale")
        def extraData = objectMapper.writeValueAsString(discount)
        sale.setExtraData(extraData)

        when:
        sale = saleRepository.save(sale)
        def optSale = saleRepository.findById(sale.id)
        def optLoadedDiscount = saleRepository.getDiscountById(sale.id)

        then:
        optSale.present
        optLoadedDiscount.present
        def loadedDiscount = optLoadedDiscount.get()
        loadedDiscount.amount == discount.amount
        loadedDiscount.note == discount.note
        loadedDiscount.numberOfDays == discount.numberOfDays

        cleanup:
        saleRepository.deleteAll()
        saleItemRepository.deleteAll()
    }
}
