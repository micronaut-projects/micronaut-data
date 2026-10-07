package io.micronaut.data.hibernate.reactive.propagate

import io.micronaut.data.hibernate.reactive.PostgresHibernateReactiveProperties
import io.micronaut.data.hibernate.reactive.operations.HibernateReactorRepositoryOperations
import io.micronaut.data.model.runtime.PagedQuery
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.hibernate.reactive.stage.Stage
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations
import spock.lang.Issue
import spock.lang.Specification

import java.time.Duration


@MicronautTest(transactional = false)
class ReactiveDataSpec extends Specification implements PostgresHibernateReactiveProperties {

    @Inject
    TestClient client

    @Inject
    HibernateReactorRepositoryOperations operations

    @Inject
    FooRepository repository

    @Inject
    ReactorReactiveTransactionOperations<?> transactionOperations

    void 'Verify ReactorCrudRepository.save(...) will update entity if already exist'() {
        setup:
        def id = 1
        def name = "SomeName"

        when:
        def res = client.create(new FooController.CreateRequest(id, name)).block()

        then:
        res != null
        res.id == id
        res.name == name

        when:
        res = client.read(id).block()

        then:
        res != null
        res.id == id
        res.name == name
    }

    void 'Verify @RequestScope present inside @Transactional methods'() {
        setup:
        def id = 2
        def name = "SomeName"

        when:
        def res = client.createTransactional(new FooController.CreateRequest(id, name)).block()

        then:
        res != null
        res.id == id
        res.name == name

        when:
        res = client.read(id).block()

        then:
        res != null
        res.id == id
        res.name == name
    }

    void 'Verify a Flux streamed from a connectable controller method closes its session'() {
        setup:
        client.create(new FooController.CreateRequest(10, "A")).block()
        client.create(new FooController.CreateRequest(11, "B")).block()

        when:
        def first = client.list().block()
        def second = client.list().block()

        then:
        first*.id.containsAll([10L, 11L])
        second*.id.containsAll([10L, 11L])
    }

    void 'Verify a session is closed when its Flux completes on another thread'() {
        setup:
        client.create(new FooController.CreateRequest(50, "H")).block()
        Stage.Session captured = null

        when:
        def all = operations.withSessionFlux { Stage.Session session ->
            captured = session
            Mono.fromCompletionStage(session.createSelectionQuery("from Foo", Foo).getResultList())
                .flatMapMany { Flux.fromIterable(it) }
                .publishOn(Schedulers.parallel())
        }.collectList().block()

        then:
        !all.isEmpty()
        !captured.isOpen()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-data/issues/2165")
    void 'Verify a transaction continues after switching threads'() {
        when: "The pipeline continues on another thread, like after a reactive HTTP client call"
        def found = transactionOperations.withTransactionMono { status ->
            repository.save(new Foo(70, "I"))
                .publishOn(Schedulers.parallel())
                .flatMap { repository.findById(70L) }
        }.block(Duration.ofSeconds(10))

        then:
        found.name == "I"
        repository.findById(70L).block().name == "I"
    }

    @Issue("https://github.com/micronaut-projects/micronaut-data/issues/2165")
    void 'Verify a transaction commits an update made after switching threads'() {
        when:
        transactionOperations.withTransactionMono { status ->
            repository.save(new Foo(71, "J"))
                .delayElement(Duration.ofMillis(50))
                .flatMap { repository.update(new Foo(71, "K")) }
        }.block(Duration.ofSeconds(10))

        then:
        repository.findById(71L).block().name == "K"
    }

    @Issue("https://github.com/micronaut-projects/micronaut-data/issues/2165")
    void 'Verify a transaction rolls back after switching threads'() {
        when:
        transactionOperations.withTransactionMono { status ->
            repository.save(new Foo(72, "L"))
                .publishOn(Schedulers.parallel())
                .flatMap { Mono.error(new IllegalStateException("boom")) }
        }.block(Duration.ofSeconds(10))

        then:
        thrown(IllegalStateException)
        repository.findById(72L).block() == null
    }

    void 'Verify counting a paged query counts its root entity'() {
        setup:
        client.create(new FooController.CreateRequest(30, "E")).block()
        def pagedQuery = [getRootEntity: { Foo }] as PagedQuery<Foo>

        expect:
        operations.count(pagedQuery).block() == client.list().block().size()
    }

}
