package io.micronaut.data.processor.mappers.spring;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.annotation.Persisted;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Maps Spring Data's Persisted to {@link Persisted}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class SpringPersistentAnnotationMapper implements NamedAnnotationMapper {
    @NonNull
    @Override
    public String getName() {
        return "org.springframework.data.annotation.Persistent";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(AnnotationValue.builder(Persisted.class).build());
    }
}
