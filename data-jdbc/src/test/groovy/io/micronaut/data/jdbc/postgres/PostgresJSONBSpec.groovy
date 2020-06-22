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
package io.micronaut.data.jdbc.postgres

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.entities.Sale
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PostgresJSONBSpec extends Specification implements PostgresTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    PostgresSaleRepository saleRepository = applicationContext.getBean(PostgresSaleRepository)

    void "test read and write json"() {
        when:
        Sale sale = new Sale()
        sale.setName("test 1")
        sale.data = [foo:'bar']
        sale.quantities = [foo:10]
        saleRepository.save(sale)
        sale = saleRepository.findById(sale.id).orElse(null)

        then:
        sale.name == 'test 1'
        sale.data == [foo:'bar']
        sale.quantities == [foo:10]

        when:
        sale.data.put('foo2', 'bar2')
        saleRepository.update(sale)
        sale = saleRepository.findById(sale.id).orElse(null)
        then:
        sale.data.containsKey('foo2')

        when:
        saleRepository.updateData(sale.id,[foo:'changed'] )
        sale = saleRepository.findById(sale.id).orElse(null)

        then:
        sale.name == 'test 1'
        sale.data == [foo:'changed']
        sale.quantities == [foo:10]

        when:"retrieving the data via DTO"
        def dto = saleRepository.getById(sale.id)

        then:"the data is correct"
        dto.name == 'test 1'
        dto.data == [foo:'changed']

        cleanup:
        saleRepository.deleteAll()
    }
}
