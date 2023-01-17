package io.micronaut.data.hibernate6

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.data.event.listeners.*
import io.micronaut.data.hibernate6.entities.EventIndividualTest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.lang.Stepwise

@MicronautTest(transactional = false, packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
@Property(name = "spec.name", value = "EventsPreUpdateSpec")
@Stepwise
class EventsPreUpdateSpec extends Specification {

    @Inject EventIndividualTestRepo repo

    @Inject EventsPreUpdateTestListener eventTestListener

    void setup() {
        eventTestListener.reset()
    }

    void "test persist event"() {
        given:
        def eventTest = new EventIndividualTest(1)
        when:
        repo.save(eventTest)

        then:
        eventTest.uuid != null
        eventTest.dateCreated != null
        eventTest.dateUpdated == eventTest.dateCreated

        eventTestListener.preUpdate == 0
    }

    void "test update event"() {
        given:
        def eventTest = new EventIndividualTest(1)
        when:
        repo.save(eventTest)
        eventTest.value = 2
        repo.update(eventTest)

        then:
        eventTestListener.preUpdate == 1
    }

    void "test remove event"() {
        given:
        def eventTest = new EventIndividualTest(1)
        when:
        repo.save(eventTest)
        repo.delete(eventTest)

        then:
        eventTestListener.preUpdate == 0
    }
}

@Factory
@Requires(property = "spec.name", value = "EventsPreUpdateSpec")
class EventsPreUpdateTestListener {
    private int preUpdate = 0

    int getPreUpdate() {
        return preUpdate
    }

    void reset() {
        preUpdate = 0
    }

    @Singleton
    PreUpdateEventListener<EventIndividualTest> preUpdateEventListener() {
        return new PreUpdateEventListener<EventIndividualTest>() {
            @Override
            boolean preUpdate(EventIndividualTest entity) {
                preUpdate++
                true
            }
        }
    }
}
