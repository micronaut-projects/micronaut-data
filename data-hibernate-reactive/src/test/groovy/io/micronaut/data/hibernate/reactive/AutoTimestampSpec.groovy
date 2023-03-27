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
package io.micronaut.data.hibernate.reactive

import io.micronaut.data.tck.entities.Company
import io.micronaut.data.tck.entities.Face
import io.micronaut.data.tck.entities.Product
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

import java.time.temporal.ChronoUnit

@MicronautTest(transactional = false, packages = "io.micronaut.data.tck.entities")
class AutoTimestampSpec extends Specification implements PostgresHibernateReactiveProperties {

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
        companyRepo.save(company).block()
        def dateCreated = company.dateCreated

        then:
        company.myId != null
        dateCreated != null
        company.dateCreated.toInstant().toEpochMilli() == company.lastUpdated.toEpochMilli()
        companyRepo.findById(company.myId).block().dateCreated.time == company.dateCreated.time

        when:
        companyRepo.update(company.myId, "Changed").block()
        def company2 = companyRepo.findById(company.myId).block()

        then:
        company.dateCreated.time == dateCreated.time
        company.dateCreated.time == company2.dateCreated.time
        company2.name == 'Changed'
        company2.lastUpdated.toEpochMilli() > company2.dateCreated.toInstant().toEpochMilli()
    }

    void "test java.time.LocalDateTime date created and last updated"() {
        when:
        def product = new Product("Foo", BigDecimal.ONE)
        productRepo.save(product).block()
        def dateCreated = product.dateCreated

        then:
        product.id != null
        dateCreated != null
        product.dateCreated == product.lastUpdated
        productRepo.findById(product.id).block().dateCreated.truncatedTo(ChronoUnit.MILLIS) == product.dateCreated.truncatedTo(ChronoUnit.MILLIS)

        when:
        product.changePrice(null)
        productRepo.update(product.id, BigDecimal.TEN).block()
        def product2 = productRepo.findById(product.id).block()

        then:
        product.dateCreated.truncatedTo(ChronoUnit.MILLIS) == dateCreated.truncatedTo(ChronoUnit.MILLIS)
        product.dateCreated.truncatedTo(ChronoUnit.MILLIS) == product2.dateCreated.truncatedTo(ChronoUnit.MILLIS)
        product2.price == BigDecimal.TEN
        product2.lastUpdated.truncatedTo(ChronoUnit.MILLIS) > product2.dateCreated.truncatedTo(ChronoUnit.MILLIS)
    }

    void "test java.time.Instant date created and last updated"() {
        when:
        def face = new Face("Foo")
        taskRepo.save(face).block()
        def dateCreated = face.dateCreated

        then:
        face.id != null
        dateCreated != null
        face.dateCreated.toEpochMilli() == face.lastUpdated.toEpochMilli()
        taskRepo.findById(face.id).block().dateCreated.toEpochMilli() == face.dateCreated.toEpochMilli()

        when:
        face.setName("Bar")
        taskRepo.update(face).block()
        def task2 = taskRepo.findById(face.id).block()

        then:
        face.dateCreated.toEpochMilli() == dateCreated.toEpochMilli()
        face.dateCreated.toEpochMilli() == task2.dateCreated.toEpochMilli()
        task2.name == "Bar"
        task2.lastUpdated.toEpochMilli() > task2.dateCreated.toEpochMilli()
    }
}
