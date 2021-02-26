package io.micronaut.data.runtime.event

import edu.umd.cs.findbugs.annotations.NonNull
import io.micronaut.data.annotation.event.*
import io.micronaut.data.event.EntityEventContext
import io.micronaut.data.event.EntityEventListener
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Unroll

import javax.inject.Inject
import javax.inject.Singleton
import java.lang.annotation.Annotation

@MicronautTest
@Stepwise
class EntityEventRegistrySpec extends Specification {

    @Inject EntityEventRegistry eventRegistry
    @Inject MyPrePersist myPrePersist
    @Inject MyPreUpdate myPreUpdate
    @Inject MyPreRemove myPreRemove
    @Inject MyPostLoad myPostLoad
    @Inject MyPostPersist myPostPersist
    @Inject MyPostRemove myPostRemove
    @Inject MyPostUpdate myPostUpdate
    @Inject TestEventListenerFactory testEventListenerFactory
    @Inject TestEventBean testEventAdapter

    @Unroll
    void "test supports method for event type #eventType"() {
        given:
        def eventTest1 = RuntimePersistentEntity.of(EventTest1)
        def eventLess = RuntimePersistentEntity.of(EventLess)

        expect:
        eventRegistry != null
        eventRegistry.supports(eventTest1, eventType)
        !eventRegistry.supports(eventLess, eventType)

        where:
        eventType << EntityEventRegistry.EVENT_TYPES
    }

    void "test fire pre persist event"() {
        given:
        def entity = new EventTest1()
        def mockEvent = new DefaultEntityEventContext(RuntimePersistentEntity.of(EventTest1), entity)

        when:
        eventRegistry.prePersist(mockEvent)

        then:
        entity.uuid != null
        entity.dateCreated != null
        entity.dateUpdated == entity.dateCreated
        entity.prePersist == 1
        testEventListenerFactory.prePersist == 1
        testEventAdapter.prePersist == 1
        entity.preRemove == 0
        testEventListenerFactory.preRemove == 0
        entity.preUpdate == 0
        testEventListenerFactory.preUpdate == 0
        entity.postRemove == 0
        testEventListenerFactory.postRemove == 0
        entity.postUpdate == 0
        testEventListenerFactory.preUpdate == 0
        entity.postLoad == 0
        entity.postPersist == 0
        myPrePersist.count == 1
        myPreUpdate.count == 0
        myPreRemove.count == 0
        myPostLoad.count == 0
        myPostUpdate.count == 0
        myPostRemove.count == 0
        myPostPersist.count == 0
    }

    void "test fire pre update event"() {
        given:
        def entity = new EventTest1()
        def mockEvent = new DefaultEntityEventContext(RuntimePersistentEntity.of(EventTest1), entity)

        when:
        eventRegistry.preUpdate(mockEvent)

        then:
        entity.prePersist == 0
        testEventListenerFactory.prePersist == 1
        entity.preRemove == 0
        entity.preUpdate == 1
        testEventListenerFactory.preUpdate == 1
        entity.postRemove == 0
        testEventListenerFactory.preRemove == 0
        entity.postUpdate == 0
        entity.postLoad == 0
        entity.postPersist == 0
        myPrePersist.count == 1
        myPreUpdate.count == 1
        myPreRemove.count == 0
        myPostLoad.count == 0
        myPostUpdate.count == 0
        myPostRemove.count == 0
        myPostPersist.count == 0
    }

    void "test fire pre remove event"() {
        given:
        def entity = new EventTest1()
        def mockEvent = new DefaultEntityEventContext(RuntimePersistentEntity.of(EventTest1), entity)

        when:
        eventRegistry.preRemove(mockEvent)

        then:
        entity.prePersist == 0
        testEventListenerFactory.prePersist == 1
        entity.preRemove == 1
        testEventListenerFactory.preRemove == 1
        entity.preUpdate == 0
        testEventListenerFactory.preUpdate == 1
        entity.postRemove == 0
        entity.postUpdate == 0
        entity.postLoad == 0
        entity.postPersist == 0
        myPrePersist.count == 1
        myPreUpdate.count == 1
        myPreRemove.count == 1
        myPostLoad.count == 0
        myPostUpdate.count == 0
        myPostRemove.count == 0
        myPostPersist.count == 0
    }

    void "test fire post load event"() {
        given:
        def entity = new EventTest1()
        def mockEvent = new DefaultEntityEventContext(RuntimePersistentEntity.of(EventTest1), entity)

        when:
        eventRegistry.postLoad(mockEvent)

        then:
        entity.prePersist == 0
        entity.preRemove == 0
        entity.preUpdate == 0
        entity.postRemove == 0
        entity.postUpdate == 0
        entity.postLoad == 1
        entity.postPersist == 0
        myPrePersist.count == 1
        myPreUpdate.count == 1
        myPreRemove.count == 1
        myPostLoad.count == 1
        myPostUpdate.count == 0
        myPostRemove.count == 0
        myPostPersist.count == 0
    }

    void "test fire post update event"() {
        given:
        def entity = new EventTest1()
        def mockEvent = new DefaultEntityEventContext(RuntimePersistentEntity.of(EventTest1), entity)


        when:
        eventRegistry.postUpdate(mockEvent)

        then:
        entity.prePersist == 0
        entity.preRemove == 0
        entity.preUpdate == 0
        entity.postRemove == 0
        entity.postUpdate == 1
        entity.postLoad == 0
        entity.postPersist == 0
        myPrePersist.count == 1
        myPreUpdate.count == 1
        myPreRemove.count == 1
        myPostLoad.count == 1
        myPostUpdate.count == 1
        myPostRemove.count == 0
        myPostPersist.count == 0
    }

    void "test fire post remove event"() {
        given:
        def entity = new EventTest1()
        def mockEvent = new DefaultEntityEventContext(RuntimePersistentEntity.of(EventTest1), entity)

        when:
        eventRegistry.postRemove(mockEvent)

        then:
        entity.prePersist == 0
        entity.preRemove == 0
        entity.preUpdate == 0
        entity.postRemove == 1
        entity.postUpdate == 0
        entity.postLoad == 0
        entity.postPersist == 0
        myPrePersist.count == 1
        myPreUpdate.count == 1
        myPreRemove.count == 1
        myPostLoad.count == 1
        myPostUpdate.count == 1
        myPostRemove.count == 1
        myPostPersist.count == 0
    }

    void "test fire post persist event"() {
        given:
        def entity = new EventTest1()
        def mockEvent = new DefaultEntityEventContext(RuntimePersistentEntity.of(EventTest1), entity)

        when:
        eventRegistry.postPersist(mockEvent)

        then:
        entity.prePersist == 0
        entity.preRemove == 0
        entity.preUpdate == 0
        entity.postRemove == 0
        entity.postUpdate == 0
        entity.postLoad == 0
        entity.postPersist == 1
        myPrePersist.count == 1
        myPreUpdate.count == 1
        myPreRemove.count == 1
        myPostLoad.count == 1
        myPostUpdate.count == 1
        myPostRemove.count == 1
        myPostPersist.count == 1
    }

    @Singleton
    static class MyPrePersist implements EntityEventListener<EventTest1> {
        int count = 0
        @Override
        boolean supports(RuntimePersistentEntity<EventTest1> entity, Class<? extends Annotation> eventType) {
            return eventType == PrePersist
        }

        @Override
        boolean prePersist(@NonNull EntityEventContext<EventTest1> context) {
            count++
            return true
        }
    }

    @Singleton
    static class MyPreRemove implements EntityEventListener<EventTest1> {
        int count = 0
        @Override
        boolean supports(RuntimePersistentEntity<EventTest1> entity, Class<? extends Annotation> eventType) {
            return eventType == PreRemove
        }

        @Override
        boolean preRemove(@NonNull EntityEventContext<EventTest1> context) {
            count++
            return true
        }
    }

    @Singleton
    static class MyPreUpdate implements EntityEventListener<EventTest1> {
        int count = 0
        @Override
        boolean supports(RuntimePersistentEntity<EventTest1> entity, Class<? extends Annotation> eventType) {
            return eventType == PreUpdate
        }

        @Override
        boolean preUpdate(@NonNull EntityEventContext<EventTest1> context) {
            count++
            return true
        }
    }

    @Singleton
    static class MyPostUpdate implements EntityEventListener<EventTest1> {
        int count = 0
        @Override
        boolean supports(RuntimePersistentEntity<EventTest1> entity, Class<? extends Annotation> eventType) {
            eventType == PostUpdate
        }

        @Override
        void postUpdate(@NonNull EntityEventContext<EventTest1> context) {
            count++
        }
    }

    @Singleton
    static class MyPostLoad implements EntityEventListener<EventTest1> {
        int count = 0
        @Override
        boolean supports(RuntimePersistentEntity<EventTest1> entity, Class<? extends Annotation> eventType) {
            eventType == PostLoad
        }

        @Override
        void postLoad(@NonNull EntityEventContext<EventTest1> context) {
            count++
        }
    }

    @Singleton
    static class MyPostRemove implements EntityEventListener<EventTest1> {
        int count = 0
        @Override
        boolean supports(RuntimePersistentEntity<EventTest1> entity, Class<? extends Annotation> eventType) {
            eventType == PostRemove
        }

        @Override
        void postRemove(@NonNull EntityEventContext<EventTest1> context) {
            count++
        }
    }

    @Singleton
    static class MyPostPersist implements EntityEventListener<EventTest1> {
        int count = 0
        @Override
        boolean supports(RuntimePersistentEntity<EventTest1> entity, Class<? extends Annotation> eventType) {
            eventType == PostPersist
        }

        @Override
        void postPersist(@NonNull EntityEventContext<EventTest1> context) {
            count++
        }
    }


}



