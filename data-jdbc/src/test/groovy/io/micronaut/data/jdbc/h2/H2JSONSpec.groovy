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

import io.micronaut.context.annotation.Property
import io.micronaut.data.jdbc.postgres.PostgresSaleRepository
import io.micronaut.data.tck.entities.Sale
import io.micronaut.test.annotation.MicronautTest
import spock.lang.PendingFeature
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
class H2JSONSpec extends Specification {
    @Inject H2SaleRepository saleRepository

    @PendingFeature(reason = "No released version of H2 supports JSON yet. See https://github.com/h2database/h2database/pull/1828 for future feature.")
    void "test read and write json"() {
        when:
        Sale sale = new Sale()
        sale.setName("test 1")
        sale.data = [foo:'bar']
        saleRepository.save(sale)
        sale = saleRepository.findById(sale.id).orElse(null)

        then:
        sale.name == 'test 1'
        sale.data == [foo:'bar']

        when:
        saleRepository.updateData(sale.id,[foo:'changed'] )
        sale = saleRepository.findById(sale.id).orElse(null)

        then:
        sale.name == 'test 1'
        sale.data == [foo:'changed']
    }
}
