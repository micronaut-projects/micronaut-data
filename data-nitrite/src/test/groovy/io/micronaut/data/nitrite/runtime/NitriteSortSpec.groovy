package io.micronaut.data.nitrite.runtime

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.CursoredPage
import io.micronaut.data.model.CursoredPageable
import io.micronaut.data.exceptions.NonUniqueResultException
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

    void 'a sorted single-result query returns the matching record'() {
        when: 'a single-result finder carries an OrderBy but no limit of its own'
            def person = personRepository.findByNameOrderByAge("Alice")
        then: 'the cursor is bounded so Nitrite can order from an index, and the record still comes back'
            person.present
            person.get().name == "Alice"
            person.get().age == 25
    }

    void 'a sorted single-result query still reports a non-unique result'() {
        given: 'a second record sharing the queried value'
            personRepository.save(new NitriteMpPerson(name: "Alice", age: 41))
        when:
            personRepository.findByNameOrderByAge("Alice")
        then: 'the bound is two rows, not one, so the second match is still seen and reported'
            thrown(NonUniqueResultException)
    }

    void 'cursor pagination does not skip records with equal sort values'() {
        given:
        personRepository.deleteAll()
        personRepository.saveAll([
                new NitriteMpPerson(name: "Same", age: 1),
                new NitriteMpPerson(name: "Same", age: 2),
                new NitriteMpPerson(name: "Same", age: 3),
                new NitriteMpPerson(name: "Same", age: 4)
        ])
        def pageable = CursoredPageable.from(2, Sort.of(Sort.Order.asc("name")))

        when:
        CursoredPage<NitriteMpPerson> first = (CursoredPage<NitriteMpPerson>) personRepository.findAll(pageable)
        CursoredPage<NitriteMpPerson> second = (CursoredPage<NitriteMpPerson>) personRepository.findAll(first.nextPageable())

        then:
        first.content.size() == 2
        second.content.size() == 2
        (first.content + second.content)*.age.sort() == [1, 2, 3, 4]
    }
}
