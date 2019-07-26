package io.micronaut.data.processor.mappers;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.*;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.List;

/**
 * Configurations bean introspection correctly for each entity.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MappedEntityMapper implements TypedAnnotationMapper<MappedEntity> {
    @Override
    public Class<MappedEntity> annotationType() {
        return MappedEntity.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<MappedEntity> annotation, VisitorContext visitorContext) {
        AnnotationValue<Introspected> introspectionConfig = buildIntrospectionConfiguration();
        return Collections.singletonList(introspectionConfig);
    }

    /**
     * The bean introspection configuration for a mapped entity.
     *
     * @return The entity
     */
    public static AnnotationValue<Introspected> buildIntrospectionConfiguration() {
        final AnnotationValueBuilder<Introspected> builder = AnnotationValue.builder(Introspected.class)
                // don't bother with transients properties
                .member("excludedAnnotations", Transient.class)
                // following are indexed for fast lookups
                .member("indexed",
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", Id.class).build(),
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", Version.class).build(),
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", DateCreated.class).build(),
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", DateUpdated.class).build(),
                        AnnotationValue.builder(Introspected.IndexedAnnotation.class)
                                .member("annotation", MappedProperty.class)
                                .member("member", "value").build()
                );
        return builder.build();
    }
}
