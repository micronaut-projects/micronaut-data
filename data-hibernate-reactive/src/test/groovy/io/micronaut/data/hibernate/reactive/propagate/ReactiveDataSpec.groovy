package io.micronaut.data.hibernate.reactive.propagate

import io.micronaut.data.hibernate.reactive.PostgresHibernateReactiveProperties
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class ReactiveDataSpec extends Specification implements PostgresHibernateReactiveProperties {

    @Inject
    TestClient client

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

}
