package io.micronaut.data.tck.tests

import io.micronaut.context.ApplicationContext
import io.micronaut.data.tck.entities.DomainEvents
import io.micronaut.data.tck.repositories.DomainEventsReactiveRepository
import io.micronaut.data.tck.repositories.DomainEventsRepository
import io.micronaut.transaction.SynchronousTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import java.sql.Connection

@Stepwise
abstract class AbstractEventsSpec extends Specification {
    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared DomainEvents entityUnderTest

    abstract DomainEventsRepository eventsRepository()

    abstract DomainEventsReactiveRepository eventsReactiveRepository()

    abstract Map<String, String> getProperties()

    void "test pre and post persist event triggered"() {
        given:
        entityUnderTest = new DomainEvents(name:'test')
        eventsRepository().save(entityUnderTest)

        expect:
        entityUnderTest.prePersist == 1
        entityUnderTest.postPersist == 1
        entityUnderTest.preUpdate == 0
        entityUnderTest.postUpdate == 0
        entityUnderTest.preRemove == 0
        entityUnderTest.postRemove == 0
        entityUnderTest.postLoad == 0
    }

    void 'test post load event triggered'() {
        given:
        def loaded = eventsRepository().findById(entityUnderTest.uuid).orElse(null)

        expect:
        loaded.prePersist == 0
        loaded.postPersist == 0
        loaded.preUpdate == 0
        loaded.postUpdate == 0
        loaded.preRemove == 0
        loaded.postRemove == 0
        loaded.postLoad == 1
    }

    void 'tests pre and post update events triggered'() {
        when:
        entityUnderTest.name = 'changed'
        eventsRepository().update(entityUnderTest)

        then:
        entityUnderTest.prePersist == 1
        entityUnderTest.postPersist == 1
        entityUnderTest.preUpdate == 1
        entityUnderTest.postUpdate == 1
        entityUnderTest.preRemove == 0
        entityUnderTest.postRemove == 0
        entityUnderTest.postLoad == 0
    }

    void 'tests pre and post remove events triggered'() {
        when:
        entityUnderTest.name = 'changed'
        eventsRepository().delete(entityUnderTest)

        then:
        entityUnderTest.prePersist == 1
        entityUnderTest.postPersist == 1
        entityUnderTest.preUpdate == 1
        entityUnderTest.postUpdate == 1
        entityUnderTest.preRemove == 1
        entityUnderTest.postRemove == 1
        entityUnderTest.postLoad == 0
    }


    void "test pre and post persist event triggered - reactive"() {
        given:
        entityUnderTest = new DomainEvents(name:'test')
        eventsReactiveRepository().save(entityUnderTest).blockingGet()

        expect:
        entityUnderTest.prePersist == 1
        entityUnderTest.postPersist == 1
        entityUnderTest.preUpdate == 0
        entityUnderTest.postUpdate == 0
        entityUnderTest.preRemove == 0
        entityUnderTest.postRemove == 0
        entityUnderTest.postLoad == 0
    }

    void 'test post load event triggered - reactive'() {
        given:
        def loaded = eventsReactiveRepository().findById(entityUnderTest.uuid).blockingGet()

        expect:
        loaded.prePersist == 0
        loaded.postPersist == 0
        loaded.preUpdate == 0
        loaded.postUpdate == 0
        loaded.preRemove == 0
        loaded.postRemove == 0
        loaded.postLoad == 1
    }

    void 'tests pre and post update events triggered - reactive'() {
        when:
        entityUnderTest.name = 'changed'
        eventsReactiveRepository().update(entityUnderTest).blockingGet()

        then:
        entityUnderTest.prePersist == 1
        entityUnderTest.postPersist == 1
        entityUnderTest.preUpdate == 1
        entityUnderTest.postUpdate == 1
        entityUnderTest.preRemove == 0
        entityUnderTest.postRemove == 0
        entityUnderTest.postLoad == 0
    }

    void 'tests pre and post remove events triggered - reactive'() {
        when:
        entityUnderTest.name = 'changed'
        eventsReactiveRepository().delete(entityUnderTest).blockingGet()

        then:
        entityUnderTest.prePersist == 1
        entityUnderTest.postPersist == 1
        entityUnderTest.preUpdate == 1
        entityUnderTest.postUpdate == 1
        entityUnderTest.preRemove == 1
        entityUnderTest.postRemove == 1
        entityUnderTest.postLoad == 0
    }
}
