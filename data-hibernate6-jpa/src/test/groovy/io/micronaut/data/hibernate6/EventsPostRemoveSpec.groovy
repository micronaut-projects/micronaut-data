package io.micronaut.data.hibernate6

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.data.event.listeners.PostPersistEventListener
import io.micronaut.data.event.listeners.PostRemoveEventListener
import io.micronaut.data.event.listeners.PreRemoveEventListener
import io.micronaut.data.hibernate6.entities.EventIndividualTest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.lang.Stepwise

@MicronautTest(transactional = false, packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
@Property(name = "spec.name", value = "EventsPostRemoveSpec")
@Stepwise
class EventsPostRemoveSpec extends Specification {

    @Inject EventIndividualTestRepo repo

    @Inject EventsPostRemoveTestListener eventTestListener

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

        eventTestListener.postRemove == 0
    }

    void "test update event"() {
        given:
        def eventTest = new EventIndividualTest(1)
        when:
        repo.save(eventTest)
        eventTest.value = 2
        repo.update(eventTest)

        then:
        eventTestListener.postRemove == 0
    }

    void "test remove event"() {
        given:
        def eventTest = new EventIndividualTest(1)
        when:
        repo.save(eventTest)
        repo.delete(eventTest)

        then:
        eventTestListener.postRemove == 1
    }
}

@Factory
@Requires(property = "spec.name", value = "EventsPostRemoveSpec")
class EventsPostRemoveTestListener {
    private int postRemove = 0

    int getPostRemove() {
        return postRemove
    }


    void reset() {
        postRemove = 0
    }

    @Singleton
    PostRemoveEventListener<EventIndividualTest> postRemoveEventListener() {
        return new PostRemoveEventListener<EventIndividualTest>() {
            @Override
            void postRemove(EventIndividualTest entity) {
                postRemove++
            }
        }
    }
}
