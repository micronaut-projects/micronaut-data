package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Property
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import javax.inject.Inject

@MicronautTest(rollback = false)
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
@Stepwise
class ProjectionSpec extends Specification {

    @Inject
    @Shared
    PersonCrudRepository crudRepository

    def setupSpec() {
        crudRepository.saveAll([
                new Person(name: "Jeff", age: 40),
                new Person(name: "Ivan", age: 30),
                new Person(name: "James", age: 35)
        ])
    }

    void "test project on single property"() {
        expect:
        crudRepository.findAgeByName("Jeff") == 40
        crudRepository.findAgeByName("Ivan") == 30
        crudRepository.findMaxAgeByNameLike("J%") == 40
        crudRepository.findMinAgeByNameLike("J%") == 35
        crudRepository.getSumAgeByNameLike("J%") == 75
        crudRepository.getAvgAgeByNameLike("J%") == 37
        crudRepository.readAgeByNameLike("J%").sort() == [35,40]
    }
}
