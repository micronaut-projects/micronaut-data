package io.micronaut.data.runtime.event.listeners

import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Version
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.runtime.event.DefaultEntityEventContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.time.Instant

@MicronautTest
class VersionGeneratingEntityEventListenerSpec extends Specification {

    @Inject
    VersionGeneratingEntityEventListener entityEventListener

    void "test instant version set"() {
        when:
            def entity = new TheEntity()
            def mockEvent = new DefaultEntityEventContext(PersistentEntity.of(TheEntity), entity)
            entityEventListener.prePersist(mockEvent)
        then:
            entity.ver
        when:
            entityEventListener.preUpdate(mockEvent)
        then:
            entity.ver
    }
}

@MappedEntity
class TheEntity {
    @Id
    @AutoPopulated
    UUID uuid

    @Version
    Instant ver
}
