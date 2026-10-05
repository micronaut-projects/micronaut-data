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
import spock.lang.Specification


@MicronautTest(transactional = false)
class ReactiveDataSpec extends Specification implements PostgresHibernateReactiveProperties {

    @Inject
    TestClient client

    @Inject
    HibernateReactorRepositoryOperations operations

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

    void 'Verify counting a paged query counts its root entity'() {
        setup:
        client.create(new FooController.CreateRequest(30, "E")).block()
        def pagedQuery = [getRootEntity: { Foo }] as PagedQuery<Foo>

        expect:
        operations.count(pagedQuery).block() == client.list().block().size()
    }

}
