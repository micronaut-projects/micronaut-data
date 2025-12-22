package io.micronaut.data.runtime.event.listeners

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import org.jspecify.annotations.NonNull
import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.DateCreated
import io.micronaut.data.annotation.DateUpdated
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.event.PrePersist
import io.micronaut.data.event.EntityEventContext
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.runtime.RuntimePersistentProperty
import io.micronaut.data.runtime.convert.DataConversionService
import io.micronaut.data.runtime.date.DateTimeProvider
import io.micronaut.data.runtime.event.DefaultEntityEventContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Instant
import spock.lang.Specification

@MicronautTest
@Property(name = "spec.name", value = "AutoPopulatedEntityEventListenerSpec")
class AutoPopulatedEntityEventListenerSpec extends Specification {

    @Inject
    TestAutoPopulatedEntityEventListener entityEventListener

    void "test values are generated when previous values are null"() {
        given:
            def entity = new TestEntity(id: UUID.randomUUID(), legacyCreateTime: null, createTime: null, updateTime: null)
            def eventContext = new DefaultEntityEventContext(PersistentEntity.of(TestEntity), entity)
            entityEventListener.supports(eventContext.getPersistentEntity(), PrePersist)
        when:
            entityEventListener.prePersist(eventContext)
        then:
            entity.legacyCreateTime != null
            entity.createTime != null
            entity.updateTime != null
    }

    void "test DateCreated values are generated when previous values are null and DateUpdated is always generated"() {
        given:
            def legacyCreateTime = Instant.now().minusSeconds(200)
            def originalUpdateTime = Instant.now().minusSeconds(100)
            def entity = new TestEntity(id: UUID.randomUUID(), legacyCreateTime: legacyCreateTime, createTime: null, updateTime: originalUpdateTime)
            def eventContext = new DefaultEntityEventContext(PersistentEntity.of(TestEntity), entity)
            entityEventListener.supports(eventContext.getPersistentEntity(), PrePersist)
        when:
            entityEventListener.prePersist(eventContext)
        then:
            entity.legacyCreateTime == legacyCreateTime
            entity.createTime != null
            entity.updateTime != null && entity.updateTime != originalUpdateTime
    }

    @MappedEntity
    static class TestEntity {
        @Id
        @AutoPopulated
        UUID id;

        @DateCreated
        Instant legacyCreateTime;

        @DateCreated
        Instant createTime;

        @DateUpdated
        Instant updateTime;
    }

    /**
     * An event listener which works like AutoTimestampEntityEventListener but keeps non-null values
     * for fields with `@DateCreated`. Like what you might want if you have to insert legacy values
     * into the database.
     */
    @Singleton
    @Replaces(AutoTimestampEntityEventListener.class)
    @Requires(property = "spec.name", value = "AutoPopulatedEntityEventListenerSpec")
    static class TestAutoPopulatedEntityEventListener extends AutoTimestampEntityEventListener {

        @Inject
        TestAutoPopulatedEntityEventListener(
            DateTimeProvider<?> dateTimeProvider,
            DataConversionService conversionService) {
            super(dateTimeProvider, conversionService);
        }

        @Override
        @NonNull
        protected RuntimePersistentProperty<Object>[] getApplicableProperties(EntityEventContext<Object> context) {
            @SuppressWarnings("unchecked")
            final RuntimePersistentProperty<Object>[] applicableProperties =
                Arrays.stream(super.getApplicableProperties(context))
                    .filter(
                        property ->
                            // properties are "applicable" if the current value is null
                            property.getProperty().get(context.getEntity()) == null
                                // or if the property is updatable (like for @DateUpdated)
                                || property.getAnnotationMetadata().booleanValue(AutoPopulated.class, AutoPopulated.UPDATABLE)
                                .orElse(true))
                    .toArray(RuntimePersistentProperty[]::new);

            return applicableProperties;
        }
    }
}
