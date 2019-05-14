package io.micronaut.data.processor.mappers.spring;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.annotation.Transient;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Maps Spring Data's Transient to {@link Transient}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class SpringTransientMapper implements NamedAnnotationMapper {
    @NonNull
    @Override
    public String getName() {
        return "org.springframework.data.annotation.Transient";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(AnnotationValue.builder(Transient.class).build());
    }
}
