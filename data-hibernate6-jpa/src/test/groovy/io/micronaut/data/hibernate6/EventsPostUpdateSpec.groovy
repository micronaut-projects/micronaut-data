package io.micronaut.data.hibernate6

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.data.event.listeners.PostRemoveEventListener
import io.micronaut.data.event.listeners.PostUpdateEventListener
import io.micronaut.data.event.listeners.PreUpdateEventListener
import io.micronaut.data.hibernate6.entities.EventIndividualTest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.lang.Stepwise

@MicronautTest(transactional = false, packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
@Property(name = "spec.name", value = "EventsPostUpdateSpec")
@Stepwise
class EventsPostUpdateSpec extends Specification {

    @Inject EventIndividualTestRepo repo

    @Inject EventsPostUpdateTestListener eventTestListener

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

        eventTestListener.postUpdate == 0
    }

    void "test update event"() {
        given:
        def eventTest = new EventIndividualTest(1)
        when:
        repo.save(eventTest)
        eventTest.value = 2
        repo.update(eventTest)

        then:
        eventTestListener.postUpdate == 1
    }

    void "test remove event"() {
        given:
        def eventTest = new EventIndividualTest(1)
        when:
        repo.save(eventTest)
        repo.delete(eventTest)

        then:
        eventTestListener.postUpdate == 0
    }
}

@Factory
@Requires(property = "spec.name", value = "EventsPostUpdateSpec")
class EventsPostUpdateTestListener {
    private int postUpdate = 0

    int getPostUpdate() {
        return postUpdate
    }

    void reset() {
        postUpdate = 0
    }

    @Singleton
    PostUpdateEventListener<EventIndividualTest> postUpdateEventListener() {
        return new PostUpdateEventListener<EventIndividualTest>() {
            @Override
            void postUpdate(EventIndividualTest entity) {
                postUpdate++
            }
        }
    }
}
