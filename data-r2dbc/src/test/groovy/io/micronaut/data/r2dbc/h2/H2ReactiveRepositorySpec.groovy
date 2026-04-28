package io.micronaut.data.r2dbc.h2

import groovy.transform.Memoized
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.BookReactiveRepository
import io.micronaut.data.tck.repositories.StudentReactiveRepository
import io.micronaut.data.tck.tests.AbstractReactiveRepositorySpec
import io.micronaut.transaction.reactive.ReactiveTransactionStatus
import io.r2dbc.spi.Connection
import reactor.core.publisher.Mono

class H2ReactiveRepositorySpec extends AbstractReactiveRepositorySpec implements H2TestPropertyProvider {

    @Memoized
    @Override
    H2ReactivePersonRepository getPersonRepository() {
        return context.getBean(H2ReactivePersonRepository)

    }

    @Memoized
    @Override
    StudentReactiveRepository getStudentRepository() {
        return context.getBean(H2StudentReactiveRepository)
    }

    @Memoized
    @Override
    BookReactiveRepository getBookRepository() {
        return context.getBean(H2ReactiveBookRepository)
    }

    void 'test with transactional connection'() {
        given:
        R2dbcOperations r2dbcOperations = context.getBean(R2dbcOperations)

        personRepository.save(new Person(name: "Tony")).block()

        when:
        Person person = Mono.from(r2dbcOperations.withTransaction({ ReactiveTransactionStatus<Connection> status ->
            personRepository.findByName("Tony", status).toFlowable()
        })).block()

        then:
        person != null
    }

    void 'test save chooses update for entity with id'() {
        given:
        Person person = personRepository.save(new Person(name: "SaveInsert", age: 10)).block()

        when:
        person.name = "SaveUpdate"
        person.age = 11
        Person updated = personRepository.save(person).block()

        then:
        updated.id == person.id
        updated.name == "SaveUpdate"
        updated.age == 11
        personRepository.findById(person.id).block().name == "SaveUpdate"
    }

    void 'test saveAll chooses insert or update and preserves order'() {
        given:
        Person existing = personRepository.save(new Person(name: "SaveAllExisting", age: 30)).block()
        existing.name = "SaveAllExistingUpdated"
        existing.age = 31

        when:
        List<Person> saved = personRepository.saveAll([
                new Person(name: "SaveAllNew1", age: 20),
                existing,
                new Person(name: "SaveAllNew2", age: 22)
        ]).collectList().block()

        then:
        saved*.name == ["SaveAllNew1", "SaveAllExistingUpdated", "SaveAllNew2"]
        saved[1].id == existing.id
        personRepository.findById(existing.id).block().name == "SaveAllExistingUpdated"
    }
}
