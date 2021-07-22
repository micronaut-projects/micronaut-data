package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Property
import io.micronaut.data.hibernate.entities.EventTest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification
import spock.lang.Stepwise

import jakarta.inject.Inject

@MicronautTest(transactional = false, packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
@Stepwise
class EventsSpec extends Specification {

    @Inject EventTestRepo repo

    void "test pre-persist event"() {
        given:
        def eventTest = new EventTest()
        when:
        repo.save(eventTest)

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
