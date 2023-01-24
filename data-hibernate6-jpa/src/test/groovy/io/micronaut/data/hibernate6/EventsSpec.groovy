package io.micronaut.data.hibernate6

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.data.event.listeners.PostPersistEventListener
import io.micronaut.data.event.listeners.PostRemoveEventListener
import io.micronaut.data.event.listeners.PostUpdateEventListener
import io.micronaut.data.event.listeners.PrePersistEventListener
import io.micronaut.data.event.listeners.PreRemoveEventListener
import io.micronaut.data.event.listeners.PreUpdateEventListener
import io.micronaut.data.hibernate6.entities.EventTest
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.lang.Stepwise

import jakarta.inject.Inject

@MicronautTest(transactional = false, packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
@Property(name = "spec.name", value = "EventsSpec")
@Stepwise
class EventsSpec extends Specification {

    @Inject EventTestRepo repo

    @Inject EventTestListeners eventTestListeners

    void setup() {
        eventTestListeners.reset()
    }

    void "test persist event"() {
        given:
        def eventTest = new EventTest(1)
        when:
        repo.save(eventTest)

        then:
        eventTest.uuid != null
        eventTest.dateCreated != null
        eventTest.dateUpdated == eventTest.dateCreated

        eventTest.prePersist == 1
        eventTest.postPersist == 1
        eventTest.preRemove == 0
        eventTest.postRemove == 0
        eventTest.preUpdate == 0
        eventTest.preUpdate == 0
        eventTest.postLoad == 0

        eventTestListeners.prePersist == 1
        eventTestListeners.postPersist == 1
        eventTestListeners.preUpdate == 0
        eventTestListeners.postUpdate == 0
        eventTestListeners.preRemove == 0
        eventTestListeners.postRemove == 0
    }

    void "test update event"() {
        given:
        def eventTest = new EventTest(1)
        when:
        repo.save(eventTest)
        eventTest.value = 2
        def eventTestDetachedUpdate = repo.update(eventTest)

        then:
        eventTest.prePersist == 1
        eventTest.postPersist == 1
        eventTest.preRemove == 0
        eventTest.postRemove == 0
        eventTest.preUpdate == 0
        eventTest.preUpdate == 0
        eventTest.postLoad == 0

        // Update listeners are run on the new entity post-update. Essentially, Hibernate detaches the original
        // persisted entity, and then the update re-loads a whole new entity before performing the update operation.
        eventTestDetachedUpdate.prePersist == 0
        eventTestDetachedUpdate.postPersist == 0
        eventTestDetachedUpdate.preRemove == 0
        eventTestDetachedUpdate.preUpdate == 1
        eventTestDetachedUpdate.postUpdate == 1
        eventTestDetachedUpdate.postLoad == 1

        eventTestListeners.prePersist == 1
        eventTestListeners.postPersist == 1
        eventTestListeners.preUpdate == 1
        eventTestListeners.postUpdate == 1
        eventTestListeners.preRemove == 0
        eventTestListeners.postRemove == 0
    }

    void "test remove event"() {
        given:
        def eventTest = new EventTest(1)
        when:
        repo.save(eventTest)
        repo.delete(eventTest)

        then:
        // Remove listeners are run on the original detached event.
        eventTest.prePersist == 1
        eventTest.postPersist == 1
        eventTest.preRemove == 1
        eventTest.postRemove == 1
        eventTest.preUpdate == 0
        eventTest.preUpdate == 0
        eventTest.postLoad == 0

        eventTestListeners.prePersist == 1
        eventTestListeners.postPersist == 1
        eventTestListeners.preUpdate == 0
        eventTestListeners.postUpdate == 0
        eventTestListeners.preRemove == 1
        eventTestListeners.postRemove == 1
    }
}

@Factory
@Requires(property = "spec.name", value = "EventsSpec")
class EventTestListeners {
    private int prePersist = 0
    private int postPersist = 0
    private int preUpdate = 0
    private int postUpdate = 0
    private int preRemove = 0
    private int postRemove = 0

    int getPrePersist() {
        return prePersist
    }

    int getPostPersist() {
        return postPersist
    }

    int getPreUpdate() {
        return preUpdate
    }

    int getPostUpdate() {
        return postUpdate
    }

    int getPreRemove() {
        return preRemove
    }

    int getPostRemove() {
        return postRemove
    }

    void reset() {
        prePersist = 0
        postPersist = 0
        preUpdate = 0
        postUpdate = 0
        preRemove = 0
        postRemove = 0
    }

    @Singleton
    PrePersistEventListener<EventTest> prePersistListener() {
        return new PrePersistEventListener<EventTest>() {
            @Override
            boolean prePersist(EventTest entity) {
                prePersist++
                true
            }
        }
    }

    @Singleton
    PostPersistEventListener<EventTest> postPersistListener() {
        return new PostPersistEventListener<EventTest>() {
            @Override
            void postPersist(EventTest entity) {
                postPersist++
            }
        }
    }

    @Singleton
    PreUpdateEventListener<EventTest> preUpdateEventListener() {
        return new PreUpdateEventListener<EventTest>() {
            @Override
            boolean preUpdate(EventTest entity) {
                preUpdate++
                true
            }
        }
    }

    @Singleton
    PostUpdateEventListener<EventTest> postUpdateEventListener() {
        return new PostUpdateEventListener<EventTest>() {
            @Override
            void postUpdate(EventTest entity) {
                postUpdate++
            }
        }
    }

    @Singleton
    PreRemoveEventListener<EventTest> preRemoveEventListener() {
        return new PreRemoveEventListener<EventTest>() {
            @Override
            boolean preRemove(EventTest entity) {
                preRemove++
                true
            }
        }
    }

    @Singleton
    PostRemoveEventListener<EventTest> postRemoveEventListener() {
        return new PostRemoveEventListener<EventTest>() {
            @Override
            void postRemove(EventTest entity) {
                postRemove++
            }
        }
    }
}
