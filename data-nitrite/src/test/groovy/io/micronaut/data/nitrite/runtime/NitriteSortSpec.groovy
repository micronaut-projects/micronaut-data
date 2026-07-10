package io.micronaut.data.nitrite.runtime

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.nitrite.model.NitriteMpPerson
import io.micronaut.data.nitrite.repository.NitriteMpPersonRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class NitriteSortSpec extends Specification implements NitriteTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    NitriteMpPersonRepository personRepository = applicationContext.getBean(NitriteMpPersonRepository)

    def cleanup() {
        personRepository.deleteAll()
    }

    def setup() {
        personRepository.saveAll([
                new NitriteMpPerson(name: "Charlie", age: 30),
                new NitriteMpPerson(name: "Alice", age: 25),
                new NitriteMpPerson(name: "Bob", age: 35)
        ])
    }

    void 'test sort ascending'() {
        when:
            def people = personRepository.findAll(Sort.of(Sort.Order.asc("name"))).toList()
        then:
            people.collect { it.name } == ["Alice", "Bob", "Charlie"]
    }

    void 'test sort descending'() {
        when:
            def people = personRepository.findAll(Sort.of(Sort.Order.desc("name"))).toList()
        then:
            people.collect { it.name } == ["Charlie", "Bob", "Alice"]
    }

    void 'test sort by age'() {
        when:
            def people = personRepository.findAll(Sort.of(Sort.Order.asc("age"))).toList()
        then:
            people.collect { it.name } == ["Alice", "Charlie", "Bob"]
    }

    void 'test sort with pageable'() {
        when:
            def page = personRepository.findAll(Pageable.from(0, 2, Sort.of(Sort.Order.asc("name"))))
        then:
            page.content.size() == 2
            page.content[0].name == "Alice"
            page.content[1].name == "Bob"
    }
}
