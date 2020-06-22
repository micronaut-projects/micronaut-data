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
package io.micronaut.data.jdbc.mysql

import io.micronaut.data.tck.entities.Sale
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
class MySqlJsonSpec extends Specification implements MySQLTestPropertyProvider {


    @Inject MySqlSaleRepository saleRepository

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

//      TODO: updates not working due to https://bugs.mysql.com/bug.php?id=93052
//        when:
//        saleRepository.updateData(sale.id,[foo:'changed'] )
//        sale = saleRepository.findById(sale.id).orElse(null)
//
//        then:
//        sale.name == 'test 1'
//        sale.data == [foo:'changed']

    }
}
