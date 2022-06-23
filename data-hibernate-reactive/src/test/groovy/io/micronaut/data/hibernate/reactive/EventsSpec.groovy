package io.micronaut.data.hibernate.reactive


import io.micronaut.data.hibernate.reactive.entities.EventTest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false, packages = "io.micronaut.data.tck.entities")
class EventsSpec extends Specification implements PostgresHibernateReactiveProperties {

    @Inject EventTestRepo repo

    void "test pre-persist event"() {
        given:
        def eventTest = new EventTest()
        when:
        repo.save(eventTest).block()

        then:
        eventTest.uuid != null
        eventTest.dateCreated != null
        eventTest.dateUpdated == eventTest.dateCreated
        eventTest.prePersist == 1
        eventTest.postPersist == 1
        eventTest.preRemove == 0
        eventTest.preUpdate == 0
        eventTest.preUpdate == 0
    }
}
