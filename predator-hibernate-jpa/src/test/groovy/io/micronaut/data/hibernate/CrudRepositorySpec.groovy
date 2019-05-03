package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Property
import io.micronaut.data.model.Pageable
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification
import spock.lang.Stepwise

import javax.inject.Inject
import javax.validation.ConstraintViolationException

@MicronautTest(rollback = false)
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
@Stepwise
class CrudRepositorySpec extends Specification {

    @Inject
    PersonCrudRepository crudRepository

    void "test save one"() {
        when:"one is saved"
        def person = new Person(name: "Fred")
        crudRepository.save(person)

        then:"the instance is persisted"
        person.id != null
        crudRepository.findById(person.id).isPresent()
        crudRepository.existsById(person.id)
        crudRepository.count() == 1
        crudRepository.count("Fred") == 1
        crudRepository.findAll().size() == 1
    }

    void "test save many"() {
        when:"many are saved"
        def p1 = new Person(name: "Frank")
        def p2 = new Person(name: "Bob")
        def people = [p1, p2]
        crudRepository.saveAll(people)

        then:"all are saved"
        people.every { it.id != null }
        people.every { crudRepository.findById(it.id).isPresent() }
        crudRepository.findAll().size() == 3
        crudRepository.count() == 3
        crudRepository.count("Fred") == 1
        crudRepository.list(Pageable.from(1)).size() == 2
        crudRepository.list(Pageable.from(0, 1)).size() == 1
    }

    void "test delete by id"() {
        when:"an entity is retrieved"
        def person = crudRepository.findByName("Frank")

        then:"the person is not null"
        person != null
        crudRepository.findById(person.id).isPresent()

        when:"the person is deleted"
        crudRepository.deleteById(person.id)

        then:"They are really deleted"
        !crudRepository.findById(person.id).isPresent()
    }
}
