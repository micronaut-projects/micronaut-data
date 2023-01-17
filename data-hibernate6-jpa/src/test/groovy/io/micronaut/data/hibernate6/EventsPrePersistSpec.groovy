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
@Property(name = "spec.name", value = "EventsPrePersistSpec")
@Stepwise
class EventsPrePersistSpec extends Specification {

    @Inject EventIndividualTestRepo repo

    @Inject EventsPrePersistTestListener eventTestListener

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

        eventTestListener.prePersist == 1
    }
}

@Factory
@Requires(property = "spec.name", value = "EventsPrePersistSpec")
class EventsPrePersistTestListener {
    private int prePersist = 0

    int getPrePersist() {
        return prePersist
    }

    void reset() {
        prePersist = 0
    }

    @Singleton
    PrePersistEventListener<EventIndividualTest> prePersistListener() {
        return new PrePersistEventListener<EventIndividualTest>() {
            @Override
            boolean prePersist(EventIndividualTest entity) {
                prePersist++
                true
            }
        }
    }
}
