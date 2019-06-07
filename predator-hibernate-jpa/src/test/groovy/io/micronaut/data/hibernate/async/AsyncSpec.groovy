package io.micronaut.data.hibernate.async

import io.micronaut.context.annotation.Property
import io.micronaut.data.hibernate.Person
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest(rollback = false, packages = "io.micronaut.data.hibernate")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class AsyncSpec extends Specification {

    @Inject
    AsyncPersonRepo asyncCrudRepository

    void "test async CRUD"() {
        when:"An entity is saved"
        Person p = asyncCrudRepository.save(new Person(name: "Fred", age: 18)).get()

        then:"The entity was saved"
        p != null
        p.id != null

        when:"The entity is retrieved"
        p = asyncCrudRepository.findById(p.id).get()

        then:"Tne entity is found"
        p != null
        p.name == 'Fred'
        asyncCrudRepository.existsById(p.id).get()
        asyncCrudRepository.count().get() == 1

        when:"another entity is saved and all entities are listed"
        def result = asyncCrudRepository.saveAll([new Person(name: "Bob", age: 20), new Person(name: "Chuck", age: 30)])
                .get()
        def john = asyncCrudRepository.save("John", 22).get()

        def list = asyncCrudRepository.findAll().get()
        def withLetterO = asyncCrudRepository.findAllByNameContains("o").get()

        then:"The results are correct"
        asyncCrudRepository.findByName("Bob").get().name == 'Bob'
        john.name == 'John'
        result.size() == 2
        withLetterO.size() == 2
        list.size() == 4

        when:"An entity is deleted"
        asyncCrudRepository.deleteById(john.id).get()
        asyncCrudRepository.delete(p).get()
        list = asyncCrudRepository.findAll().get()

        then:"The results are correct"
        list.size() == 2

        when:"All are deleted"
        asyncCrudRepository.deleteAll().get()

        then:"All are gone"
        asyncCrudRepository.count().get() == 0
    }
}
