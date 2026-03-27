package io.micronaut.data.nitrite.mongoport

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.mongoport.entities.NitriteMpPerson
import io.micronaut.data.nitrite.mongoport.entities.NitriteMpAddress
import io.micronaut.data.nitrite.mongoport.repositories.NitriteMpPersonRepository
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class NitriteDocumentRepositorySpec extends Specification implements NitriteTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    NitriteMpPersonRepository personRepository = applicationContext.getBean(NitriteMpPersonRepository)

    def cleanup() {
        personRepository.deleteAll()
    }

    void "test between"() {
        given:
            savePersons(["A", "B", "C", "D", "E", "F"])
        when:
            def peopleBetween = personRepository.findAllByNameBetween("B", "E").collect { it.name}
        then:
            peopleBetween == ["B", "C", "D", "E"]
        when:
            def peopleNotBetween = personRepository.findAllByNameNotBetween("B", "E").collect { it.name}
        then:
            peopleNotBetween == ["A", "F"]
    }

    void "test custom find"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis"])
            def peopleToUpdate = personRepository.findAll().toList()
            peopleToUpdate.forEach {it.age = 100 }
            personRepository.updateAll(peopleToUpdate)
        when:
            def allPeople = personRepository.findAll().toList()
        then:
            allPeople.size() == 4
            allPeople[0].age == 100
            allPeople[1].age == 100
            allPeople[2].age == 100
            allPeople[3].age == 100

        when:
            def people = personRepository.customFind("J.*").toList()

        then:
            people.size() == 2
            people[0].name == "James"
            people[0].age == 0
            people[1].name == "Jeff"
            people[1].age == 0
    }

    void "test custom find paginated"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis", "Josh", "Steven", "Jake", "Jim"])
            def peopleToUpdate = personRepository.findAll().toList()
            peopleToUpdate.forEach {it.age = 100 }
            personRepository.updateAll(peopleToUpdate)
        when:
            def peoplePage = personRepository.customFindPage("J.*", Pageable.from(0, 2))
            def people = peoplePage.getContent()
        then:
            peoplePage.hasTotalSize()
            peoplePage.getTotalPages() == 3
            peoplePage.pageNumber == 0
            people.size() == 2
            people[0].name == "Jake"
            people[0].age == 0
            people[1].name == "James"
        when:
            peoplePage = personRepository.customFindPage("J.*", peoplePage.nextPageable())
            people = peoplePage.getContent()
        then:
            peoplePage.hasTotalSize()
            peoplePage.getTotalPages() == 3
            peoplePage.pageNumber == 1
            people.size() == 2
            people[0].name == "Jeff"
            people[0].age == 0
            people[1].name == "Jim"
        when:
            peoplePage = personRepository.customFindPage("J.*", peoplePage.nextPageable())
            people = peoplePage.getContent()
        then:
            peoplePage.hasTotalSize()
            peoplePage.getTotalPages() == 3
            peoplePage.pageNumber == 2
            people.size() == 1
            people[0].name == "Josh"
    }

    void "test custom update"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
            def people = personRepository.findAll().toList()
            def dennisList = people.findAll { it.name == "Dennis" }
            dennisList.forEach { it.name = "Denis" }
            personRepository.updateAll(dennisList)
            people = personRepository.findAll().toList()

        then:
            people.count { it.name == "Dennis"} == 0
            people.count { it.name == "Denis"} == 2
    }

    void "test custom delete"() {
        given:
            savePersons(["Dennis", "Jeff", "James", "Dennis"])

        when:
            def people = personRepository.findAll().toList()
            people.findAll {it.name == "Dennis"}.forEach{ it.name = "DoNotDelete"}
            def deleted = personRepository.deleteAll(people.findAll { it.name == "DoNotDelete" })
            people = personRepository.findAll().toList()

        then:
            people.size() == 2
            people.every { it.name != "Dennis" }
    }

    void "test sorting"() {
        given:
            savePersons(["Charlie", "Alice", "Bob"])
        when:
            def people = personRepository.findAll(Sort.of(Sort.Order.asc("name"))).toList()
        then:
            people.collect { it.name } == ["Alice", "Bob", "Charlie"]
        when:
            people = personRepository.findAll(Sort.of(Sort.Order.desc("name"))).toList()
        then:
            people.collect { it.name } == ["Charlie", "Bob", "Alice"]
    }

    private void savePersons(List<String> names) {
        names.each { name ->
            personRepository.save(new NitriteMpPerson(name))
        }
    }
}
