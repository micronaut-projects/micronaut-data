package io.micronaut.data.hibernate

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.data.event.listeners.PostPersistEventListener
import io.micronaut.data.hibernate.entities.EventIndividualTest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.lang.Stepwise

@MicronautTest(transactional = false, packages = "io.micronaut.data.tck.entities")
@H2DBProperties
@Property(name = "spec.name", value = "EventsPostPersistSpec")
@Stepwise
class EventsPostPersistSpec extends Specification {

    @Inject EventIndividualTestRepo repo

    @Inject EventsPostPersistTestListener eventTestListener

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

        eventTestListener.postPersist == 1
    }
}

@Factory
@Requires(property = "spec.name", value = "EventsPostPersistSpec")
class EventsPostPersistTestListener {
    private int postPersist = 0

    int getPostPersist() {
        return postPersist
    }

    void reset() {
        postPersist = 0
    }

    @Singleton
    PostPersistEventListener<EventIndividualTest> postPersistListener() {
        return new PostPersistEventListener<EventIndividualTest>() {
            @Override
            void postPersist(EventIndividualTest entity) {
                postPersist++
            }
        }
    }
}
