package io.micronaut.data.hibernate.datetime

import io.micronaut.context.annotation.Property
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class LocalDateSpec extends Specification {

    @Inject LocalDateTestRepository repository

    void "test sort by date created"() {
        when:
        repository.save("Fred")
        repository.save("Bob")

        then:
        repository.count() == 2
        repository.findAll(Pageable.from(Sort.of(
                Sort.Order.asc("name")
        ))).first().name == "Bob"
        repository.findAll(Pageable.from(Sort.of(
                Sort.Order.desc("createdDate")
        ))).content*.createdDate != null
        repository.findAll(Pageable.from(Sort.of(
                Sort.Order.desc("createdDate")
        ))).first().name == "Fred"
    }
}
