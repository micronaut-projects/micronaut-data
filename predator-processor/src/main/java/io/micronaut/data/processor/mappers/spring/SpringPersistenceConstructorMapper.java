package io.micronaut.data.processor.mappers.spring;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Creator;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Maps Spring Data's PersistenceConstructor to {@link Creator}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class SpringPersistenceConstructorMapper implements NamedAnnotationMapper {
    @Nonnull
    @Override
    public String getName() {
        return "org.springframework.data.annotation.PersistenceConstructor";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
                AnnotationValue.builder(Creator.class).build()
        );
    }
}
