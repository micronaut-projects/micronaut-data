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
@Property(name = "spec.name", value = "EventsPreRemoveSpec")
@Stepwise
class EventsPreRemoveSpec extends Specification {

    @Inject EventIndividualTestRepo repo

    @Inject EventsPreRemoveTestListener eventTestListener

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

        eventTestListener.preRemove == 0
    }

    void "test update event"() {
        given:
        def eventTest = new EventIndividualTest(1)
        when:
        repo.save(eventTest)
        eventTest.value = 2
        repo.update(eventTest)

        then:
        eventTestListener.preRemove == 0
    }

    void "test remove event"() {
        given:
        def eventTest = new EventIndividualTest(1)
        when:
        repo.save(eventTest)
        repo.delete(eventTest)

        then:
        eventTestListener.preRemove == 1
    }
}

@Factory
@Requires(property = "spec.name", value = "EventsPreRemoveSpec")
class EventsPreRemoveTestListener {
    private int preRemove = 0

    int getPreRemove() {
        return preRemove
    }


    void reset() {
        preRemove = 0
    }

    @Singleton
    PreRemoveEventListener<EventIndividualTest> preRemoveEventListener() {
        return new PreRemoveEventListener<EventIndividualTest>() {
            @Override
            boolean preRemove(EventIndividualTest entity) {
                preRemove++
                true
            }
        }
    }
}
