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
package io.micronaut.data.hibernate6

import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.entities.Company
import io.micronaut.data.tck.entities.Face
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

import java.time.temporal.ChronoUnit

@MicronautTest(transactional = false, packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class AutoTimestampSpec extends Specification {

    @Shared
    @Inject
    CompanyRepo companyRepo

    @Shared
    @Inject
    CompanyRepoService companyRepoService

    @Shared
    @Inject
    AuditCompanyRepository auditCompanyRepository

    @Shared
    @Inject
    ProductRepo productRepo

    @Shared
    @Inject
    FaceRepo taskRepo

    def setup() {
        companyRepo.deleteAll()
    }

    void "test java.util.Date date created and last updated"() {
        when:
        def company = new Company("Apple", new URL("https://apple.com"))
        companyRepo.save(company)
        def dateCreated = company.dateCreated

        then:
        company.myId != null
        dateCreated != null
        company.dateCreated.toInstant().truncatedTo(ChronoUnit.MILLIS) == company.lastUpdated.truncatedTo(ChronoUnit.MILLIS)
        companyRepo.findById(company.myId).get().dateCreated == company.dateCreated

        when:
        companyRepo.update(company.myId, "Changed")
        def company2 = companyRepo.findById(company.myId).orElse(null)

        then:
        company.dateCreated.time == dateCreated.time
        company.dateCreated.time == company2.dateCreated.time
        company2.name == 'Changed'
        company2.lastUpdated.truncatedTo(ChronoUnit.MILLIS) > company2.dateCreated.toInstant().truncatedTo(ChronoUnit.MILLIS)
    }

    void "test java.util.Date date created and last updated transactional"() {
        when:
        def company = new AuditCompany()
        company.setName("Apple")
        auditCompanyRepository.save(company)
        def dateCreated = company.dateCreated

        then:
        company.id
        company.createUser
        company.dateCreated
        company.dateCreated.truncatedTo(ChronoUnit.MILLIS) == company.dateUpdated.truncatedTo(ChronoUnit.MILLIS)
    }

    void "test java.time.Instant date created and last updated"() {
        when:
        def face = new Face("Foo")
        taskRepo.save(face)
        def dateCreated = face.dateCreated.truncatedTo(ChronoUnit.MILLIS)

        then:
        face.id != null
        dateCreated != null
        face.dateCreated.truncatedTo(ChronoUnit.MILLIS) == face.lastUpdated.truncatedTo(ChronoUnit.MILLIS)
        taskRepo.findById(face.id).get().dateCreated.truncatedTo(ChronoUnit.MILLIS) == face.dateCreated.truncatedTo(ChronoUnit.MILLIS)

        when:
        face.setName("Bar")
        taskRepo.update(face)
        def task2 = taskRepo.findById(face.id).orElse(null)

        then:
        face.dateCreated.truncatedTo(ChronoUnit.MILLIS) == dateCreated.truncatedTo(ChronoUnit.MILLIS)
        face.dateCreated.truncatedTo(ChronoUnit.MILLIS) == task2.dateCreated.truncatedTo(ChronoUnit.MILLIS)
        task2.name == "Bar"
        task2.lastUpdated.truncatedTo(ChronoUnit.MILLIS) > task2.dateCreated.truncatedTo(ChronoUnit.MILLIS)
    }
}
