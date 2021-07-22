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
package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.entities.Company
import io.micronaut.data.tck.entities.Face
import io.micronaut.data.tck.entities.Product
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest(transactional = false, packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
@IgnoreIf({ jvm.isJava15Compatible() })
class AutoTimestampSpec extends Specification {

    @Shared
    @Inject
    CompanyRepo companyRepo

    @Shared
    @Inject
    ProductRepo productRepo

    @Shared
    @Inject
    FaceRepo taskRepo

    void "test java.util.Date date created and last updated"() {
        when:
        def company = new Company("Apple", new URL("https://apple.com"))
        companyRepo.save(company)
        def dateCreated = company.dateCreated

        then:
        company.myId != null
        dateCreated != null
        company.dateCreated.toInstant().toEpochMilli() == company.lastUpdated.toEpochMilli()
        companyRepo.findById(company.myId).get().dateCreated == company.dateCreated

        when:
        companyRepo.update(company.myId, "Changed")
        def company2 = companyRepo.findById(company.myId).orElse(null)

        then:
        company.dateCreated.time == dateCreated.time
        company.dateCreated.time == company2.dateCreated.time
        company2.name == 'Changed'
        company2.lastUpdated.toEpochMilli() > company2.dateCreated.toInstant().toEpochMilli()
    }

    void "test java.time.LocalDateTime date created and last updated"() {
        when:
        def product = new Product("Foo", BigDecimal.ONE)
        productRepo.save(product)
        def dateCreated = product.dateCreated

        then:
        product.id != null
        dateCreated != null
        product.dateCreated == product.lastUpdated
        productRepo.findById(product.id).get().dateCreated == product.dateCreated

        when:
        product.changePrice()
        productRepo.update(product.id, BigDecimal.TEN)
        def product2 = productRepo.findById(product.id).orElse(null)

        then:
        product.dateCreated == dateCreated
        product.dateCreated == product2.dateCreated
        product2.price == BigDecimal.TEN
        product2.lastUpdated > product2.dateCreated
    }

    void "test java.time.Instant date created and last updated"() {
        when:
        def face = new Face("Foo")
        taskRepo.save(face)
        def dateCreated = face.dateCreated

        then:
        face.id != null
        dateCreated != null
        face.dateCreated == face.lastUpdated
        taskRepo.findById(face.id).get().dateCreated == face.dateCreated

        when:
        face.setName("Bar")
        taskRepo.update(face)
        def task2 = taskRepo.findById(face.id).orElse(null)

        then:
        face.dateCreated == dateCreated
        face.dateCreated == task2.dateCreated
        task2.name == "Bar"
        task2.lastUpdated > task2.dateCreated
    }
}
