package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Property
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest(transactional = false)
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class AutoTimestampSpec extends Specification {

    @Shared
    @Inject
    CompanyRepo companyRepo

    void "test date created and last updated"() {
        when:
        def company = new Company("Apple", new URL("http://apple.com"))
        companyRepo.save(company)
        def dateCreated = company.dateCreated

        then:
        company.myId != null
        dateCreated != null
        company.dateCreated.toInstant() == company.lastUpdated
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
}
